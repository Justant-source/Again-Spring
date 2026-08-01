/**
 * Journey 16: 어드민 대시보드 홈 (V2 관제센터 개편)
 *
 * A. 페이지 기본 렌더링 — ActionCenter·KPI 그리드·핫 게시글·Cmd+K
 * B. 드릴다운 이동 — ActionCenter 카드 클릭 → 해당 관리 페이지
 * C. 비관리자 접근 차단
 *
 * LLM 가드레일: 모든 엔드포인트는 DB 집계 읽기 — LLM 미호출.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { ADMIN_DASHBOARD } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

// ── A. 기본 렌더링 ───────────────────────────────────────────────
test.describe('Journey 16-A: 대시보드 홈 기본 렌더링', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('ActionCenter 섹션이 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    const actionCenter = page.locator(ADMIN_DASHBOARD.actionCenter)
    await expect(actionCenter).toBeVisible({ timeout: 10_000 })
  })

  test('KPI 그리드가 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const kpiGrid = page.locator(ADMIN_DASHBOARD.kpiGrid)
    await expect(kpiGrid).toBeVisible({ timeout: 10_000 })
  })

  test('핫 게시글 카드가 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const hotPosts = page.locator(ADMIN_DASHBOARD.hotPosts)
    await expect(hotPosts).toBeVisible({ timeout: 10_000 })
  })

  test('헤딩이 "관리자 대시보드" 텍스트를 포함함', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    await expect(page.locator('h1, h2').first()).toContainText(/관리자|대시보드/, { timeout: 8_000 })
  })
})

// ── B. Cmd+K 팔레트 ──────────────────────────────────────────────
test.describe('Journey 16-B: Cmd+K 커맨드 팔레트', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('Ctrl+K 단축키로 팔레트가 열림', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    // 어드민 페이지가 완전히 로드될 때까지 대기
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})

    await page.keyboard.press('Control+k')

    const palette = page.locator(ADMIN_DASHBOARD.commandPalette)
    await expect(palette).toBeVisible({ timeout: 5_000 })
  })

  test('팔레트에서 ESC로 닫힘', async ({ page }) => {
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

// ── C. API 검증 ──────────────────────────────────────────────────
test.describe('Journey 16-C: 신규 대시보드 API', () => {

  test('GET /api/admin/dashboard/action-center → 200', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/action-center`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(typeof body.pendingReports).toBe('number')
    expect(typeof body.openInquiries).toBe('number')
    expect(typeof body.marketingAwaitingApproval).toBe('number')
    expect(typeof body.aiFailuresToday).toBe('number')
  })

  test('GET /api/admin/dashboard/kpis → 200 + 배열', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/kpis?days=7`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(Array.isArray(body)).toBe(true)
    if (body.length > 0) {
      expect(typeof body[0].key).toBe('string')
      expect(Array.isArray(body[0].sparkline)).toBe(true)
    }
  })

  test('GET /api/admin/dashboard/hot-posts → 200 + 배열', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/hot-posts?hours=48&limit=5`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(Array.isArray(body)).toBe(true)
  })

  test('GET /api/admin/dashboard/pulse → 200 + data 배열', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/pulse?hours=24`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(Array.isArray(body.data)).toBe(true)
  })

  test('미인증 → /api/admin/dashboard/action-center → 401/403', async ({ request }) => {
    const res = await request.get(`${BASE}/api/admin/dashboard/action-center`)
    expect([401, 403]).toContain(res.status())
  })
})

// ── D. 비관리자 접근 차단 ─────────────────────────────────────────
test.describe('Journey 16-D: 비관리자 차단', () => {

  test('미로그인 → /admin → /login 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })
})
