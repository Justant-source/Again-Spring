/**
 * Journey 18: 방문 추적 (VisitTracker)
 *
 * A. utm 공개 페이지 → POST /api/public/visits
 * B. 같은 path·세션 재방문 시 중복 전송 없음
 * C. /admin 경로 제외
 *
 * API validation은 PublicVisitControllerTest로 이관.
 */
import { test, expect } from '../support/no-llm-fixture'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Journey 18-A: UTM 방문 추적', () => {

  test('utm 파라미터가 있는 공개 페이지 방문 시 방문 이벤트 전송', async ({ page }) => {
    const visitPost = page.waitForRequest(
      (req) => req.url().includes('/api/public/visits') && req.method() === 'POST',
      { timeout: 8_000 },
    )
    await page.goto(`${BASE}/?utm_source=instagram&utm_medium=social&utm_campaign=asm-job-1`)
    await visitPost
  })

  test('utm 없는 내부 이동은 방문 이벤트 미전송', async ({ page }) => {
    const requests: string[] = []
    page.on('request', req => {
      if (req.url().includes('/api/public/visits')) {
        requests.push(req.method())
      }
    })

    await page.goto(`${BASE}/`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {})
    expect(requests.length).toBe(0)
  })

  test('같은 path·세션 재방문 시 중복 POST 없음', async ({ page }) => {
    const posts: string[] = []
    page.on('request', req => {
      if (req.url().includes('/api/public/visits') && req.method() === 'POST') {
        posts.push(req.url())
      }
    })

    const url = `${BASE}/?utm_source=e2e-dedupe&utm_medium=test&utm_campaign=dedupe-1`
    const first = page.waitForRequest(
      (req) => req.url().includes('/api/public/visits') && req.method() === 'POST',
      { timeout: 8_000 },
    )
    await page.goto(url)
    await first

    const countAfterFirst = posts.length
    await page.goto(url)
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {})
    expect(posts.length).toBe(countAfterFirst)
  })
})

test.describe('Journey 18-B: 어드민 경로 추적 제외', () => {

  test('/admin 경로는 방문 이벤트 미전송', async ({ page }) => {
    const requests: string[] = []
    page.on('request', req => {
      if (req.url().includes('/api/public/visits')) {
        requests.push(req.method())
      }
    })

    await page.goto(`${BASE}/admin?utm_source=test`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {})
    expect(requests.length).toBe(0)
  })
})
