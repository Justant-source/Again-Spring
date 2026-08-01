/**
 * 실 BE 대상 FE E2E 설정.
 *
 * 사전 조건:
 *   cd env && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
 *   → curl http://localhost:8091/api/health (UP 확인)
 *
 * 구조:
 *   Docker nginx(8091) → /api/* → BE 컨테이너  (CORS: same-origin, MSW: 비활성)
 *                      → /*    → FE 컨테이너  (NODE_ENV=production, MSW 비활성)
 *
 * 실행: npm run test:e2e:realbe
 *       E2E_BASE_URL=http://localhost:8091 npm run test:e2e:realbe
 *
 * spec 위치: tests/e2e-realbe/journeys/*.spec.ts
 *   01-guest-golden-path       — 게스트 진입·피드 (@mobile)
 *   02-member-auth-session     — 로그인/로그아웃/세션
 *   03-community-feed-compose  — 피드·작성
 *   04-voting                  — 투표·soft-delete 복구
 *   05-comments-lifecycle      — 댓글 CRUD·신고 제출
 *   06-partner-invite-answer   — 초대·paired·publish-mode
 *   07-profile                 — 프로필·정보 수정
 *   08-email-verification-signup
 *   09-permissions-guards      — 라우트 가드·admin API 403 스모크
 *   10-landing                 — 랜딩 (@mobile)
 *   11-admin-ai-rules          — AI 규칙·content UI·prompts 회귀
 *   12-og-card                 — OG 메타
 *   13-marketing-jobs(+callbacks) — 마케팅 UI·콜백
 *   14-marketing-credentials   — 플랫폼 계정 탭
 *   15-admin-ai-generation-status
 *   16-admin-dashboard-home
 *   17-admin-crawl-freshness / community-insights
 *   18-visit-tracking          — UTM·dedupe
 *   19-search-panel
 *   20-notifications
 *   21-password-reset
 *   22-jury-seeded-ui          — SQL 시드 배심원 UI (LLM 미호출)
 *
 * LLM 절대 호출 금지: support/no-llm-fixture.ts + createPost(jurorCount:0)
 */
import { defineConfig, devices } from '@playwright/test'
import path from 'path'
import { chromiumLaunchOptions } from './tests/e2e-realbe/support/browser'

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
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8091',
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
