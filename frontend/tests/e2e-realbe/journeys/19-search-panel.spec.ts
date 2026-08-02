/**
 * Journey 19: 광장형 검색 패널 (SearchPanel)
 *
 * - 검색 아이콘 클릭 → 검색 오버레이 열기
 * - 검색창에 쿼리 입력 → Enter → 결과 또는 공결과 상태
 * - 뒤로 가기/X 버튼 → 닫기
 * - 카테고리 변경 후 재검색
 */
import { test, expect } from '../support/no-llm-fixture'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Journey 19: 검색 패널 (SearchPanel)', () => {

  test('검색 — 아이콘 클릭 시 오버레이 열림', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    const searchBtn = page.locator('button[aria-label="검색"]')
    await expect(searchBtn).toBeVisible()
    await searchBtn.click()

    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible({ timeout: 3_000 })
  })

  test('검색 — 쿼리 입력 → Enter → 결과 로드 또는 공결과', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    await searchInput.fill('테스트')
    await searchInput.press('Enter')

    const emptyMsg = page.getByText('검색 결과가 없습니다')
    const resultCount = page.getByText(/[\d,]+건/)
    await expect(emptyMsg.or(resultCount).first()).toBeVisible({ timeout: 8_000 })
  })

  test('검색 — 최근 검색 목록 표시', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await searchInput.fill('첫번째')
    await searchInput.press('Enter')

    await expect(
      page.getByText('검색 결과가 없습니다').or(page.getByText(/[\d,]+건/)).first(),
    ).toBeVisible({ timeout: 8_000 })

    const backBtn = page.locator('span:has-text("‹")').first()
    await backBtn.click()
    await expect(page.locator('text=최근 검색')).toBeVisible({ timeout: 5_000 })
  })

  test('검색 — 뒤로 가기 버튼으로 닫기', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    const backBtn = page.locator('span:has-text("‹")').first()
    await backBtn.click()
    await expect(searchInput).not.toBeVisible({ timeout: 3_000 })
  })

  test('검색 — X 버튼으로 검색어 지우기', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    await searchInput.fill('테스트입력')
    const clearBtn = page.locator('span:has-text("✕")').last()
    await expect(clearBtn).toBeVisible({ timeout: 3_000 })
    await clearBtn.click()
    await expect(searchInput).toHaveValue('')
  })

  test('검색 — 한 글자는 검색하지 않고 안내', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    await searchInput.fill('가')
    await searchInput.press('Enter')

    await expect(page.getByText('검색어는 두 글자 이상 입력해 주세요')).toBeVisible({ timeout: 3_000 })
    await expect(page.getByText(/[\d,]+건/)).not.toBeVisible()
  })

  test('검색 — 카테고리별 검색 범위', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    await page.getByRole('button', { name: '연인' }).click()
    await expect(page.getByRole('button', { name: '연인' })).toBeVisible()

    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    const placeholder = await searchInput.getAttribute('placeholder')
    expect(placeholder).toContain('연인')
    expect(placeholder).toContain('광장')
  })
})
