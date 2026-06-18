# LLM 안전 규칙 — 다시봄

> 이 파일은 CLAUDE.md 절대 규칙 #3·#7에서 추출한 상세 규칙이다.
> CLAUDE.md는 이 파일을 `@import`한다. **코드 가드와 반드시 동기화.**

---

## 1. AI 출력 금지 표현 (절대 규칙 #3)

**권위본**: `docs/shared/policies/forbidden-words.md`

AI 배심원·요약 출력에서 아래 표현은 **절대 금지**. 위반 시 미게시·ERROR 처리.

| 금지 | 대체 |
|---|---|
| 판결·판사·판정 | 공감·관점 |
| 처방·진단 | 상황 분석 |
| 승자·패자·가해자·피해자 | 작성자(A)·상대방(B) |
| 유죄·무죄 | — |
| 가스라이팅·나르시시스트·소시오패스 | — |
| 과실비율 | 공감 비율 |

**사용자 입력에는 필터 미적용** — 필터는 AI 출력에만 적용 (CLAUDE.md FE 불변 규칙).

---

## 2. LLM 오류·크레딧 소진 = 콘텐츠 아님 (절대 규칙 #7)

**배경**: 2026-06-07 prod 인시던트 — "Credit balance is too low" 오류 문자열이 댓글 본문으로 게시됨.
추가: 2026-06-12 clcocloud Haiku 거절 노드 — "I can't help with this request"·역할극 거절문.
추가: 2026-06-18 NATEPAN 62% 오염 — 언어 가드(한글 비율<10%) 도입 + ML corpus 정화(171행, 11개 커뮤니티 삭제).

### 방어 3계층 — 시그니처 추가 시 반드시 두 곳 모두 갱신 (언어 가드는 3곳)

| 계층 | 위치 | 역할 |
|---|---|---|
| L1 인보커 | `ai-user/llm/.../service/LlmErrorSignature.java` | LLM 워커 내부에서 오류 문자열 감지 + 언어 가드 |
| L2 오케스트레이터 | `ai-user/orchestrator/.../safety/ContentSafetyGuard.java` | 봇 텍스트를 BE 게시 전 최종 검사 + 언어 가드 |
| L3 ML corpus | `Again-Spring-AI-User/app/api/routes_corpus.py` | ingest 시 ai 행 한글 없으면 거부 |

### 현재 시그니처 카테고리 (코드 참조: `LlmErrorSignature.java`)

- **언어 가드 (2026-06-18)**: 한글 char 비율 < 10% → 무효. 영어 거절·오류 근본 탐지 → L1에서 감지 시 Sonnet 폴백 발동
- **제공자 오류**: `credit balance`, `rate_limit`, `overloaded`, `authentication_error`, `api_error`
- **자기 정체 노출**: `i'm kiro`, `i'm claude`, `저는 claude`, `나는 claude`
- **역할극 거절**: `cannot roleplay`, `can't help with this`, `I can't help with this request`, `역할극`, `프롬프트 인젝션`, `i can't fulfill`, `i can't write this`
- **일반 거절**: 거절문 패턴 (§18 `ai-user/docs/llm.md` 참조)

### 오염 루프 방지

거절문이 `loadRecentBodies`를 통해 다음 프롬프트에 재주입되면 루프가 생긴다.
`ActionExecutor.loadRecentBodies`는 **ContentSafetyGuard 통과분만** history에 저장한다.

### 처리 원칙

```
오류/거절 감지 → ERROR 로그 → 예외 처리 → 미게시 (무음 실패 X)
```

---

## 3. PromptSanitizer — 사용자 입력 보안

**위치**: `backend/.../llm/PromptSanitizer.java`

모든 사용자 입력은 `PromptSanitizer`를 경유한 후 `<user_input>` 태그로 프롬프트에 삽입.

- 제어문자 제거 (tab 제외)
- `<`·`>` → 전각 문자 치환 (프롬프트 인젝션 방어)
- 최대 5,000자 자름
- FE에서 BE 경유 없이 LLM 직접 호출 금지 (CLAUDE.md 절대 규칙 #1)

---

## 4. Claude API 우선순위 + 재시도 규칙 (Claude Code 행동 규칙)

**우선순위**: `1. clcocloud claude API` → `2. Claude Code CLI` (폴백)

**재시도 한도: 최대 3회** — Claude Code가 수동으로 trigger, tick, API 호출 등을 반복할 때의 상한.
- tick/trigger 수동 실행: 3회 이하
- API 호출 실패 후 재시도: 3회 이하
- 3회 소진 후 실패 시 → 오류 로그 + 중단 (10회·무한 재시도 금지)

**코드 설정값** (ai-user llm):
- `LLM_API_REFUSAL_RETRIES=0` (dev·prod .env) → 거절 노드 재시도 1회
- 기본값 (`application.yml`): `refusal-retries: 2` (= 총 3회) → 위 규칙과 일치
- `refusal-fallback-model` 미설정 = Sonnet 폴백 비활성

**Sonnet 4.6 인시던트 대응**: Sonnet 4.6 다운 시 Haiku 생성은 영향 없음. run_ab_test.py 평가나 report 엔드포인트만 영향 받음 → 해당 작업 연기.

---

## 5. LLM 브릿지 (요약 — 상세: `docs/backend/llm-bridge.md`)

- 호출 경로: `backend` → HTTP POST → `againspring-llm:8090/v1/invoke`
- 모델: `claude-haiku-4-5-20251001` (기본) / `claude-sonnet-4-6` (report)
- 인증: 호스트 `~/.claude` 마운트
- BE는 `RemoteLlmProvider`만 사용 — 직접 Claude CLI/API 호출 금지
- 세션 만료 시: 호스트 `claude` 재로그인 → `cd env && docker compose restart againspring-llm`
