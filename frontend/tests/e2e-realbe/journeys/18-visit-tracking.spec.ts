/**
 * Journey 18: 방문 추적 (VisitTracker)
 *
 * A. utm 공개 페이지 → POST /api/public/visits
 * B. 같은 path·세션 재방문 시 중복 전송 없음
 * C. /admin 경로 제외
 * D. UTM 없는 일반 페이지뷰도 기록된다 (2026-08-29 개편 — 이전엔 UTM/외부 referrer가
 *    있을 때만 기록해 전체 방문량을 알 수 없었다)
 * E. POST 본문이 camelCase 필드명을 쓴다 (회귀 방지 — 2026-08-29까지 프런트가
 *    snake_case로 보내 utm_source/session_key가 100% 유실됐던 인시던트)
 * F. UTM 방문이 visit_events에 utm_source·session_key·visitor_key가 NULL 아닌 채로 저장된다
 * G. as_utm 쿠키는 first-touch — 이후 다른 UTM으로 재방문해도 덮어쓰지 않는다
 *
 * API validation·필드명 계약(Jackson)은 PublicVisitControllerTest(BE)로 이관.
 */
import { test, expect } from '../support/no-llm-fixture'
import { sql } from '../support/db'

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

  test('utm 없는 일반 페이지뷰도 방문 이벤트를 전송한다 (2026-08-29 개편)', async ({ page }) => {
    // 개편 전에는 UTM/외부 referrer가 있을 때만 기록해 "사이트에 몇 명이 왔나"라는
    // 가장 기본적인 질문에 답할 수 없었다. 지금은 /admin을 제외한 모든 페이지뷰를
    // 남기고 봇 여부는 서버가 User-Agent로 판정한다(is_bot 플래그, 행은 보존).
    const visitPost = page.waitForResponse(
      (res) => res.url().includes('/api/public/visits') && res.request().method() === 'POST',
      { timeout: 8_000 },
    )
    await page.goto(`${BASE}/`)
    const res = await visitPost
    expect(res.status()).toBe(200)

    const body = res.request().postDataJSON() as Record<string, unknown>
    expect(body.path).toBeTruthy()
    expect(body.utmSource).toBeUndefined()
    expect(body.utmMedium).toBeUndefined()
    expect(body.utmCampaign).toBeUndefined()
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

  // 검증하는 불변식은 "요청이 0건"이 아니라 "/admin 경로가 기록되지 않는다"이다.
  // 비로그인 상태로 /admin에 가면 클라이언트가 /login으로 보내고, 거기서 방문이
  // 기록되는 것은 정상이다(2026-08-29 개편으로 모든 페이지뷰를 기록한다).
  // 이전 버전은 "요청 0건"을 단언해, 리다이렉트된 정상 페이지뷰까지 실패로 잡았다.
  test('/admin 경로는 방문 이벤트로 기록되지 않는다', async ({ page }) => {
    const recordedPaths: string[] = []
    page.on('request', req => {
      if (req.url().includes('/api/public/visits') && req.method() === 'POST') {
        const body = req.postDataJSON() as { path?: string } | null
        if (body?.path) recordedPaths.push(body.path)
      }
    })

    await page.goto(`${BASE}/admin?utm_source=test`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {})

    expect(recordedPaths.filter(p => p.startsWith('/admin'))).toEqual([])
  })
})

test.describe('Journey 18-C: 요청 본문 필드명 계약 (camelCase 회귀 방지)', () => {

  test('UTM 방문 POST 본문은 camelCase 필드명을 담는다 (snake_case 아님)', async ({ page }) => {
    // 2026-08-29까지 frontend/lib/api/visits.ts가 utm_source/session_key(snake_case)로
    // 보내는 바람에 BE VisitRequest(camelCase)가 조용히 버렸다. 이 테스트는 실제 네트워크
    // 요청 본문을 가로채 카멜케이스 키가 실제로 오가는지 직접 확인한다.
    const campaign = `e2e-camelcase-${Date.now()}`
    const visitResponse = page.waitForResponse(
      (res) => res.url().includes('/api/public/visits') && res.request().method() === 'POST',
      { timeout: 8_000 },
    )
    await page.goto(
      `${BASE}/?utm_source=e2e-source&utm_medium=e2e-medium&utm_campaign=${campaign}&utm_content=e2e-content`,
    )
    const res = await visitResponse
    expect(res.status()).toBe(200)

    const body = res.request().postDataJSON() as Record<string, unknown>

    expect(body.utmSource).toBe('e2e-source')
    expect(body.utmMedium).toBe('e2e-medium')
    expect(body.utmCampaign).toBe(campaign)
    expect(body.utmContent).toBe('e2e-content')
    expect(typeof body.sessionKey).toBe('string')
    expect(typeof body.visitorKey).toBe('string')

    // 회귀 방지: 과거 버그였던 snake_case 키가 섞여 있지 않아야 한다
    expect(body).not.toHaveProperty('utm_source')
    expect(body).not.toHaveProperty('utm_medium')
    expect(body).not.toHaveProperty('utm_campaign')
    expect(body).not.toHaveProperty('session_key')
    expect(body).not.toHaveProperty('visitor_key')
  })
})

