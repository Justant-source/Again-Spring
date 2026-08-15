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

## 프롬프트 파일

사람글 최초 게시(`PostComposeService`)는 원문을 그대로 저장한다. 톤 정규화는 파트너 초대 답변 경로에서만 사용한다.

| 용도 | 파일 | 로드 서비스 |
|---|---|---|
| 톤 정규화 | `docs/shared/prompts/community/post_tonalization.md` | `TonalizationService` (`AnswerProcessingService` / 파트너 초대) |

주력 LLM 생성은 AI-user 스택(`llm-ai-user`)이 담당한다.

---

## llm-worker 컨테이너

`llm-worker/` 디렉토리: Spring Boot + Claude CLI 실행 앱  
- 모델: `claude-haiku-4-5-20251001`
- `~/.claude` bind mount (Claude 인증)
- 엔드포인트: `POST /v1/invoke`, `GET /v1/invocations`

**세션 만료 시 갱신**:
```bash
# 호스트에서 재인증
claude
# 워커 재시작
cd /home/justant/Data/Again-Spring/env
docker compose restart againspring-llm   # base 스택 (dev·prod 공유)
```

---

## 동시성

- ThreadPoolExecutor: 코어 100, 큐 500
- 타임아웃: 600초. 타임아웃·원격 취소·워커 종료 시 Claude CLI 부모와 자식 프로세스를 함께 종료한다.
- 종료 순서: stdin/stdout/stderr를 닫고 자식부터 정상 종료를 요청한 뒤, 2초 내 남은 프로세스는 강제 종료한다.
- monitoring: 호출 성공/실패/지연과 timeout·수동 취소·프로세스 종료 지표를 `/v1/metrics`에서 제공한다.

---

**참고**: [CLAUDE.md — LLM 브릿지 핵심](../../CLAUDE.md)
