@.claude/rules/llm-safety.md
@.claude/rules/multi-agent.md

# CLAUDE.md — 다시봄 프로젝트 개발 가이드

> ⚠️ **이 파일은 180줄 미만 유지.** 역할 = 라우터 + 절대 규칙. 상세는 `docs/` 디렉토리에 위임.

**프로젝트**: 다시봄 · Again Spring — 갈등 커뮤니티 플랫폼.
갈등을 게시하면 AI 배심원(심리상담사 페르소나)과 커뮤니티가 양쪽 입장을 분석하고 공감 비율을 제공.
**스택**: FE Next.js 14 · BE Spring Boot 3.3 + MariaDB 11 · LLM = Claude CLI 브릿지 (remote only)
**도메인**: `againspring.net`(prod) · **상태**: 미공개(prelaunch) — 실서버 검증은 prod만. `dev.againspring.net` 스택은 휴면.

**도메인 용어**: 사연=갈등 게시글 · 배심원=AI 심리상담사 페르소나 9인 · 공감 비율=A:B %(판결 아님) · 진영=작성자(A)/상대방(B) · 광장=공개 피드

---

## 📖 컨텍스트 읽기 규칙 — 토큰 낭비 금지

1. **아래 라우팅 표의 진입 문서만 읽는다.** `docs/` 디렉토리 전체 스캔 금지.
2. **문서 간 충돌 시** `docs/_index.md`의 `authority`를 따른다. **코드(runtime) > 모든 문서.**
3. **부재(삭제) 확인**: 각 `docs/<module>/structure.md`의 "부재하는 것" 섹션 — 온보딩 페이지·`sessionStore`·`keywordGuard.ts` 등은 광장형 피벗 때 삭제됨. import/참조 금지.
4. 문서에 없는 사실은 추측하지 말고 코드를 직접 grep으로 확인.

---

## 🗺️ 작업 라우팅 (범위 → 코드 → 진입 문서)

| 작업 범위 | 코드 위치 | 진입 문서 (이것만 읽기) |
|---|---|---|
| AI agent 작업 루프 | — | `docs/agent-development.md` |
| 시스템 전체 그림 파악 | — | `docs/system.md` |
| 문서 권위/충돌 해결 | — | `docs/_index.md` |
| FE 기능/UI | `frontend/` | `docs/frontend/README.md` |
| FE 디자인 (톤·색·타이포·시그니처) | — | `docs/frontend/design/system.md` |
| FE UX 원칙 / PR 체크리스트 | — | `docs/frontend/ux/principles.md` · `ux/hax-checklist.md` |
| FE 테스트/e2e | `frontend/tests/` | `docs/frontend/testing.md` |
| BE 기능/API | `backend/` | `docs/backend/README.md` |
| LLM 브릿지 (메인 시스템) | `backend/.../llm/` | `docs/backend/llm-bridge.md` |
| AI 유저 (생성·오케스트레이션·학습) | `ai-user/` | `docs/ai-user/README.md` |
| API 명세 + DB 스키마 | — | `docs/shared/api/` |
| 정책 (금지어·인증·약관·권한) | — | `docs/shared/policies/` |
| LLM 프롬프트 (런타임 자산) | `docs/shared/prompts/` | 同 위치 |
| 환경/인프라/배포 | `env/` | `docs/env/deployment.md` · `docs/env/architecture.md` |
| 환경 변수 사전 | — | `docs/env/environment-variables.md` |
| **마케팅 (ASM)** | AS `100.81.189.92` → `ssh justant@100.115.252.61`<br>`~/Data/Again-Spring-Marketing` | ASM `CLAUDE.md` · `docs/shared/marketing/x-thread-strategy.md` |

**ASM**: Python 3.12 + FastAPI, 포트 8200. AS 호스트에서 `ssh justant@100.115.252.61` **암호 없이** 접속. **활성 = X / `x_thread` + Instagram / `instagram_feed`** (게시 후 24h 자동). 네이버·YouTube·Threads 보류. Again-Spring 쪽은 thin client만.

---

## 🚨 절대 규칙

1. **FE는 LLM 직접 호출 금지** — 모든 LLM 요청은 BE 경유 (REST API)
2. **BE는 RemoteLlmProvider만 사용** — HTTP POST → `againspring-llm:8090/v1/invoke` (base 스택 공유)
3. **LLM 프롬프트/출력 수정 시** `docs/shared/policies/forbidden-words.md` 확인. AI 출력에 판결/처방/승패 표현 금지 → "공감/관점/작성자/상대방" (상세: `.claude/rules/llm-safety.md`)
4. **🚨 prod 배포** — 명시적 "prod에 배포해줘" 지시 없으면 금지. **미공개 기간 필수 순서**:
   ① local unit/build → ② DB 백업 → ③ prod(:8091) 배포 → ④ e2e-realbe (`E2E_BASE_URL=http://localhost:8091`) 전체 통과 → ⑤ main commit & push
   **dev 스택(:8090) 배포·e2e·동기화 금지** (명시 요청 전). compose.dev는 휴면 보관.
