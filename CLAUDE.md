# CLAUDE.md — 다시봄 프로젝트 개발 가이드

> ⚠️ **이 파일은 250줄 미만 유지.** 역할 = 라우터 + 절대 규칙. 상세는 4개 docs 디렉토리에 위임.

**프로젝트**: 다시봄 · Again Spring — 갈등 커뮤니티 플랫폼.
갈등을 게시하면 AI 배심원(심리상담사 페르소나)과 커뮤니티가 양쪽 입장을 분석하고 공감 비율을 제공.
**스택**: FE Next.js 14 · BE Spring Boot 3.3 + MariaDB 11 · LLM = Claude CLI 브릿지 (remote only)
**도메인**: `dev.againspring.net`(dev) / `againspring.net`(prod) · **상태**: 광장형 피벗 완료 (2026-06-02, defc742)

**도메인 용어**: 사연=갈등 게시글 · 배심원=AI 심리상담사 페르소나 9인 · 공감 비율=A:B %(판결 아님) · 진영=작성자(A)/상대방(B) · 광장=공개 피드

---

## 📖 컨텍스트 읽기 규칙 — 토큰 낭비 금지

1. **아래 라우팅 표의 진입 문서만 읽는다.** 작업과 무관한 docs 디렉토리 전체 스캔 금지.
2. **문서 간 충돌 시** `shared/docs/manifest.yaml`의 `authority`를 따른다. **코드(runtime) > 모든 문서.**
3. **부재(삭제) 확인**: 각 `structure.md`의 "부재하는 것" 섹션 — 온보딩 페이지·`sessionStore`·`keywordGuard.ts` 등은 광장형 피벗 때 삭제됨. import/참조 금지.
4. 문서에 없는 사실은 추측하지 말고 코드를 직접 grep으로 확인.

---

## 🗺️ 작업 라우팅 (범위 → 코드 → 진입 문서)

| 작업 범위 | 코드 위치 | 진입 문서 (이것만 읽기) |
|---|---|---|
| FE 기능/UI | `frontend/` | `frontend/docs/README.md` |
| FE 디자인 (톤·색·타이포·시그니처) | — | `frontend/docs/design/system.md` |
| FE UX 원칙 / PR 체크리스트 | — | `frontend/docs/ux/principles.md` · `ux/hax-checklist.md` |
| FE 테스트/e2e | `frontend/tests/` | `frontend/docs/testing.md` |
| BE 기능/API | `backend/` | `backend/docs/README.md` |
| LLM 브릿지 (메인 시스템) | `backend/.../llm/` | `backend/docs/llm-bridge.md` |
| AI 유저 (생성·오케스트레이션·학습) | `ai-user/` | `ai-user/docs/README.md` |
| API 명세 + DB 스키마 | — | `shared/docs/api/` |
| 정책 (금지어·인증·약관·권한) | — | `shared/docs/policies/` |
| LLM 프롬프트 (런타임 자산) | `shared/docs/prompts/` | 同 위치 |
| 환경/인프라/배포 | `env/` | `env/docs/deployment.md` · `architecture.md` |
| 환경 변수 사전 | — | `env/docs/environment-variables.md` |
| 문서 권위/충돌 해결 | — | `shared/docs/manifest.yaml` |
| **마케팅 (ASM — 별도 서버)** | SSH `justant@100.115.252.61`<br>`~/Data/Again-Spring-Marketing` | ASM 저장소의 `CLAUDE.md` |

**ASM**: Python 3.12 + FastAPI, 포트 8200, 수정·commit·push 허용(명시적 지시 기준). Again-Spring 쪽은 thin client만.

---

## 🚨 절대 규칙

1. **FE는 LLM 직접 호출 금지** — 모든 LLM 요청은 BE 경유 (REST API)
2. **BE는 RemoteLlmProvider만 사용** — HTTP POST → `againspring-llm:8090/v1/invoke` (base 스택 공유)
3. **LLM 프롬프트/출력 수정 시** `shared/docs/policies/forbidden-words.md` 확인. AI 출력에 판결/처방/승패 표현 금지 → "공감/관점/작성자/상대방"
4. **🚨 prod 배포** — 명시적 "prod에 배포해줘" 지시 없으면 금지. **필수 순서**:
   ① dev 배포 → ② e2e-realbe 전체 통과 (**dev:8090 대상만** — prod 대상 실행 절대 금지) → ③ main commit & push → ④ prod 배포 (main 기준, `.env.prod` 전 값 입력, DB 백업 후)
