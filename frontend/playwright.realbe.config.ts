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
 *
 * spec 위치: tests/e2e-realbe/journeys/*.spec.ts
 *   01-guest-golden-path  — 게스트 최초 여정
 *   02-member-auth-session — 이메일 로그인/로그아웃/세션 재사용
 *   03-community-feed-compose — 광장 피드·작성
 *   04-voting — 투표 (게스트 포함, soft-delete 복구)
 *   05-comments-lifecycle — 댓글 CRUD
 *   06-partner-invite-answer — 상대 초대·답변·paired
 *   07-profile — 프로필·정보 수정
 *   08-email-verification-signup — 이메일 인증·가입 완주
 *   09-permissions-guards — 권한 게이팅
 *   10-landing — 랜딩 페이지
 *   11-admin-ai-rules — 관리자 AI 규칙관리 (비-LLM 경로만)
 *
 * LLM 절대 호출 금지:
 *   - support/no-llm-fixture.ts 가드레일이 모든 spec에 적용됨
 *   - jurorCount=0 강제 (support/api.ts의 createPost)
 *   - /jury/retry, /analyze 계열 엔드포인트 접근 시 테스트 즉시 실패
 */
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
      // chromium은 journeys/ 전체 실행
    },
    {
      name: 'mobile-realbe',
      use: { ...devices['Pixel 5'] },
      // 모바일은 @mobile 태그가 붙은 스모크 케이스만 실행
      // (전체 재실행 제거: 벽시계 시간 2배, GuestSessionRateLimiter 소진 방지)
      grep: /@mobile/,
    },
  ],
})
