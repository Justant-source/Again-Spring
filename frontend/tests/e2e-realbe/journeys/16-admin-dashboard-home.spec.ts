/**
 * Journey 16: 어드민 대시보드 홈 (V2 관제센터 개편)
 *
 * A. 페이지 기본 렌더링 — ActionCenter·KPI 그리드·핫 게시글
 * B. Cmd+K 팔레트
 *
 * API 계약은 AdminDashboardControllerTest + DashboardOpsServiceTest로 이관.
 * 미로그인 /admin 가드는 Journey 09에 위임.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { ADMIN_DASHBOARD } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

test.describe('Journey 16-A: 대시보드 홈 기본 렌더링', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('ActionCenter·KPI·핫 게시글·헤딩이 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    await expect(page.locator(ADMIN_DASHBOARD.actionCenter)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(ADMIN_DASHBOARD.kpiGrid)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(ADMIN_DASHBOARD.hotPosts)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('h1, h2').first()).toContainText(/관리자|대시보드/, { timeout: 8_000 })
  })
})

test.describe('Journey 16-B: Cmd+K 커맨드 팔레트', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('Ctrl+K로 열리고 ESC로 닫힘', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})

    await page.keyboard.press('Control+k')
    const palette = page.locator(ADMIN_DASHBOARD.commandPalette)
    await expect(palette).toBeVisible({ timeout: 5_000 })

    await page.keyboard.press('Escape')
    await expect(palette).not.toBeVisible({ timeout: 3_000 })
  })
})
