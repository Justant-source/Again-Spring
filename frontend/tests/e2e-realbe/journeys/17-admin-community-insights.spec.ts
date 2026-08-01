/**
 * Journey 17: 어드민 커뮤니티 인사이트 (/admin/stats)
 *
 * UI 스모크 — 기간·인사이트·퍼널·자생도·리텐션.
 * API 계약은 AdminDashboardControllerTest로 이관.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { ADMIN_STATS } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

test.describe('Journey 17-insights: 인사이트 UI', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('기간·인사이트·퍼널·자생도·리텐션이 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/, { timeout: 10_000 })

    const periodSelect = page.locator(ADMIN_STATS.periodSelect)
    await expect(periodSelect).toBeVisible({ timeout: 10_000 })
    await expect(page.getByRole('button', { name: '7일' })).toBeVisible()
    await expect(page.getByRole('button', { name: '30일' })).toBeVisible()

    await expect(page.locator(ADMIN_STATS.insights)).toBeVisible({ timeout: 12_000 })
    await expect(page.locator(ADMIN_STATS.funnel)).toBeVisible({ timeout: 8_000 })
    await expect(page.locator(ADMIN_STATS.productionRatio)).toBeVisible({ timeout: 8_000 })

    await page.getByRole('button', { name: '30일' }).click()
    await expect(page.locator(ADMIN_STATS.insights)).toBeVisible({ timeout: 10_000 })

    await expect(page.locator('body')).toContainText(/리텐션|역산/, { timeout: 10_000 })
  })
})
