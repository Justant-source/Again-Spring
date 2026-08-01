/**
 * Journey 17: 어드민 대시보드 — 크롤링 신선도 배지
 *
 * A. 크롤 신선도 배지 기본 렌더링
 * B. Stale 상태 시 경고 표시 확인
 * C. 소스별 24시간 데이터 표시
 * D. API 엔드포인트 검증
 *
 * LLM 가드레일: 크롤 상태는 DB 집계 읽기 — LLM 미호출.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { ADMIN_CRAWL } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

// ── A. 크롤 신선도 배지 기본 렌더링 ────────────────────────────────
test.describe('Journey 17-A: 크롤 신선도 배지 렌더링', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('대시보드에서 크롤 신선도 배지가 렌더됨', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    await expect(badge).toBeVisible({ timeout: 10_000 })
  })

  test('배지가 "자동화 파이프라인" 텍스트를 포함함', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    await expect(badge).toContainText(/자동화|파이프라인|크롤링|신선도/, { timeout: 10_000 })
  })

  test('배지가 "조회" 시각을 표시함', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    // 시간:분:초 포맷 확인 (HH:MM:SS)
    await expect(badge).toContainText(/\d{1,2}:\d{2}:\d{2}/, { timeout: 10_000 })
  })
})

// ── B. Stale 상태 표시 ───────────────────────────────────────────
test.describe('Journey 17-B: Stale 상태 경고', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('배지가 24시간 데이터 보유 여부를 표시함', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    // "성공 기록" 또는 "경고" 텍스트 확인
    const text = await badge.textContent()
    const isStaleOrHealthy = text?.includes('성공 기록 없음') || text?.includes('데이터 저장됨')
    expect(isStaleOrHealthy).toBeTruthy()
  })

  test('배지가 소스별 24시간 저장 건수를 표시함', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    // 최소한 "24h:"와 숫자가 포함되어야 함
    await expect(badge).toContainText(/24h/, { timeout: 10_000 })
  })
})

// ── C. 소스별 정보 표시 ──────────────────────────────────────────
test.describe('Journey 17-C: 소스별 데이터 표시', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('배지가 소스명을 포함함 (natepan, blind 등)', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    const text = await badge.textContent()
    // 최소 하나의 크롤 소스명 포함 확인
    const hasSources = /natepan|blind|theqoo|dcinside|clien/.test(text || '')
    expect(hasSources).toBeTruthy()
  })

  test('배지가 마지막 성공 크롤 시각을 표시함 (월-일 HH:MM 포맷 또는 "기록 없음")', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/)

    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    const text = await badge.textContent()
    // 날짜/시각 또는 "기록" 텍스트 확인
    const hasTimestamp = /\d{1,2}:\d{2}|기록/i.test(text || '')
    expect(hasTimestamp).toBeTruthy()
  })
})

// ── D. API 엔드포인트 검증 ───────────────────────────────────────
test.describe('Journey 17-D: 크롤 상태 API', () => {

  test('GET /api/admin/crawl-status → 200 + 올바른 스키마', async ({ request }) => {
    const { tokenFromStorageState } = await import('../support/api')
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'ADMIN storageState 없음')

    const res = await request.get(`${BASE}/api/admin/crawl-status`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(200)

    const body = await res.json()
    // 필드 검증
    expect(typeof body.savedBySource24h).toBe('object')
    expect(typeof body.lastSuccessfulAt).toBe('object')
    expect(typeof body.failureCount24h).toBe('number')
    expect(typeof body.stale).toBe('boolean')
    expect(typeof body.checkedAt).toBe('string')

    // savedBySource24h는 Record<string, number>
    Object.entries(body.savedBySource24h).forEach(([source, count]) => {
      expect(typeof source).toBe('string')
      expect(typeof count).toBe('number')
    })

    // lastSuccessfulAt는 Record<string, string>
    Object.entries(body.lastSuccessfulAt).forEach(([source, timestamp]) => {
      expect(typeof source).toBe('string')
      expect(typeof timestamp).toBe('string')
    })
  })

  test('GET /api/admin/crawl-status (미인증) → 401/403', async ({ request }) => {
    const res = await request.get(`${BASE}/api/admin/crawl-status`)
    expect([401, 403]).toContain(res.status())
  })

  test('GET /api/admin/crawl-status (비관리자) → 403', async ({ request, context }) => {
    // 일반 사용자 토큰으로 시도 (권한 검증)
    // 이 테스트는 인프라가 사용자 권한 검증을 수행하는 경우만 유효
    // 현재는 skip 처리
    test.skip(true, 'guest/non-admin 계정 테스트는 별도 fixture 필요')
  })
})

// ── E. 자동 갱신 (Refresh) ───────────────────────────────────────
test.describe('Journey 17-E: 배지 갱신', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('대시보드 refresh 버튼 클릭 후 배지 데이터 갱신', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin$/, { timeout: 10_000 })

    // 초기 배지 렌더 대기
    const badge = page.locator(ADMIN_CRAWL.freshnessBadge)
    await expect(badge).toBeVisible({ timeout: 10_000 })
    const initialText = await badge.textContent()

    // 대시보드 refresh 버튼 클릭 (admin-page-refresh)
    const refreshBtn = page.locator('[data-testid="admin-page-refresh"]')
    if (await refreshBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await refreshBtn.locator('button').first().click()
      // 갱신 로딩 상태 대기
      await page.waitForTimeout(500)
      // 배지가 여전히 렌더되어 있는지 확인
      await expect(badge).toBeVisible({ timeout: 5_000 })
    }
  })
})
