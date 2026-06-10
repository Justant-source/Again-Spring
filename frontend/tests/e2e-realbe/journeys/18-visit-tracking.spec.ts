/**
 * Journey 18: 방문 추적 (VisitTracker + PublicVisitController)
 *
 * A. utm 파라미터가 있는 공개 페이지 방문 시 /api/public/visits POST 발생
 * B. 같은 path·세션에서 재방문 시 중복 전송 없음
 * C. /admin 경로는 추적 안 됨
 * D. API 직접 검증 — 유효·무효 요청
 *
 * LLM 가드레일: 방문 기록 API는 DB 쓰기 — LLM 미호출.
 */
import { test, expect } from '../support/no-llm-fixture'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── A. UTM 파라미터 방문 추적 ────────────────────────────────────
test.describe('Journey 18-A: UTM 방문 추적', () => {

  test('utm 파라미터가 있는 공개 페이지 방문 시 방문 이벤트 전송', async ({ page }) => {
    const requests: string[] = []

    page.on('request', req => {
      if (req.url().includes('/api/public/visits')) {
        requests.push(req.method())
      }
    })

    // utm 파라미터와 함께 공개 페이지 방문
    await page.goto(`${BASE}/?utm_source=instagram&utm_medium=social&utm_campaign=asm-job-1`)
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})

    // 방문 이벤트 전송 확인 (최대 3초 대기)
    await page.waitForTimeout(2000)
    expect(requests.filter(m => m === 'POST').length).toBeGreaterThanOrEqual(1)
  })

  test('utm 없는 내부 이동은 방문 이벤트 미전송', async ({ page }) => {
    const requests: string[] = []

    page.on('request', req => {
      if (req.url().includes('/api/public/visits')) {
        requests.push(req.method())
      }
    })

    // utm 없이 공개 페이지 방문
    await page.goto(`${BASE}/`)
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})
    await page.waitForTimeout(1500)

    // 방문 이벤트 미전송 (utm 없으니 조건 불충족)
    expect(requests.length).toBe(0)
  })
})

// ── B. /admin 경로 제외 ──────────────────────────────────────────
test.describe('Journey 18-B: 어드민 경로 추적 제외', () => {

  test('/admin 경로는 방문 이벤트 미전송', async ({ page }) => {
    const requests: string[] = []

    page.on('request', req => {
      if (req.url().includes('/api/public/visits')) {
        requests.push(req.method())
      }
    })

    // utm이 있어도 admin 경로이면 추적 안 됨
    await page.goto(`${BASE}/admin?utm_source=test`)
    await page.waitForTimeout(2000)

    expect(requests.length).toBe(0)
  })
})

// ── C. API 직접 검증 ─────────────────────────────────────────────
test.describe('Journey 18-C: PublicVisitController API', () => {

  test('유효한 방문 이벤트 → 200', async ({ request }) => {
    const res = await request.post(`${BASE}/api/public/visits`, {
      data: {
        path: '/community',
        utmSource: 'instagram',
        utmMedium: 'social',
        utmCampaign: 'test-campaign',
        sessionKey: 'test-session-' + Date.now(),
      },
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.status).toBe('recorded')
  })

  test('/admin 경로 → 400', async ({ request }) => {
    const res = await request.post(`${BASE}/api/public/visits`, {
      data: {
        path: '/admin/dashboard',
        sessionKey: 'test-session',
      },
    })
    expect(res.status()).toBe(400)
  })

  test('path가 /로 시작하지 않는 경우 → 400', async ({ request }) => {
    const res = await request.post(`${BASE}/api/public/visits`, {
      data: {
        path: 'invalid-path',
        sessionKey: 'test-session',
      },
    })
    expect(res.status()).toBe(400)
  })

  test('인증 없이 접근 가능 (permitAll) → 200 or 400', async ({ request }) => {
    // 인증 없이도 API가 작동함을 확인 (401/403이 아님)
    const res = await request.post(`${BASE}/api/public/visits`, {
      data: {
        path: '/community',
        sessionKey: 'permit-all-test',
      },
    })
    // 인증 없이도 컨트롤러에 도달함 — 401/403이 아님을 확인 (429=rate limit도 허용)
    expect([200, 400, 429]).toContain(res.status())
  })
})
