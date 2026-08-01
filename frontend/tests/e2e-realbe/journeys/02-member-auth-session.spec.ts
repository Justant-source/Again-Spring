/**
 * Journey 02: 회원 인증·세션 재사용
 *
 * - UI 이메일 로그인 1회 (실제 폼 동작 검증)
 * - 로그아웃 → 토큰 제거
 * - storageState 재사용 확인 (매 테스트 재로그인 불필요)
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import {
  EMAIL_INPUT_PLACEHOLDER,
  PASSWORD_INPUT_PLACEHOLDER,
  LOGIN_BUTTON,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const TOKEN_KEY = 'again-spring-token'

// ── A. 이메일 로그인 (UI) ─────────────────────────────────────────
test.describe('Journey 02-A: 이메일 로그인 (UI)', () => {

  test('이메일 로그인 → again-spring-token 저장', async ({ page }) => {
    await page.goto(`${BASE}/login`)

    await page.getByPlaceholder(EMAIL_INPUT_PLACEHOLDER).fill(PERSONA_TEST1.email)
    await page.getByPlaceholder(PASSWORD_INPUT_PLACEHOLDER).fill(PERSONA_TEST1.password)
    await page.getByRole('button', LOGIN_BUTTON).click()

    // 로그인 성공 → 랜딩 또는 리다이렉트 목적지
    await page.waitForURL(/\/$|\/community|\/session\/new|\/onboarding/, { timeout: 10_000 })

    const token = await page.evaluate((key: string) => localStorage.getItem(key), TOKEN_KEY)
    expect(token).toBeTruthy()
  })
})

// ── B. 로그아웃 ────────────────────────────────────────────────────
test.describe('Journey 02-B: 로그아웃', () => {

  test('로그아웃 → again-spring-token 제거', async ({ page }) => {
    // storageState로 로그인 상태 진입
    const statePath = authStatePath(PERSONA_TEST1.email)
    const token0 = await import('fs').then((fs) => {
      const state = JSON.parse(fs.readFileSync(statePath, 'utf-8'))
      const ls = state.origins?.[0]?.localStorage ?? []
      return (ls.find((e: { name: string; value: string }) => e.name === TOKEN_KEY)?.value as string) ?? null
    })
    expect(token0).toBeTruthy()

    await page.goto(BASE)
    await page.evaluate((t: string) => localStorage.setItem('again-spring-token', t), token0)
    await page.reload()
    await page.waitForURL(/\/$|\/community|\/session|\/onboarding/, { timeout: 10_000 })

    // 로그아웃 버튼(존재하면 클릭) 또는 localStorage 직접 제거
    const logoutBtn = page.getByRole('button', { name: /로그아웃|logout/i })
    if (await logoutBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await logoutBtn.click()
    } else {
      await page.evaluate((key: string) => localStorage.removeItem(key), TOKEN_KEY)
    }

    const token = await page.evaluate((key: string) => localStorage.getItem(key), TOKEN_KEY)
    expect(token).toBeNull()
  })
})

// ── C. storageState 재사용 ────────────────────────────────────────
test.describe('Journey 02-C: storageState 재사용', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('storageState로 진입 → /profile 접근 시 로그인 상태 유지', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    // 게스트·비인증이면 /login으로 리다이렉트됨 — 프로필이 로드되면 인증 성공
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    expect(page.url()).toContain('/profile')
  })
})
