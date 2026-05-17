import { defineConfig, devices } from '@playwright/test'

/**
 * 실 BE 대상 FE E2E 설정.
 *
 * 사전 조건:
 *   1. DB (MariaDB): cd env && docker compose up -d
 *   2. BE: cd backend && ./gradlew bootRun  → localhost:8080
 *   3. 이 설정의 webServer가 NEXT_PUBLIC_DISABLE_MSW=true 로 FE를 띄움
 *
 * 실행: npx playwright test --config=playwright.realbe.config.ts
 * 또는: npm run test:e2e:realbe
 */
export default defineConfig({
  testDir: './tests/e2e-realbe',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,   // 실 BE 상태 공유 — 순차 실행
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on',
    screenshot: 'on',
    video: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium-realbe',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    command: 'NEXT_PUBLIC_DISABLE_MSW=true npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: true,  // 이미 띄워진 dev server 재사용
    timeout: 120000,
    env: {
      NEXT_PUBLIC_DISABLE_MSW: 'true',
      NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
    },
  },
})
