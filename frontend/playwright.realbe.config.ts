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
 *       E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
 *
 * ⚠️ Origin: BE CORS는 `localhost` 패턴을 허용. `127.0.0.1`로 열면 브라우저 POST가
 *    "Invalid CORS request"(403)로 게스트/로그인 UI가 전부 실패한다.
 *    아래 normalize가 127.0.0.1 → localhost로 강제한다 (CorsConfig에도 127 패턴 추가).
 *
 * ⚠️ prod(:8091)에서 e2e 실행 금지 — E3.
 *
 * spec 위치: tests/e2e-realbe/journeys/*.spec.ts
 *   01–22 journeys (LLM 절대 호출 금지: no-llm-fixture + createPost(jurorCount:0))
 */
import { defineConfig, devices } from '@playwright/test'
import path from 'path'
import { chromiumLaunchOptions } from './tests/e2e-realbe/support/browser'
import { assertE2EDevOnly, E2E_DEFAULT_BASE_URL } from './tests/e2e-realbe/support/env'

/** 127.0.0.1 → localhost (CORS Origin 미스매치 방지) */
function normalizeE2EBaseURL(url: string): string {
  return url.replace(/^(https?:\/\/)127\.0\.0\.1(?=[:/]|$)/i, '$1localhost')
}

const E2E_BASE_URL = assertE2EDevOnly(
  normalizeE2EBaseURL(process.env.E2E_BASE_URL ?? E2E_DEFAULT_BASE_URL),
)
process.env.E2E_BASE_URL = E2E_BASE_URL

export default defineConfig({
  testDir: './tests/e2e-realbe',
  testMatch: ['journeys/**/*.spec.ts'],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 1,
  workers: 1,
  reporter: 'html',
  globalSetup: path.resolve('./tests/e2e-realbe/support/global-setup.ts'),
  globalTeardown: path.resolve('./tests/e2e-realbe/support/global-teardown.ts'),
  use: {
    baseURL: E2E_BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.PW_VIDEO === '1' ? 'retain-on-failure' : 'off',
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
  },
  timeout: 120_000,
  expect: { timeout: 10_000 },

  projects: [
    {
      name: 'chromium-realbe',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: chromiumLaunchOptions(),
      },
    },
    {
      name: 'mobile-realbe',
      use: {
        ...devices['Pixel 5'],
        launchOptions: chromiumLaunchOptions(),
      },
      grep: /@mobile/,
    },
  ],
})
