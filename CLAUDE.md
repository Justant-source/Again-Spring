# CLAUDE.md — 다시봄 프로젝트 개발 가이드

> ⚠️ **이 파일은 250줄 미만으로 유지한다.** 상세 내용은 4개 docs 디렉토리에 위임.

**프로젝트**: 다시봄 · Again Spring
**도메인**: `dev.againspring.net` (dev) / `againspring.net` (prod)
**상태**: 커뮤니티 광장 + AI 배심원 모델 (2026-06-02 피벗 완료, commit defc742)
**기준일**: 2026-06-03

---

## 🎯 프로젝트 한 줄 요약

갈등 커뮤니티 플랫폼. 갈등을 게시하면 AI 배심원(심리상담사 페르소나)과 커뮤니티가 양쪽 입장을 분석하고 공감 비율을 제공하는 웹앱.
FE: Next.js 14 · BE: Spring Boot 3.3 + MariaDB 11 + Claude Code LLM 브릿지 (remote CLI only).

---

## 🏗️ 작업 범위 분리

| 작업 범위 | 디렉토리 | 진입 문서 |
|---|---|---|
| FE 기능/UI | `frontend/` | `frontend/docs/README.md` |
| BE 기능/API | `backend/` | `backend/docs/README.md` |
| LLM 브릿지 (메인 시스템) | `backend/src/main/java/.../llm/` | `backend/docs/llm-bridge.md` |
| AI 유저 LLM 생성 | `ai-user/llm/` | `ai-user/docs/README.md` |
| AI 유저 오케스트레이션 | `ai-user/orchestrator/` | `ai-user/docs/README.md` |
| AI 유저 학습/RAG | `ai-user/learning/` | `ai-user/docs/README.md` |
| 공유 타입/스키마/프롬프트 | `shared/` | `shared/docs/README.md` |
| 환경/인프라/배포 | `env/` | `env/docs/README.md` |
| 마케팅 자동화 (dev 전용) | `marketing/` | `marketing/README.md` |

### 절대 규칙

1. **FE는 Claude Code 직접 호출 금지** — 모든 LLM 요청은 BE 경유 (REST API)
2. **BE는 RemoteLlmProvider만 사용** — HTTP POST → `againspring-llm:8090/v1/invoke` (base 스택 공유)
3. **LLM 프롬프트/출력 수정 시** `shared/docs/policies/forbidden-words.md` 반드시 확인
4. **🚨 prod 배포 절대 규칙** — 명시적 "prod에 배포해줘" 지시 없으면 배포 금지.
   **필수 순서**: ① dev 배포 → ② **e2e 테스트 (dev 대상, 전체 통과)** → ③ commit & push (main) → ④ prod 배포
   e2e-realbe는 **반드시 dev(localhost:8090)에서만** 실행한다. prod 대상 실행 금지.
5. **`.env.prod` git 커밋 절대 금지**
6. **문서 위치** — 루트는 `README.md`, `CLAUDE.md`만. 상세 문서는 4개 docs 디렉토리만 허용.
7. **🚨 LLM 토큰/크레딧 소진 = 오류, 콘텐츠 아님** — CLI든 API든 토큰·크레딧·쿼터가 모자라면(예: "Credit balance is too low", usage/rate limit, overloaded) **그 오류 문자열을 글·댓글 본문으로 절대 게시 금지**. 반드시 ① ERROR 로그 기록 → ② 생성 실패 처리(예외) → ③ prod·dev 어디에도 작성하지 않음.
   방어 계층: ai-user/llm 인보커(`ClaudeCliInvoker`·`ClaudeApiInvoker`)가 제공자 오류를 감지해 예외를 던지고, orchestrator `ContentSafetyGuard`가 오류 시그니처를 최종 차단(`LLM_ERROR_SIGNATURE`). 시그니처 추가 시 두 곳(`LlmErrorSignature`, `ContentSafetyGuard`) 모두 갱신. (2026-06-07 prod 인시던트)

---

## 📋 핵심 문서 위치

| 영역 | 권위본 |
|---|---|
| API 명세 + DB 스키마 | `shared/docs/api/` |
| AI 출력 금지어 정책 | `shared/docs/policies/forbidden-words.md` |
| 서비스 정책 (인증·온보딩·약관) | `shared/docs/policies/` |
| LLM 프롬프트 | `shared/docs/prompts/` |
| FE 디자인 시스템 (톤·색·타이포·시그니처) | `frontend/docs/design/system.md` |
| FE UX 원칙 | `frontend/docs/ux/principles.md` |
| FE 컴포넌트 PR 체크리스트 | `frontend/docs/ux/hax-checklist.md` |
| 배포 절차 | `env/docs/deployment.md` |
| 환경 변수 사전 | `env/docs/environment-variables.md` |

---

## 🎨 FE UX 핵심 규칙

> 권위본: [`frontend/docs/ux/principles.md`](frontend/docs/ux/principles.md) · 체크리스트: [`frontend/docs/ux/hax-checklist.md`](frontend/docs/ux/hax-checklist.md)

