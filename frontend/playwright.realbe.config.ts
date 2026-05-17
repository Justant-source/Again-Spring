import { defineConfig, devices } from '@playwright/test'

/**
 * 실 BE 대상 FE E2E 설정.
 *
 * 사전 조건:
 *   cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
 *   → localhost:8090 nginx 응답 확인
 *
 * 구조:
 *   Docker nginx(8090) → /api/* → BE 컨테이너  (CORS: same-origin, MSW: production이므로 비활성)
 *                      → /*    → FE 컨테이너  (NODE_ENV=production, MSW 비활성)
 *
 * webServer 없음 — 이미 실행 중인 Docker 스택 재사용.
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
    baseURL: 'http://localhost:8090',
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
  // webServer 없음 — Docker dev 스택(localhost:8090)을 그대로 사용
})
