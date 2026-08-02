/**
 * Journey 09: 권한 및 라우트 가드
 *
 * - 미인증 /admin → /login?next=/admin
 * - 미인증 admin 하위 경로 (/admin/ai-user) → /login
 * - 게스트 /profile → /login
 * - 등록 회원(USER only) /admin → / 리다이렉트
 * - admin API 403 스모크 (test5) — 11/13/14에서 통합
 * - 게스트 하단 탭 → 게스트 안내 시트 (라우팅 없음)
 * - 로그인 페이지 정리: "게스트로 둘러보기" 버튼·"계정이 없으신가요?" 없음
 * - GuestInfoSheet 로그인 버튼 → /login 이동
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONAS } from '../fixtures/personas'
import { tokenFromStorageState } from '../support/api'
import {
  USER_CHIP,
  GUEST_INFO_SHEET,
  NAV_NOTIFICATIONS,
  NAV_ACTIVITY,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
// test5 = USER only (globalSetup이 ADMIN/TESTER 미부여)
const PERSONA_TEST5 = PERSONAS[4]

// ── A. 미인증 라우트 가드 ─────────────────────────────────────────
test.describe('Journey 09-A: 미인증 라우트 가드', () => {

  test('미인증 — /admin 접근 → /login?next=/admin 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
    expect(page.url()).toMatch(/next=%2Fadmin|next=\/admin/)
  })

  test('게스트 — /profile 접근 → /login 리다이렉트', async ({ page }) => {
    // /guest를 거치지 않고 바로 /profile (게스트 자동발급이지만 profile은 회원 전용)
    await page.goto(`${BASE}/profile`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })

  test('미인증 — /admin/ai-user → /login 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-user`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })
})

// ── B. 등록 회원 /admin 가드 ─────────────────────────────────────
test.describe('Journey 09-B: 등록 회원 /admin 가드', () => {

  test('registered(USER only) — /admin 접근 → / 리다이렉트', async ({ browser }) => {
    const statePath = authStatePath(PERSONA_TEST5.email)
    const ctx = await browser.newContext({ storageState: statePath })
    const pg = await ctx.newPage()

    await pg.goto(`${BASE}/admin`)
    await pg.waitForURL(/\/$|\/community|\/session|\/onboarding/, { timeout: 10_000 })
    expect(pg.url()).not.toContain('/admin')

    await pg.close()
    await ctx.close()
  })
})

// ── B2. admin API 403 스모크 (11/13/14에서 통합) ─────────────────
test.describe('Journey 09-B2: admin API 403 스모크 (USER only)', () => {
  const endpoints = [
    '/api/admin/ai-rules/global',
    '/api/admin/marketing/jobs',
    '/api/admin/marketing/credentials',
  ] as const

  for (const path of endpoints) {
    test(`비-어드민(test5) — GET ${path} → 403`, async ({ request }) => {
      const token = tokenFromStorageState(PERSONA_TEST5.email)
      test.skip(!token, 'test5 storageState 없음 — global-setup 먼저 실행')

      const res = await request.get(`${BASE}${path}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      expect([403, 401]).toContain(res.status())
    })
  }
})

// ── C. 게스트 하단 네비게이션 → 안내 시트 ────────────────────────
test.describe('Journey 09-C: 게스트 하단 네비게이션 안내 시트', () => {

  test('게스트 — 하단 "알림" 탭 → 게스트 안내 시트 (라우팅 없음)', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.locator(NAV_NOTIFICATIONS)).toBeVisible({ timeout: 12_000 })

    await page.locator(NAV_NOTIFICATIONS).click()
    await expect(page.locator(GUEST_INFO_SHEET)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('게스트로는')).toBeVisible()
    expect(page.url()).not.toContain('/notifications')
  })

  test('게스트 — 하단 "내 활동" 탭 → 게스트 안내 시트 (라우팅 없음)', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.locator(NAV_ACTIVITY)).toBeVisible({ timeout: 12_000 })

    await page.locator(NAV_ACTIVITY).click()
    await expect(page.locator(GUEST_INFO_SHEET)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('게스트로는')).toBeVisible()
    expect(page.url()).not.toContain('/profile')
    expect(page.url()).not.toContain('/login')
  })
})

// ── D. 로그인 페이지 정리 ─────────────────────────────────────────
test.describe('Journey 09-D: 로그인 페이지 정리 (회귀)', () => {

  test('"게스트로 둘러보기" 버튼·"계정이 없으신가요?" 문구 없음', async ({ page }) => {
    await page.goto(`${BASE}/login`)
    await expect(page.getByPlaceholder('이메일')).toBeVisible({ timeout: 8_000 })

    await expect(page.getByText('게스트로 둘러보기')).toHaveCount(0)
    await expect(page.getByText('계정이 없으신가요')).toHaveCount(0)

    // 회원가입·비밀번호 찾기 링크는 유지
    await expect(page.getByRole('link', { name: '회원가입' })).toBeVisible()
    await expect(page.getByRole('link', { name: '비밀번호 찾기' })).toBeVisible()
  })
})

// ── E. GuestInfoSheet 로그인 버튼 ────────────────────────────────
test.describe('Journey 09-E: GuestInfoSheet 로그인 버튼', () => {

  test('UserChip 클릭 → 시트 내 로그인 버튼 → /login 이동', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.locator(USER_CHIP)).toBeVisible({ timeout: 12_000 })

    await page.locator(USER_CHIP).click()
    const sheet = page.locator(GUEST_INFO_SHEET)
    await expect(sheet).toBeVisible({ timeout: 5_000 })

    await expect(sheet.getByRole('button', { name: '회원가입하기' })).toBeVisible()
    await expect(sheet.getByRole('button', { name: '게스트로 계속하기' })).toBeVisible()
    const loginBtn = sheet.getByRole('button', { name: '로그인' })
    await expect(loginBtn).toBeVisible()

    await loginBtn.click()
    await page.waitForURL(/\/login/, { timeout: 8_000 })
    expect(page.url()).toContain('/login')
  })
})
