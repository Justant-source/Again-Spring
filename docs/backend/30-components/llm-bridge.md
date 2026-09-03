# LLM 브릿지 — 다시봄 백엔드 LLM 연동 구조

## 개요

다시봄 백엔드는 LLM 추론을 직접 수행하지 않습니다.
별도의 `llm-worker` 컨테이너(Claude CLI 기반)에 HTTP로 요청합니다.

**흐름**: `BE (RemoteLlmProvider)` → HTTP POST → `againspring-llm:8090/v1/invoke` (base·prod 공유; server-dev는 `LLM_ENABLED=false`로 호출 차단)

---

## 패키지 구조

```
backend/src/main/java/com/againspring/
└── llm/
    ├── PromptSanitizer.java       # 사용자 입력 검증 + <user_input> 태그 삽입
    ├── LlmImage.java              # 선택 vision 첨부 (mime + base64)
    ├── config/                    # LLM 설정 (LlmProperties)
    ├── fallback/                  # 로컬 fallback (개발 전용)
    ├── monitoring/                # 호출 지표 수집
    ├── prompt/                    # 프롬프트 로더
    └── remote/                    # RemoteLlmProvider
        └── dto/                   # InvocationRequest / InvocationResponse
```

---

## RemoteLlmProvider

`llm/remote/RemoteLlmProvider.java`

- HTTP POST `{llm.remote.base-url}/v1/invoke`
- `llm.enabled=false`(env `LLM_ENABLED`)이면 워커 호출 없이 `501 LLM_DISABLED` (server-dev L3)
- 로컬/prod 기본 base-url: `http://againspring-llm:8090` (server-dev는 compose에서 더미 URL + 네트워크 격리)
- 타임아웃: `llm.remote.default-timeout-ms` (운영 기본 600,000ms). HTTP read timeout은 이 값보다 길게 둔다.
- 인증: 없음 (내부 네트워크, 컨테이너 간)
- **선택 이미지**: `LLMProvider.invoke(prompt, model, List<LlmImage>)` — `{mime, base64}` 최대 1장. `RemoteLlmProvider`는 body `images`로 전달한다. 인터페이스 기본 구현은 이미지가 있으면 `UnsupportedOperationException`(호출측 `VISION_FAIL`) — 텍스트 invoke는 사진이 없을 때만.

---

## 설정 키 (application.yml)

| 키 | 설명 |
|---|---|
| `llm.enabled` / `LLM_ENABLED` | false면 RemoteLlmProvider가 501 거절 (server-dev) |
| `llm.remote.base-url` | llm-worker 엔드포인트 |
| `llm.remote.default-timeout-ms` | 기본 타임아웃 (ms) |
| `llm.compose.provider` | (선택) compose provider |
| `llm.compose.model` | (선택) compose 모델 |

---

## PromptSanitizer

`llm/PromptSanitizer.java`

모든 사용자 입력은 LLM 프롬프트 삽입 전 PromptSanitizer를 반드시 경유합니다.

```java
String sanitized = promptSanitizer.sanitize(userInput);
// → "<user_input>" + escaped + "</user_input>"
```

**보안 규칙**:
- 프롬프트 주입 방지: 특수 문자 이스케이프
- 길이 제한: 최대 5,000자
- `PromptSanitizer` 미경유 LLM 호출은 코드 리뷰에서 차단

---

## LLM 오류·거절·누출 시그니처 (SSOT)

`docs/shared/policies/llm-error-signatures.json` 한 파일이 권위본이다. backend(`service/ai/LlmErrorSignatures`),
llm-ai-user, orchestrator, ai-learning이 같은 파일을 `/app/shared/docs/policies/`에서 읽는다(`:ro`).
시그니처 추가 = JSON 수정 + 해당 컨테이너 restart. 코드에 문자열을 넣지 않는다.
backend는 `SyntheticOutputGuard`로 `users.synthetic=1` 작성자의 글·댓글에만 이 판정을 적용해 422 `LLM_ERROR_OUTPUT`으로 거부한다. 실사용자 입력에는 적용하지 않는다.

---

## 프롬프트 파일

사람글 최초 게시(`PostComposeService`)는 원문을 그대로 저장한다. 톤 정규화는 파트너 초대 답변 경로에서만 사용한다.

| 용도 | 파일 | 로드 서비스 |
|---|---|---|
| 톤 정규화 | `docs/shared/prompts/community/post_tonalization.md` | `TonalizationService` (`AnswerProcessingService` / 파트너 초대) |
| X 선댓글 | `docs/shared/prompts/marketing/x-outbound-reply.md` · `x-outbound-donts.md` | `XCommentComposer.composeOutbound` |

주력 LLM 생성은 AI-user 스택(`llm-ai-user`)이 담당한다.

---

## llm-worker 컨테이너

