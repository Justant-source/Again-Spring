import { defineConfig, devices } from '@playwright/test'
import path from 'path'

/**
 * 실 BE 대상 FE E2E 설정.
 *
 * 사전 조건:
 *   cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
 *   → curl http://localhost:8090/api/health (UP 확인)
 *
 * 구조:
 *   Docker nginx(8090) → /api/* → BE 컨테이너  (CORS: same-origin, MSW: 비활성)
 *                      → /*    → FE 컨테이너  (NODE_ENV=production, MSW 비활성)
 *
 * 실행: npm run test:e2e:realbe
 *       npx playwright test --config=playwright.realbe.config.ts
 */
export default defineConfig({
  testDir: './tests/e2e-realbe',
  testMatch: ['invariants/**/*.spec.ts', 'flows/**/*.spec.ts', 'guest-golden-path.spec.ts'],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: 'html',
  globalSetup: path.resolve('./tests/e2e-realbe/support/global-setup.ts'),
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8090',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
  },
  timeout: 120_000,
  expect: { timeout: 10_000 },

  projects: [
    {
      name: 'chromium-realbe',
      use: { ...devices['Desktop Chrome'] },
      // chromium은 전체 spec 실행 (invariants · flows · guest-golden-path)
    },
    {
      name: 'mobile-realbe',
      use: { ...devices['Pixel 5'] },
      // guest-golden-path 제외: In-memory GuestSessionRateLimiter(1/24h)가 chromium 실행 후 소진됨
      // invariants + flows만 실행해 모바일 뷰포트 회귀를 검증
      testMatch: ['invariants/**/*.spec.ts', 'flows/**/*.spec.ts'],
    },
  ],
})
