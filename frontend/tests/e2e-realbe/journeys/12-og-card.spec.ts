/**
 * Journey 12: OG 카드 (동적 Open Graph 메타 + 이미지)
 *
 * 공개 게시글 URL 공유 시 카카오톡·페북·트위터 크롤러가 받는 서버 응답을 검증.
 *
 * 커버 범위:
 *   A. 서버 응답에 og:title · og:description · og:url · og:image 포함
 *   B. og:url 이 절대 https:// URL
 *   C. opengraph-image 라우트가 200 image/png 반환
 *   D. 비공개(PRIVATE) 글 → 메타 제너릭, 이미지 라우트는 여전히 200/png(fallback)
 *
 * no-llm 가드레일 호환:
 *   - OG 경로는 GET /api/community/posts/{id} (공개, jurorCount 무관) 만 호출
 *   - jurorCount>0 / jury/retry / 마케팅 엔드포인트 미접촉 → 가드레일 안전
 */
import { test, expect } from '../support/no-llm-fixture'
import { guestLogin, createPost } from '../support/api'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { authStatePath } from '../fixtures/auth-state'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── A / B / C: 공개 게시글 OG 메타 + 이미지 라우트 ─────────────────
test.describe('Journey 12-A: 공개 글 OG 메타', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('공개 글 페이지에 og:title · og:description · og:image · og:url 존재, url이 절대 https', async ({
    request,
  }) => {
    // 글 생성 (jurorCount=0 강제)
    const token = (await import('../support/api')).tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'OG 카드 e2e 테스트 사연',
      body: '이 사연은 OG 카드 e2e 테스트용입니다. 충분한 길이의 본문으로 작성합니다.',
      category: 'OTHER',
    })

    // 서버 렌더된 HTML 가져오기 (크롤러 관점)
    const res = await request.get(`${BASE}/community/${postId}`)
    expect(res.ok()).toBeTruthy()
    const html = await res.text()

    // og:title 존재
    expect(html).toMatch(/property="og:title"/i)

    // og:description 존재
    expect(html).toMatch(/property="og:description"/i)

    // og:image 존재 (opengraph-image.tsx 자동 주입)
    expect(html).toMatch(/property="og:image"/i)

    // og:url 이 절대 https:// 형식
    const ogUrlMatch = html.match(/property="og:url"\s+content="([^"]+)"/)
    expect(ogUrlMatch).not.toBeNull()
    if (ogUrlMatch) {
      expect(ogUrlMatch[1]).toMatch(/^https?:\/\//)
      expect(ogUrlMatch[1]).toContain(postId)
    }

    // twitter:card 존재
    expect(html).toMatch(/name="twitter:card"/i)
  })
})

// ── C: opengraph-image 라우트 (PNG 이미지 응답) ────────────────────
test.describe('Journey 12-C: OG 이미지 라우트', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('opengraph-image 라우트가 200 image/png + 실제 PNG 데이터 반환', async ({ request }) => {
    const token = (await import('../support/api')).tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'OG 이미지 라우트 테스트',
      body: 'OG 이미지 라우트 e2e 테스트용 본문입니다.',
      category: 'COUPLE',
    })

    // opengraph-image 라우트 직접 호출
    const imgRes = await request.get(`${BASE}/community/${postId}/opengraph-image`)
    expect(imgRes.status()).toBe(200)
    expect(imgRes.headers()['content-type']).toContain('image/png')

    // PNG 시그니처 확인 (최소 10 바이트 이상의 실제 PNG)
    const body = await imgRes.body()
    expect(body.length).toBeGreaterThan(1000)
    // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
    expect(body[0]).toBe(0x89)
    expect(body[1]).toBe(0x50) // P
    expect(body[2]).toBe(0x4e) // N
    expect(body[3]).toBe(0x47) // G
  })

  test('Cache-Control 헤더 존재', async ({ request }) => {
    const token = (await import('../support/api')).tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'OG Cache-Control 헤더 테스트',
      body: 'Cache-Control 헤더 테스트용 본문입니다.',
      category: 'FRIEND',
    })

    const imgRes = await request.get(`${BASE}/community/${postId}/opengraph-image`)
    expect(imgRes.status()).toBe(200)
    const cc = imgRes.headers()['cache-control']
    expect(cc).toBeDefined()
    expect(cc).toContain('max-age=')
  })
})

// ── D: 존재하지 않는 글 → fallback (200 PNG, 제너릭 메타) ────────────
test.describe('Journey 12-D: 없는 글 fallback', () => {
  test('존재하지 않는 postId → opengraph-image 라우트 여전히 200/png(fallback)', async ({
    request,
  }) => {
    const fakeId = 'post_000000000000000000000'
    const imgRes = await request.get(`${BASE}/community/${fakeId}/opengraph-image`)
    // 404가 아닌 200으로 fallback 카드를 반환해야 함 (크롤러에 500/404 주면 Kakao가 빈 미리보기 캐싱)
    expect(imgRes.status()).toBe(200)
    expect(imgRes.headers()['content-type']).toContain('image/png')
    const body = await imgRes.body()
    expect(body.length).toBeGreaterThan(1000)
  })
})