`llm-worker/` 디렉토리: Spring Boot + Claude CLI 실행 앱  
컨테이너: `againspring-llm` (base 스택, dev·prod 공유, port :8090)  
- 모델: `claude-haiku-4-5-20251001`
- 보고서/분석 모델: `claude-sonnet-5` (2026-08-21부터; 이전 `claude-sonnet-4-6`)
- `~/.claude` bind mount (Claude 인증)
- 엔드포인트: `POST /v1/invoke`, `GET /v1/invocations`
- **호출 경로**: `backend` → HTTP POST → `againspring-llm:8090/v1/invoke` (RemoteLlmProvider 경유)
- `/v1/invoke` **optional `images`**: mime+base64. 없으면 텍스트만. 있으면 임시 파일로 CLI에 첨부 후 삭제.

**CLI 도구 오버헤드 감소 (2026-08-21)**: llm-worker는 structured output이 불필요하므로 `--disallowedTools "*"`로 모든 CLI 도구를 차단. 
입력 토큰 오버헤드를 25,267 토큰에서 ~279 토큰으로 감소시킨다(기본값 대비 -99%). 
구조화 경로가 필요할 때는 명시 도구 리스트로 `StructuredOutput` 유지하면 약 18,812 토큰 오버헤드다.

**프롬프트 지시 JSON 모드 (2026-08-21 도입, 2026-08-21 활성화)**: 
`--json-schema` 플래그 대신 schema를 프롬프트 텍스트로 주입하는 `LLM_STRUCTURED_PROMPT_MODE` 플래그(기본 `false`, `.env.ai-user`에서 `true`로 활성화).
이 모드에서는 `--disallowedTools "*"`를 사용하므로 오버헤드가 279 토큰으로 떨어진다(스키마 인라인 350~400 토큰 비용으로 순절감 ~18.1k/호출).

| 모드 | --disallowedTools | 오버헤드 | 검증 |
|---|---|---|---|
| 스키마 플래그 (`--json-schema`) | 명시 리스트 (StructuredOutput만 허용) | ~18.8k | 엄격 (CLI stream-json 검증) |
| 프롬프트 모드 (LLM_STRUCTURED_PROMPT_MODE=true) | `*` (모두 차단) | ~279 | lenient (JsonExtractorUtil로 substring 추출) |
| 비구조화 (기본) | `*` (모두 차단) | ~279 | — |

프롬프트 모드 실측 (/v2/generate/thread-plan, 2026-08-21):
- 스키마 모드 (캐시 warm): 입력 49,311 / 출력 3,888 / 43.3s
- 프롬프트 모드 (캐시 cold): 입력 4,381 / 출력 859 / 13.9s

캐시가 걸린 스키마 모드보다 캐시 없는 프롬프트 모드가 11배 작은 이유는 그동안 캐시에 얹혀 있던 4만 토큰대가 
앱 프롬프트가 아니라 **CLI 도구 정의였다는 증거**다.

### 응답 절단 감지 및 차단 (2026-08-23)

**문제**: 마케팅 영상 발행 잡의 44.5%가 `VARIANT_LLM_ERROR`로 실패. LLM이 낸 JSON 응답이 문장 중간에서 잘렸다.

