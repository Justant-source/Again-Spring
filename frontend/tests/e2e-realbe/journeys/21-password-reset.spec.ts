/**
 * Journey 21: 비밀번호 찾기 / 재설정
 *
 * - forgot-password UI 스모크 (존재하지 않는 이메일)
 * - password_reset_tokens SQL 시드 → reset API → login API
 */
import { test, expect } from '../support/no-llm-fixture'
import { PERSONAS } from '../fixtures/personas'
import { runSqlScript } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const PERSONA = PERSONAS[5] // test6 — PRELOGIN 외 격리
const NEW_PASSWORD = 'e2eReset99!'
const TEST123_HASH = '$2a$12$9EFz.LWcKCU9N/UEPETS7OwIRVslpITrtGseQe1GiqZOMgQ9gCic6'

test.describe('Journey 21-A: 비밀번호 찾기 UI', () => {

  test('/forgot-password — 이메일 제출 → 확인 안내', async ({ page }) => {
    await page.goto(`${BASE}/forgot-password`)
    await page.waitForLoadState('domcontentloaded')

    const emailInput = page.locator('input[type="email"], input[placeholder="이메일"]').first()
    await expect(emailInput).toBeVisible({ timeout: 15_000 })
    await emailInput.fill('e2e-nonexistent@again.com')

    await page.getByRole('button', { name: /임시 비밀번호 받기|발송/ }).click()
    await expect(page.getByText(/메일|확인/)).toBeVisible({ timeout: 10_000 })
  })
})

test.describe('Journey 21-B: 토큰 재설정 → 로그인', () => {
  let resetToken = ''

  test.beforeEach(() => {
    resetToken = `e2e_reset_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    runSqlScript(`
      DELETE FROM password_reset_tokens WHERE email='${PERSONA.email}';
      INSERT INTO password_reset_tokens (email, token, created_at, expires_at, used)
      VALUES ('${PERSONA.email}', '${resetToken}', NOW(3), DATE_ADD(NOW(3), INTERVAL 30 MINUTE), 0);
    `)
  })

  test.afterAll(() => {
    try {
      runSqlScript(`
        DELETE FROM password_reset_tokens WHERE email='${PERSONA.email}';
        UPDATE users SET password_hash='${TEST123_HASH}', must_change_password=0 WHERE email='${PERSONA.email}';
      `)
    } catch { /* ignore */ }
  })

  test('시드 토큰으로 비밀번호 변경 후 API 로그인', async ({ request }) => {
    const apiRes = await request.post(`${BASE}/api/auth/reset-password`, {
      data: { token: resetToken, newPassword: NEW_PASSWORD },
    })
    expect(apiRes.ok(), `reset-password: ${apiRes.status()} ${await apiRes.text()}`).toBeTruthy()

    const loginRes = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA.email, password: NEW_PASSWORD },
    })
    expect(loginRes.ok(), `login: ${loginRes.status()} ${await loginRes.text()}`).toBeTruthy()
    const body = await loginRes.json()
    expect(body.accessToken || body.token || body.access_token).toBeTruthy()
  })
})
