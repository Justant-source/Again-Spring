/**
 * Journey 19: 광장형 검색 패널 (SearchPanel)
 *
 * - 검색 아이콘 클릭 → 검색 오버레이 열기
 * - 검색창에 쿼리 입력 → Enter → 결과 또는 공결과 상태
 * - 뒤로 가기/X 버튼 → 닫기
 * - 카테고리 변경 후 재검색
 */
import { test, expect } from '../support/no-llm-fixture'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

test.describe('Journey 19: 검색 패널 (SearchPanel)', () => {

  test('검색 — 아이콘 클릭 시 오버레이 열림', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 검색 버튼 클릭 (aria-label="검색")
    const searchBtn = page.locator('button[aria-label="검색"]')
    await expect(searchBtn).toBeVisible()
    await searchBtn.click()

    // 검색 입력창이 보임
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible({ timeout: 3_000 })
  })

  test('검색 — 쿼리 입력 → Enter → 결과 로드 또는 공결과', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 검색 열기
    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    // 검색어 입력 및 엔터
    await searchInput.fill('테스트')
    await searchInput.press('Enter')

    // 결과 뷰로 전환됨 — 결과 있으면 "사연 N건"(SearchPanel.tsx:171), 없으면 "검색 결과가 없습니다"(:167)
    // 두 상태 모두 허용(.or) + web-first 자동 재시도 → 풀스위트에서 '테스트' 매칭 글 유무에 무관하게 안정
    const emptyMsg = page.getByText('검색 결과가 없습니다')
    const resultCount = page.getByText(/[\d,]+건/)
    await expect(emptyMsg.or(resultCount).first()).toBeVisible({ timeout: 8_000 })
  })

  test('검색 — 최근 검색 목록 표시', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 첫 번째 검색
    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await searchInput.fill('첫번째')
    await searchInput.press('Enter')
    await page.waitForTimeout(1500)

    // 뒤로 가기 (결과 뷰 → 진입 뷰)
    const backBtn = page.locator('span:has-text("‹")').first()
    await backBtn.click()
    await page.waitForTimeout(500)

    // 최근 검색 헤더 표시됨 (localStorage 저장됨)
    await expect(page.locator('text=최근 검색')).toBeVisible()
  })

  test('검색 — 뒤로 가기 버튼으로 닫기', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 검색 열기
    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    // 진입 뷰에서 뒤로 버튼 → 닫기
    const backBtn = page.locator('span:has-text("‹")').first()
    await backBtn.click()
    await page.waitForTimeout(500)

    // 오버레이가 닫혀야 함
    await expect(searchInput).not.toBeVisible({ timeout: 3_000 })
  })

  test('검색 — X 버튼으로 검색어 지우기', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 검색 열기
    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    // 검색어 입력
    await searchInput.fill('테스트입력')
    await page.waitForTimeout(300)

    // X 버튼 찾기 및 클릭 (✕ 스팬)
    const clearBtn = page.locator('span:has-text("✕")').last()
    await expect(clearBtn).toBeVisible()
    await clearBtn.click()

    // 입력창이 비워짐
    await expect(searchInput).toHaveValue('')
  })

  test('검색 — 카테고리별 검색 범위', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // 연인 카테고리 선택
    await page.getByRole('button', { name: '연인' }).click()
    await page.waitForTimeout(500)

    // 검색 열기
    await page.locator('button[aria-label="검색"]').click()
    const searchInput = page.locator('input[placeholder*="검색"]')
    await expect(searchInput).toBeVisible()

    // 입력창 플레이스홀더에 "연인 광장에서 검색" 포함
    const placeholder = await searchInput.getAttribute('placeholder')
    expect(placeholder).toContain('연인')
    expect(placeholder).toContain('광장')
  })
})