**오탐 (2026-09-02, AS#1009/#1010)**: `VideoVariantService.looksLikeLlmError`가 원문 전체에 `"overloaded"` 부분문자열을 걸면, 시봄이 카탈로그 id `overloaded`가 들어 있는 **정상 JSON**을 `LLM_ERROR`로 버리고 QUALITY_GATE가 죽는다. 제공자 과부하는 `Overloaded` / `overloaded_error` / `api overloaded`만 매칭하고, JSON 문자열 값 `"overloaded"`는 콘텐츠로 둔다.

**2026-09-03**: backend의 오류 시그니처 판정(`AiCorrectionService`·`XPersonaLearnService`·`VideoVariantService`)은 `com.againspring.service.ai.LlmErrorSignatures` 로더를 거쳐 `docs/shared/policies/llm-error-signatures.json` SSOT를 읽는다(하드코딩 목록 폐기). `VideoVariantService`만 위 `overloaded`-as-JSON-값 예외를 로더 호출 앞단에서 별도로 유지한다.

**근본 원인** (b2eb851a 커밋으로 확정):
1. Claude CLI의 `--output-format stream-json` 출력이 최종 `result` 이벤트 없이 끝날 때(생성이 중간에 끊김)
2. `ClaudeCliInvoker.readStreamingOutput()`이 누적된 부분 스트림을 정상 응답으로 반환
3. 호출자(백엔드)가 잘린 JSON을 파싱하다 실패 → 잡 사망

CLI가 오류 종료해도 부분 텍스트가 있으면 성공으로 반환하던 경로도 동일한 문제였다.

**수정**:
- `StreamResult.truncatedStream` 플래그: 최종 `result` 이벤트 유무로 완결 여부 판정
- 부분 스트림 감지 시 경고 로그 기록 (예: `[llm] 최종 result 이벤트 없음 — 부분 스트림 1234자 (절단)`)
- `[LLMSTATS]` 로그를 `OK` 대신 `TRUNCATED`로 기록
- `CLAUDE_TRUNCATED` 예외 발생 → 호출자의 재시도 경로로 전달
- 오류 종료 시 부분 텍스트 폐기

**토큰 상한 가설의 오류**:
- 초기 진단: 출력 토큰이 상한에 닿아 절단되는 줄 알았으나 **틀렸다**
- 실측: 누적 출력 11,926 토큰까지 나오고, Claude CLI에는 출력 상한 옵션이 없다
- 시도 2회: `maxTokens` 전달로 해결하려 했으나 **대상 저장소를 잘못 짚었다**
  - 백엔드가 호출하는 워커는 `ai-user/llm/` 아님 → `llm-worker/` (컨테이너 `againspring-llm`)
  - `ai-user/llm/`은 orchestrator 내부 경로(다른 구조)
  - 혼동 근거: "LLM 생성"과 "마케팅 LLM"이 다른 워커를 탄다(§3)

**[LLMSTATS] 로깅 (2026-08-21)**: llm-worker의 `ClaudeCliInvoker.logLlmStats()` 메서드가 모든 호출(성공/실패)에 대해 `[LLMSTATS]` 포맷으로 메트릭스를 기록한다:
```
[LLMSTATS] ts=2026-08-21T12:34:56.789Z sys=AS type=INVOKE model=claude-haiku-4-5-20251001 attempt=1 retryReason=null in=2847 out=156 cache_read=0 cache_write=0 cache_hit=0% result=OK duration_ms=3421 corrId=abc-123
```
절단 감지 시 `result=TRUNCATED`로 기록된다 (2026-08-23).

**세션 만료 시 갱신**:
```bash
# 호스트에서 재인증
claude
# 워커 재시작
cd /home/justant/Data/Again-Spring/env
docker compose restart againspring-llm   # base 스택 (dev·prod 공유)
```

### 마케팅 경로 인증 오류 즉시 감지 (2026-08-15)

마케팅 LLM 호출(`VideoVariantService` 등)에서 `LlmErrorSignatures` SSOT(위 §"LLM 오류·거절·누출 시그니처")의 `authentication_error` 시그니처가
**연속 2회** 감지되면 `MarketingLlmAuthGuard`가 재시도 없이 즉시 회로를 열고 긴급 텔레그램을 보낸다
(`🚨 [긴급] Claude 세션 만료 — 수동 재인증 필요`). 인증 오류는 재시도해도 100% 실패하므로
일반 운영 오류 재시도 정책(총 2회, 5분 후 재큐잉)의 **유일한 예외**다.

회로가 열려 있는 동안 `VideoVariantService`는 채널 LLM을 **호출하지 않고** `LLM_AUTH_CIRCUIT_OPEN`만 기록한다. `session limit` / `hit your session`은 쿼터 창이지 OAuth 만료가 아니므로 회로를 열지 않는다. 호출 횟수: `docs/ai-user/70-policy/llm-call-budget.md` §3.

회로가 열려 있는 동안 신규 마케팅 LLM 호출은 즉시 실패 처리되며(재시도 대상에서도 제외), 5분 후
자동으로 반닫히거나 수동으로 리셋할 수 있다. 마케팅 워커(`againspring-llm`)와 AI-user 워커
(`againspring-llm-ai-user`)는 별개 컨테이너지만 **같은 `~/.claude` 계정을 공유**하므로, 세션 만료 시
두 경로가 동시에 멈춘다 — 위 재인증 절차 한 번으로 양쪽 모두 복구된다.

이전에는 5분 주기 WSL 워치독의 canary ping(현재 10분 주기, `docs/env/watchdog.md`)만이 세션 만료를
감지했는데, 요청이 없는 시간대(특히 새벽)에는 최대 감지 지연이 컸다. 이 가드는 **실제 호출이 발생한
순간** 실패를 신호로 쓰므로 지연이 0에 가깝다.

---

## 동시성

- ThreadPoolExecutor: 코어 100, 큐 500
- 타임아웃: 600초. 타임아웃·원격 취소·워커 종료 시 Claude CLI 부모와 자식 프로세스를 함께 종료한다.
- 종료 순서: stdin/stdout/stderr를 닫고 자식부터 정상 종료를 요청한 뒤, 2초 내 남은 프로세스는 강제 종료한다.
- monitoring: 호출 성공/실패/지연과 timeout·수동 취소·프로세스 종료 지표를 `/v1/metrics`에서 제공한다.

---

**참고**: [CLAUDE.md — LLM 브릿지 핵심](../../../CLAUDE.md)
