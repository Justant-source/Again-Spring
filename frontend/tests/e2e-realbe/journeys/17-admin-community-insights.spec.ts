/**
 * Journey 17: 어드민 커뮤니티 인사이트 (/admin/stats 개편)
 *
 * A. 기간 선택 — 7/14/30/90일 버튼
 * B. 핵심 지표 — DAU/WAU/MAU/Stickiness 카드
 * C. 퍼널·자생도 차트 렌더
 * D. 기존 리텐션·역산 채움 동작 유지 확인
 * E. API 검증 — insights·traffic 엔드포인트
 *
 * LLM 가드레일: 집계 읽기 전용.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { ADMIN_STATS } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

// ── A·B. 기본 렌더링 ─────────────────────────────────────────────
test.describe('Journey 17-A: 인사이트 섹션 렌더링', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('기간 선택 버튼 그룹이 표시됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/, { timeout: 10_000 })

    const periodSelect = page.locator(ADMIN_STATS.periodSelect)
    await expect(periodSelect).toBeVisible({ timeout: 10_000 })
    // 7일 버튼 존재 확인
    await expect(page.getByRole('button', { name: '7일' })).toBeVisible()
    await expect(page.getByRole('button', { name: '30일' })).toBeVisible()
  })

  test('인사이트 섹션이 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/)

    const insights = page.locator(ADMIN_STATS.insights)
    await expect(insights).toBeVisible({ timeout: 12_000 })
  })

  test('참여 퍼널이 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/)

    await page.locator(ADMIN_STATS.insights).waitFor({ timeout: 12_000 })
    const funnel = page.locator(ADMIN_STATS.funnel)
    await expect(funnel).toBeVisible({ timeout: 8_000 })
  })

  test('자생도 차트가 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/)

    await page.locator(ADMIN_STATS.insights).waitFor({ timeout: 12_000 })
    const chart = page.locator(ADMIN_STATS.productionRatio)
    await expect(chart).toBeVisible({ timeout: 8_000 })
  })

  test('30일 버튼 클릭 시 인사이트 섹션이 다시 로드됨', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/)

    await page.locator(ADMIN_STATS.periodSelect).waitFor({ timeout: 10_000 })

    // 현재 7일이 기본값인 경우
    const btn30 = page.getByRole('button', { name: '30일' })
    await btn30.click()

    // insights 섹션이 여전히 보임
    await expect(page.locator(ADMIN_STATS.insights)).toBeVisible({ timeout: 10_000 })
  })
})

// ── C. 기존 기능 보존 ────────────────────────────────────────────
test.describe('Journey 17-B: 기존 리텐션 기능 보존', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('기존 일일 추이 섹션이 여전히 존재함', async ({ page }) => {
    await page.goto(`${BASE}/admin/stats`)
    await page.waitForURL(/\/admin\/stats/)

    // 기존 리텐션/역산채움 영역은 하단에 유지
    await expect(page.locator('body')).toContainText(/리텐션|역산/, { timeout: 10_000 })
  })
})

// ── D. API 검증 ──────────────────────────────────────────────────
test.describe('Journey 17-C: 인사이트·트래픽 API', () => {

  test('GET /api/admin/dashboard/insights → 200', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/insights?days=30&realOnly=true`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(typeof body.dau).toBe('number')
    expect(typeof body.mau).toBe('number')
    expect(body.funnel).toBeDefined()
    expect(Array.isArray(body.productionSeries)).toBe(true)
  })

  test('GET /api/admin/dashboard/traffic → 200', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/dashboard/traffic?days=30`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(Array.isArray(body.dailySeries)).toBe(true)
    expect(Array.isArray(body.topSources)).toBe(true)
  })
})