test.describe('Journey 18-D: DB 기록 — UTM·세션·방문자 키가 NULL이 아니다', () => {

  test('UTM 방문이 visit_events에 utm_source·session_key·visitor_key가 채워진 채 저장된다', async ({ page, context }) => {
    const campaign = `e2e-dbcheck-${Date.now()}`
    const visitResponse = page.waitForResponse(
      (res) => res.url().includes('/api/public/visits') && res.request().method() === 'POST',
      { timeout: 8_000 },
    )
    await page.goto(
      `${BASE}/?utm_source=e2e-db-source&utm_medium=e2e-db-medium&utm_campaign=${campaign}`,
    )
    const res = await visitResponse
    expect(res.status()).toBe(200) // 200 응답을 받은 시점엔 이미 BE가 동기 저장을 마쳤다

    const cookies = await context.cookies()
    const visitorKeyCookie = cookies.find((c) => c.name === 'as_vid')
    expect(visitorKeyCookie).toBeTruthy()
    const visitorKey = decodeURIComponent(visitorKeyCookie!.value)
    // SQL 인젝션 방지: 쿠키 값이 randomKey() 형식(영숫자)인지 먼저 검증
    expect(visitorKey).toMatch(/^[a-zA-Z0-9]+$/)

    const where = `visitor_key='${visitorKey}' AND utm_campaign='${campaign}' ORDER BY occurred_at DESC LIMIT 1`

    const utmSource = sql(`SELECT utm_source FROM visit_events WHERE ${where}`)
    const sessionKeyNotNull = sql(`SELECT session_key IS NOT NULL FROM visit_events WHERE ${where}`)
    const visitorKeyNotNull = sql(`SELECT visitor_key IS NOT NULL FROM visit_events WHERE ${where}`)

    expect(utmSource).toBe('e2e-db-source')
    expect(sessionKeyNotNull).toBe('1')
    expect(visitorKeyNotNull).toBe('1')
  })
})

test.describe('Journey 18-E: as_utm 쿠키 first-touch', () => {

  test('as_utm 쿠키는 최초 유입 값을 유지하고 이후 다른 UTM으로 덮어써지지 않는다', async ({ page, context }) => {
    // first-touch 정책: 마지막 클릭이 아니라 "처음 데려온 채널"을 가입에 귀속시킨다.
    // 방문 POST 응답을 기다리지 않고 검증 대상인 쿠키 자체를 폴링한다.
    // waitForResponse는 goto() 도중 POST가 이미 끝나버리면 다음 응답을 영원히
    // 기다리다 타임아웃난다(실제로 그렇게 깨졌다). 쿠키는 최종 상태라 경합이 없다.
    const readUtmCookie = async () => {
      const cookie = (await context.cookies()).find((c) => c.name === 'as_utm')
      return cookie ? JSON.parse(decodeURIComponent(cookie.value)) : null
    }

    await page.goto(`${BASE}/?utm_source=first-touch-a&utm_medium=email&utm_campaign=camp-a`)
    await page.waitForLoadState('domcontentloaded')

    await expect.poll(readUtmCookie, { timeout: 10_000 }).toMatchObject({
      source: 'first-touch-a',
      campaign: 'camp-a',
    })

    // 다른 경로 + 다른 UTM으로 재진입 — first-touch가 유지되어야 한다
    await page.goto(`${BASE}/community?utm_source=second-touch-b&utm_medium=push&utm_campaign=camp-b`)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {})

    // 덮어쓰기가 일어난다면 이 시점에 이미 바뀌어 있다 — poll이 아니라 즉시 단언한다.
    expect(await readUtmCookie()).toMatchObject({
      source: 'first-touch-a',
      campaign: 'camp-a',
    })
  })
})
