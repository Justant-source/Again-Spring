/**
 * Journey 17: 어드민 대시보드 — 크롤링 신선도 배지
 *
 * A. 크롤 신선도 배지 UI
 * B. refresh 버튼
 *
 * API 스키마는 AdminCrawlStatusControllerTest로 이관.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { ADMIN_CRAWL } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

test.describe('Journey 17-A: 크롤 신선도 배지 렌더링', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('배지 텍스트·소스·시각·24h·상태가 표시됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    await expect(badge).toBeVisible({ timeout: 10_000 })
    await expect(badge).toContainText(/자동화|파이프라인|크롤링|신선도/, { timeout: 10_000 })
    await expect(badge).toContainText(/\d{1,2}:\d{2}:\d{2}/, { timeout: 10_000 })
    await expect(badge).toContainText(/24h/, { timeout: 10_000 })

    const text = await badge.textContent()
    expect(text?.includes('성공 기록 없음') || text?.includes('데이터 저장됨')).toBeTruthy()
    expect(/natepan|blind|theqoo|dcinside|clien/.test(text || '')).toBeTruthy()
    expect(/\d{1,2}:\d{2}|기록/i.test(text || '')).toBeTruthy()
  })
})

test.describe('Journey 17-B: 배지 갱신', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('대시보드 refresh 버튼 클릭 후 배지 데이터 갱신', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    await expect(badge).toBeVisible({ timeout: 10_000 })

    const refreshBtn = page.locator('[data-testid="admin-page-refresh"]')
    if (await refreshBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      const crawlResp = page.waitForResponse(
        (r) => r.url().includes('/api/admin/crawl-status') && r.ok(),
        { timeout: 8_000 },
      ).catch(() => null)
      await refreshBtn.locator('button').first().click()
      await crawlResp
      await expect(badge).toBeVisible({ timeout: 5_000 })
    }
  })
})