- **AI 신뢰성 최우선**: 배심원·요약은 AI임을 명확히 표시, 사용자 글과 시각 구분
- **작성자=피치(peach #C9785A), 상대방=세이지(sage #5F8F76)** — 앱 전체 일관 유지
- **판결/처방/승패 표현 금지** (AI 출력만) — 대체: "공감", "관점", "작성자/상대방"
- **사용자 입력에 금지어 필터 미적용** — 사용자가 쓴 텍스트의 책임은 사용자에게 있음

---

## ⚠️ AI 출력 품질 기준 (AI 배심원·요약에만 적용)

> 권위본: [`shared/docs/policies/forbidden-words.md`](shared/docs/policies/forbidden-words.md)

```bash
cd frontend && npm run lint:words   # 코드베이스 하드코딩 카피 검사
```

---

## 🔌 로컬 개발 명령

```bash
# DB
cd /home/justant/Data/Again-Spring/env && docker compose up -d   # MariaDB localhost:3306

# BE
cd /home/justant/Data/Again-Spring/backend && ./gradlew bootRun  # localhost:8080

# FE
cd /home/justant/Data/Again-Spring/frontend && npm install && npm run dev  # localhost:3000

# 헬스 체크
curl http://localhost:8080/api/health
```

---

## 🧠 LLM 브릿지 핵심

- **구조**: `againspring-backend` → HTTP → `againspring-llm:8090` (`/v1/invoke`)
- **공유**: `againspring-llm`은 **base 스택** 소속 — dev·prod 백엔드가 동일 컨테이너 사용
- **모델**: `claude-haiku-4-5-20251001` · **인증**: 호스트 `~/.claude` 마운트 (API 키 불필요)
- **플래그**: `--strict-mcp-config --no-session-persistence --print`
- **동시성**: ThreadPoolExecutor 100 + LinkedBlockingQueue 500 · 타임아웃 120초

**보안**: 사용자 입력은 반드시 `PromptSanitizer` 경유 후 `<user_input>` 태그로 삽입.

세션 만료 시: 호스트 `claude` 재로그인 → `cd env && docker compose restart againspring-llm`

---

## 🧪 테스트 정책

| 계층 | 목표 커버리지 |
|---|---|
| Unit — Service | 80% |
| Unit — Controller | 70% |
| LLM Bridge | 90% |
| Integration (API) | 80% |
| Security (Sanitizer + Crisis Guard) | 100% |

```bash
cd backend && ./gradlew test
cd frontend && npm run test
```

### e2e ↔ 기능 동기화 규칙 (prod 게이트)

FE/BE 기능을 **추가**하면 대응 e2e를 `frontend/tests/e2e-realbe/journeys/`에 추가, **수정**하면 e2e 갱신, **삭제**하면 e2e 제거한다.
e2e-realbe 전체 통과는 dev→prod 배포의 필수 게이트(절대 규칙 #4).
e2e는 **실 BE(8090)** 응답을 쓰되 **LLM 절대 호출 금지** — 가드레일 픽스처(`support/no-llm-fixture.ts`)가 `jurorCount>0` 및 분석·마케팅 생성 엔드포인트를 자동 차단한다.
권위본: [`frontend/docs/testing.md`](frontend/docs/testing.md)

---

## 🚀 배포 핵심

> 자세한 절차: [`env/docs/deployment.md`](env/docs/deployment.md)

| 환경 | 도메인 | compose 파일 | nginx 포트 |
|---|---|---|---|
| dev | `dev.againspring.net` | `docker-compose.dev.yml` + `.env.dev` | 8090 |
| prod | `againspring.net` | `docker-compose.prod.yml` + `.env.prod` | 8091 |

```bash
# ① base 스택 (공유 LLM 워커 — dev·prod 공통, 먼저 기동)
cd /home/justant/Data/Again-Spring/env
docker compose up -d --build

# ② dev 배포
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
curl http://localhost:8090/api/health

# ③ prod 배포 (명시적 지시 시에만 — main 브랜치 기준)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
curl http://localhost:8091/api/health
```

Cloudflare Tunnel: `dev.againspring.net → :8090` · `againspring.net → :8091`

---

## 💡 개발 체크리스트

### FE 수정 시
- [ ] AI 배심원·요약 출력에 판결/처방 표현 없는지 확인
- [ ] `npm run lint:words` 통과
- [ ] `npm run build` 성공
- [ ] `data-testid` 변경 시 `tests/e2e-realbe/support/selectors.ts` 동기화
- [ ] **기능 추가/수정/삭제 시 `journeys/` e2e 동기화** (테스트 정책 §e2e 규칙 참조)
- [ ] pre-commit hook (vitest) 통과 (긴급 우회: `SKIP_TESTS=1 git commit`)

### BE 수정 시
- [ ] `shared/docs/api/rest-spec.md` 명세 일치 확인
- [ ] LLM 호출 시 `PromptSanitizer` 경유 확인
- [ ] 테스트 커버리지 80% 이상
- [ ] **기능 추가/수정/삭제 시 `journeys/` e2e 동기화** (테스트 정책 §e2e 규칙 참조)

### prod 배포 전 (명시적 지시 시에만) — 순서 엄수
- [ ] ① dev 빌드·배포 완료 (`docker-compose.dev.yml`)
- [ ] ② **🚨 e2e-realbe 전체 통과** — `E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe`
      → **dev(8090) 대상으로만 실행. prod(8091) 대상 실행 절대 금지**
- [ ] ③ main 브랜치 commit & push 완료
- [ ] ④ `env/.env.prod` 모든 값 입력 (기본값 없음)
- [ ] ⑤ MariaDB 볼륨 백업 후 prod 배포

---

**마지막 업데이트**: 2026-06-03 | **담당**: Claude Code (Agent)
