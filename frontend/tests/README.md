# Frontend Test Infrastructure

Testing infrastructure for the Again Spring frontend.

- **Vitest** (unit/component/integration): centralized under `tests/`
- **Playwright MSW** (`npm run test:e2e`): `tests/e2e/` — a11y-only (axe WCAG checks)
- **Playwright real-BE** (`npm run test:e2e:realbe`): `tests/e2e-realbe/` — **prod 배포 전 필수 게이트** (커밋 단계 불필요)

## Directory Structure

```
tests/
├── unit/              # Pure function / utility tests (Vitest)
├── component/         # React component tests (RTL + Vitest)
├── integration/       # API/store integration tests (Vitest)
├── e2e/               # Playwright MSW — a11y only (npm run test:e2e)
│   ├── a11y.spec.ts
│   └── fixtures/      # page.route API mock (a11y에서 사용)
├── e2e-realbe/        # Playwright real-BE 회귀 방지 (npm run test:e2e:realbe)
│   ├── invariants/    # 절대 불변 규칙 4 spec (최우선)
│   ├── flows/         # frontend/docs/ux/flows/ 1:1 매핑
│   ├── fixtures/      # personas, auth-state, cleanup, api-helpers
│   └── support/       # global-setup, selectors, waits, assertions
├── fixtures/          # Vitest 데이터 팩토리 (createUser/Session/Message…)
├── setup.ts           # Vitest setup (MSW server, next mock, DOM polyfill)
└── README.md
```

## Running Tests

```bash
# Vitest (unit/component/integration) — MSW 기반
npm test
npm run test:watch
npm run test:coverage

# Playwright — a11y only (MSW dev server 필요: npm run dev)
npm run test:e2e
npm run test:a11y

# Playwright — real-BE 불변/flow 회귀 검증 (dev Docker 스택 필요)
# 사전 조건: cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
npm run test:e2e:realbe
```

## 절대 불변 규칙 (tests/e2e-realbe/invariants/)

아래 4개는 실 BE + 실 브라우저 레벨에서 검증하는 회귀 방지 최우선 spec:

| Spec | 보호 대상 | 권위본 |
|---|---|---|
| `crisis-modal-dismiss` | CrisisModal은 ESC·backdrop으로 닫히지 않는다 | `frontend/docs/ux/principles.md`, `08-crisis.md` |
| `crisis-dual-defense` | FE + BE 이중 차단 (409 메시지 미저장) | `shared/docs/policies/crisis-detection.md` |
| `duo-message-isolation` | 상대 메시지 원문이 DOM·API에 절대 노출 안 됨 | `frontend/docs/ux/principles.md`, `06-duo.md` |
| `contribution-ratio-legal-notice` | 법적 안내 박스가 항상 표시·미은닉 | `frontend/README.md` 절대 불변 규칙 |

## Coverage Targets (Vitest)

| 항목 | 목표 |
|---|---|
| Lines / Functions / Statements | 80% |
| Branches | 70% |

## Writing Tests

Vitest 테스트 작성법 및 픽스처 사용 예시는 `tests/fixtures/index.ts`의 팩토리 함수 참조.

Playwright real-BE spec 작성 시:
- `tests/e2e-realbe/fixtures/personas.ts` — 페르소나 10명 참조
- `tests/e2e-realbe/support/selectors.ts` — data-testid 컨벤션
- `tests/e2e-realbe/support/waits.ts` — LLM 응답 폴링
- test.beforeAll에서 `cleanup()` 호출 필수
