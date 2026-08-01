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
├── e2e-realbe/        # Playwright real-BE (npm run test:e2e:realbe)
│   ├── journeys/      # 01–22 journey specs (유일한 실행 대상)
│   ├── fixtures/      # personas, auth-state, cleanup, api-helpers
│   └── support/       # global-setup, selectors, no-llm-fixture, db, api
├── fixtures/          # Vitest 데이터 팩토리
├── setup.ts           # Vitest setup
└── README.md
```

> `invariants/` · `flows/` 디렉터리는 비어 있거나 삭제됨 — journey 스위트만 사용.

## Running Tests

```bash
# Vitest
npm test

# Playwright — a11y only
npm run test:e2e
npm run test:a11y

# Playwright — real-BE (prod :8091 게이트)
# 사전 조건: prod compose UP + curl http://localhost:8091/api/health
E2E_BASE_URL=http://localhost:8091 npm run test:e2e:realbe
```

## Coverage Targets (Vitest)

| 항목 | 목표 |
|---|---|
| Lines / Functions / Statements | 80% |
| Branches | 70% |

## Writing real-BE specs

- `tests/e2e-realbe/fixtures/personas.ts` — 페르소나
- `tests/e2e-realbe/support/selectors.ts` — data-testid
- `tests/e2e-realbe/support/no-llm-fixture.ts` — 필수 import (LLM 금지)
- `support/api.ts`의 `createPost`만 사용 (`jurorCount:0`)
- 상세: `docs/frontend/testing.md`