5. **`.env.prod` git 커밋 절대 금지**
6. **문서 위치** — 루트는 `README.md`·`CLAUDE.md`만. 상세 문서는 4개 docs 디렉토리만.
7. **🚨 LLM 토큰/크레딧 소진 = 오류, 콘텐츠 아님** — "Credit balance is too low"·rate limit·overloaded 등 제공자 오류 문자열을 글·댓글 본문으로 절대 게시 금지. ERROR 로그 → 예외 처리 → 미게시.
   방어 2계층: ai-user 인보커(`LlmErrorSignature`) + orchestrator `ContentSafetyGuard`. 시그니처 추가 시 **두 곳 모두** 갱신. (2026-06-07 prod 인시던트)

---

## 🎨 FE 불변 규칙

> 권위본: `frontend/docs/ux/principles.md` · `frontend/docs/design/system.md`

- **진영색**: 작성자=피치 `#C9785A` / 상대방=세이지 `#5F8F76` — 앱 전체 일관
- AI 배심원·요약은 AI임을 명확히 표시, 사용자 글과 시각 구분
- **사용자 입력에 금지어 필터 미적용** (책임은 사용자) — 필터는 AI 출력에만 적용

---

## 🧠 LLM 브릿지 (요약 — 상세: `backend/docs/llm-bridge.md`)

- `backend` → HTTP → `againspring-llm:8090/v1/invoke` (**base 스택**, dev·prod 공유) · 모델 `claude-haiku-4-5-20251001` · 인증 = 호스트 `~/.claude` 마운트
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
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe   # prod 게이트

# 검증 — BE
cd backend && ./gradlew test

# 헬스
curl localhost:8080/api/health            # 로컬 BE
curl localhost:8090/api/health            # dev
curl localhost:8091/api/health            # prod
```

---

## 🧪 테스트 핵심

- **e2e ↔ 기능 동기화 (prod 게이트)**: 기능 추가/수정/삭제 시 `frontend/tests/e2e-realbe/journeys/`의 대응 spec을 추가/갱신/제거. e2e-realbe 전체 통과 = prod 배포 필수 게이트 (절대 규칙 #4).
- e2e는 실 BE(8090) 사용하되 **LLM 절대 호출 금지** — 모든 spec은 `support/no-llm-fixture.ts`를 import (jurorCount>0·분석 엔드포인트 자동 차단).
- 계층별 커버리지 목표·상세 전략: `frontend/docs/testing.md` · `backend/docs/testing.md`

---

## 🚀 배포 (요약 — 절차 권위본: `env/docs/deployment.md`)

| 환경 | 도메인 | compose | nginx |
|---|---|---|---|
| dev | dev.againspring.net | `docker-compose.dev.yml` + `.env.dev` | :8090 |
| prod | againspring.net | `docker-compose.prod.yml` + `.env.prod` | :8091 |

```bash
cd env
docker compose up -d --build                                                  # ① base (공유 LLM 워커 — 먼저)
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build    # ② dev
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build  # ③ prod (절대 규칙 #4 충족 시)
```

---

## ✅ 수정 시 체크 (요약)

- **FE**: `lint:words` · `lint:docs` · `build` 통과 / `data-testid` 변경 → `tests/e2e-realbe/support/selectors.ts` 동기화 / journeys e2e 동기화 / pre-commit vitest (긴급 우회 `SKIP_TESTS=1`)
- **BE**: `shared/docs/api/rest-spec.md` 일치 / LLM 호출은 `PromptSanitizer` 경유 / 커버리지 80%+ / journeys e2e 동기화
- **prod 배포 전**: 절대 규칙 #4 순서 그대로 (dev 배포 → e2e dev:8090 전체 통과 → main push → 백업 → prod)

---

**마지막 업데이트**: 2026-06-11 | **담당**: Claude Code (Agent)
