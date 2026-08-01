/**
 * Journey 21: 비밀번호 찾기 / 재설정
 *
 * - forgot-password UI 스모크 (존재하지 않는 이메일 — 실계정 비밀번호 미변경)
 * - password_reset_tokens SQL 시드 → /reset-password/[token] 완주 → 로그인
 * - afterAll에서 test6 password_hash를 test123으로 복구
 */
import { test, expect } from '../support/no-llm-fixture'
import { PERSONAS } from '../fixtures/personas'
import { runSqlScript, sql } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const PERSONA = PERSONAS[5] // test6 — PRELOGIN 외 격리
const RESET_TOKEN = `e2e_reset_${Date.now()}`
const NEW_PASSWORD = 'e2eReset99!'
const TEST123_HASH = '$2a$12$9EFz.LWcKCU9N/UEPETS7OwIRVslpITrtGseQe1GiqZOMgQ9gCic6'

test.describe('Journey 21-A: 비밀번호 찾기 UI', () => {

  test('/forgot-password — 이메일 제출 → 확인 안내', async ({ page }) => {
    await page.goto(`${BASE}/forgot-password`)
    await expect(page.getByPlaceholder('이메일')).toBeVisible({ timeout: 8_000 })
    // 존재하지 않는 이메일 — anti-enumeration 200, 실계정 비밀번호 미변경
    await page.getByPlaceholder('이메일').fill('e2e-nonexistent@again.com')
    await page.getByRole('button', { name: '임시 비밀번호 받기' }).click()
    await expect(page.getByText('메일을 확인해주세요')).toBeVisible({ timeout: 8_000 })
  })
})

test.describe('Journey 21-B: 토큰 재설정 → 로그인', () => {

  test.beforeAll(() => {
    runSqlScript(`
      DELETE FROM password_reset_tokens WHERE email='${PERSONA.email}';
      INSERT INTO password_reset_tokens (email, token, created_at, expires_at, used)
      VALUES ('${PERSONA.email}', '${RESET_TOKEN}', NOW(3), DATE_ADD(NOW(3), INTERVAL 30 MINUTE), 0);
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

  test('시드 토큰으로 비밀번호 변경 후 새 비밀번호 로그인', async ({ page }) => {
    await page.goto(`${BASE}/reset-password/${RESET_TOKEN}`)
    await expect(page.getByPlaceholder('새 비밀번호 (8자 이상)')).toBeVisible({ timeout: 8_000 })
    await page.getByPlaceholder('새 비밀번호 (8자 이상)').fill(NEW_PASSWORD)
    await page.getByPlaceholder('비밀번호 확인').fill(NEW_PASSWORD)
    await page.getByRole('button', { name: '비밀번호 변경' }).click()
    await expect(page.getByText('비밀번호가 변경되었어요')).toBeVisible({ timeout: 10_000 })

    await page.goto(`${BASE}/login`)
    await page.getByPlaceholder('이메일').fill(PERSONA.email)
    await page.getByPlaceholder('비밀번호').fill(NEW_PASSWORD)
    await page.getByRole('button', { name: '로그인' }).click()
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 })
    const token = await page.evaluate(() => localStorage.getItem('again-spring-token'))
    expect(token).toBeTruthy()
  })
})