5. **`.env.prod` git 커밋 절대 금지**
6. **문서 위치** — 루트는 `README.md`·`CLAUDE.md`·`AGENTS.md`만. 모든 상세 문서는 `docs/` 하위만.
7. **🚨 LLM 토큰/크레딧 소진 = 오류, 콘텐츠 아님** — 오류 문자열을 글·댓글 본문으로 절대 게시 금지. 방어 2계층: `LlmErrorSignature` + `ContentSafetyGuard`. 시그니처 추가 시 **두 곳 모두** 갱신. (상세: `.claude/rules/llm-safety.md`)
8. **🚨 SSOT Doc-Sync 게이트** — commit/push 전 필수:
   ① `git diff --staged --name-only` → `docs/_index.md` 트리거맵 조회
   ② 대응 문서 + README를 코드에 맞춰 **같은 커밋**에서 갱신
   ③ `cd frontend && npm run lint:docs` 통과
   ④ 갱신 대상 없으면 커밋 메시지에 `Doc-Sync: 없음` 명시
   **HALT** — API/포트/ER/상태전이/정책/환경변수 변경인데 대응 문서 못 찾으면 push 중단·보고
9. **브랜치 정책 — main 단일 브랜치** — 모든 작업은 `main`에서 직접 commit·push. feature/topic 브랜치 생성 금지. worktree 에이전트가 만든 임시 브랜치는 작업 완료 즉시 삭제.

---

## 🎨 FE 불변 규칙

> 권위본: `docs/frontend/ux/principles.md` · `docs/frontend/design/system.md`

- **진영색**: 작성자=피치 `#C9785A` / 상대방=세이지 `#5F8F76` — 앱 전체 일관
- AI 배심원·요약은 AI임을 명확히 표시, 사용자 글과 시각 구분
- **사용자 입력에 금지어 필터 미적용** (책임은 사용자) — 필터는 AI 출력에만 적용

---

## 🧠 LLM 브릿지 (요약 — 상세: `docs/backend/llm-bridge.md`)

- `backend` → HTTP → `againspring-llm:8090/v1/invoke` (**base 스택**) · 모델 `claude-haiku-4-5-20251001` · 인증 = 호스트 `~/.claude` 마운트
- **보안**: 사용자 입력은 반드시 `PromptSanitizer` 경유 후 `<user_input>` 태그로 삽입
- 세션 만료 시: 호스트 `claude` 재로그인 → `cd env && docker compose restart againspring-llm`

---

## 🔌 자주 쓰는 명령

```bash
# 로컬 개발 (DB → BE → FE)
cd env && docker compose up -d            # MariaDB :3306 + LLM 워커
cd backend && ./gradlew bootRun           # :8080
cd frontend && npm run dev                # :3000

# 검증 — FE
cd frontend && npm run lint:words && npm run lint:docs && npm run build
npm run test                              # vitest
E2E_BASE_URL=http://localhost:8091 npm run test:e2e:realbe   # 실서버 = prod만

# 검증 — BE
cd backend && ./gradlew test

# 헬스
curl localhost:8080/api/health            # 로컬 BE
curl localhost:8091/api/health            # prod (유일한 실서버 검증면)
```

---

## 🧪 테스트 핵심

- **e2e ↔ 기능 동기화 (prod 게이트)**: 기능 추가/수정/삭제 시 `frontend/tests/e2e-realbe/journeys/`의 대응 spec을 추가/갱신/제거. e2e-realbe(`:8091`) 전체 통과 = prod 배포 게이트 (절대 규칙 #4).
- e2e는 실 BE(**prod:8091**) 사용하되 **LLM 절대 호출 금지** — 모든 spec은 `support/no-llm-fixture.ts`를 import (jurorCount>0·분석 엔드포인트 자동 차단).
- 계층별 커버리지 목표·상세 전략: `docs/frontend/testing.md` · `docs/backend/testing.md`

---

## 🚀 배포 (요약 — 절차 권위본: `docs/env/deployment.md`)

| 환경 | 도메인 | compose | nginx | 상태 |
|---|---|---|---|---|
| prod | againspring.net | `docker-compose.prod.yml` + `.env.prod` | :8091 | **활성** |
| dev | dev.againspring.net | `docker-compose.dev.yml` + `.env.dev` | :8090 | **휴면** (명시 요청 전 배포 금지) |

```bash
cd env
docker compose up -d --build                                                  # ① base (공유 LLM)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build  # ② prod (절대 규칙 #4)
```

---

## ✅ 수정 시 체크 (요약)

- **FE**: `lint:words` · `lint:docs` · `build` 통과 / `data-testid` 변경 → `tests/e2e-realbe/support/selectors.ts` 동기화 / journeys e2e 동기화 / pre-commit vitest (긴급 우회 `SKIP_TESTS=1`)
- **BE**: `docs/shared/api/rest-spec.md` 일치 / LLM 호출은 `PromptSanitizer` 경유 / 커버리지 80%+ / journeys e2e 동기화
- **prod 배포 전**: 절대 규칙 #4 (백업 → prod:8091 → e2e `localhost:8091` → push)

---

**마지막 업데이트**: 2026-08-01 | **담당**: Claude Code (Agent)
