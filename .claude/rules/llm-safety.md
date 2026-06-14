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

### 방어 2계층 — 시그니처 추가 시 반드시 두 곳 모두 갱신

| 계층 | 위치 | 역할 |
|---|---|---|
| L1 인보커 | `ai-user/llm/.../service/LlmErrorSignature.java` | LLM 워커 내부에서 오류 문자열 감지 |
| L2 오케스트레이터 | `ai-user/orchestrator/.../safety/ContentSafetyGuard.java` | 봇 텍스트를 BE 게시 전 최종 검사 |

### 현재 시그니처 카테고리 (코드 참조: `LlmErrorSignature.java`)

- **제공자 오류**: `credit balance`, `rate_limit`, `overloaded`, `authentication_error`, `api_error`
- **자기 정체 노출**: `i'm kiro`, `i'm claude`, `저는 claude`, `나는 claude`
- **역할극 거절**: `cannot roleplay`, `can't help with this`, `I can't help with this request`, `역할극`, `프롬프트 인젝션`
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

## 4. LLM 브릿지 (요약 — 상세: `docs/backend/llm-bridge.md`)

- 호출 경로: `backend` → HTTP POST → `againspring-llm:8090/v1/invoke`
- 모델: `claude-haiku-4-5-20251001` (기본) / `claude-sonnet-4-6` (report)
- 인증: 호스트 `~/.claude` 마운트
- BE는 `RemoteLlmProvider`만 사용 — 직접 Claude CLI/API 호출 금지
- 세션 만료 시: 호스트 `claude` 재로그인 → `cd env && docker compose restart againspring-llm`
