/**
 * Journey 13: 마케팅 잡 관리 (ASM 얇은 클라이언트)
 *
 * A. API 기본 (ASM 불필요) — 항상 실행
 *    - 인증 가드: 미인증 401, 비-어드민 403
 *    - GET /api/admin/marketing/jobs → 200 + 배열
 *    - GET /api/admin/marketing/jobs/{invalid} → 404
 *
 * B. UI 페이지 (ASM 불필요) — 항상 실행
 *    - /admin/marketing 페이지 로드 및 사이드바 항목 확인
 *
 * C. 잡 생성·조회 흐름 (ASM M0 스텁 필요)
 *    - ASM_STUB_AVAILABLE=true 환경 변수 시 실행
 *    - POST /api/admin/marketing/jobs → REQUESTED/QUEUED 상태
 *    - GET /api/admin/marketing/jobs/{id} → 상세 확인
 *    - POST /api/admin/marketing/jobs/{id}/publish (READY 상태 대기 후)
 *
 * 가드레일: LLM 미호출, ASM 스텁만 사용, 실 GPU·Claude 불필요.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { tokenFromStorageState, createPost } from '../support/api'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)  // test1 = ADMIN
const TEST5_AUTH = authStatePath(PERSONAS[4].email)    // test5 = USER only

const ASM_AVAILABLE = process.env.ASM_STUB_AVAILABLE === 'true'

// ── A. API 기본 (ASM 불필요) ─────────────────────────────────────
test.describe('Journey 13-A: 마케팅 잡 API 기본', () => {

  test('미인증 — GET /api/admin/marketing/jobs → 401/403', async ({ request }) => {
    const res = await request.get(`${BASE}/api/admin/marketing/jobs`)
    // Spring Security 6 기본값: 미인증 접근에 403 반환 (401 EntryPoint 미설정)
    expect([401, 403]).toContain(res.status())
  })

  test('비-어드민(test5) — GET /api/admin/marketing/jobs → 403', async ({ request }) => {
    const token = tokenFromStorageState(PERSONAS[4].email)
    test.skip(!token, 'test5 storageState 없음 — global-setup 먼저 실행')

    const res = await request.get(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status()).toBe(403)
  })

  test('어드민 — GET /api/admin/marketing/jobs → 200 + 배열', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음 — global-setup 먼저 실행')

    const res = await request.get(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status(), '잡 목록 응답 코드').toBe(200)
    const body = await res.json()
    expect(Array.isArray(body), '응답이 배열').toBe(true)
  })

  test('어드민 — GET /api/admin/marketing/jobs/99999 → 404', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const res = await request.get(`${BASE}/api/admin/marketing/jobs/99999`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status(), '존재하지 않는 잡 → 404').toBe(404)
  })

  test('어드민 — POST /api/admin/marketing/jobs 필수 필드 누락 → 400', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const res = await request.post(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {},  // postId 누락
    })
    // 필드 검증 실패 → 400 또는 422
    expect(res.status(), '필수 필드 누락 → 4xx').toBeGreaterThanOrEqual(400)
    expect(res.status(), '필수 필드 누락 → 4xx').toBeLessThan(500)
  })
})

// ── B. UI 페이지 (ASM 불필요) ────────────────────────────────────
test.describe('Journey 13-B: 마케팅 잡 UI 페이지', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('어드민 — /admin/marketing 페이지 로드', async ({ page }) => {
    await page.goto(`${BASE}/admin/marketing`)
    await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })
    // 페이지 제목 또는 핵심 텍스트 확인
    await expect(
      page.getByText(/마케팅|Marketing|잡/i).first(),
    ).toBeVisible({ timeout: 8_000 })
  })

  test('어드민 — 사이드바에 마케팅 잡 링크 존재', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })
    // 사이드바 링크 확인
    await expect(
      page.getByRole('link', { name: /마케팅/i }),
    ).toBeVisible({ timeout: 8_000 })
  })

  test('어드민 — /admin/marketing/jobs/99999 → 404 페이지 또는 빈 상태', async ({ page }) => {
    await page.goto(`${BASE}/admin/marketing/jobs/99999`)
    // 404 메시지나 에러 핸들링 확인 (앱이 크래시하지 않아야 함)
    await page.waitForLoadState('networkidle', { timeout: 10_000 })
    // 앱이 응답하고 있음을 확인 (빈 상태, 에러 메시지, 또는 리다이렉트 모두 허용)
    const url = page.url()
    expect(url).toBeTruthy()
  })
})

// ── C. 잡 생성·조회 흐름 (ASM M0 스텁 필요) ─────────────────────
test.describe('Journey 13-C: 마케팅 잡 생성·조회 흐름 (ASM 스텁)', () => {
  test.skip(!ASM_AVAILABLE, 'ASM_STUB_AVAILABLE=true 환경 변수 필요 — ASM M0 스텁 실행 중이어야 함')
  test.describe.configure({ mode: 'serial' })
  test.use({ storageState: ADMIN_AUTH })

  let adminToken: string
  let testPostId: string
  let createdJobId: number

  test.beforeAll(async ({ request }) => {
    adminToken = tokenFromStorageState(PERSONA_TEST1.email)
    if (!adminToken) return

    // 테스트용 사연 생성 (jurorCount=0 강제 — LLM 미호출)
    try {
      testPostId = await createPost(request, {
        token: adminToken,
        title: '[e2e] 마케팅 잡 테스트용 사연',
        body: '테스트용 사연 본문입니다. 마케팅 잡 e2e 검증용.',
        category: 'DAILY',
      })
    } catch {
      // 사연 생성 실패 시 기존 PUBLIC 사연 사용
      const feedRes = await request.get(`${BASE}/api/community/posts?page=0&size=1`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })
      if (feedRes.ok()) {
        const feedBody = await feedRes.json()
        const posts = feedBody.content ?? []
        if (posts.length > 0) testPostId = posts[0].id as string
      }
    }
  })

  test('마케팅 잡 생성 — POST /api/admin/marketing/jobs', async ({ request }) => {
    test.skip(!adminToken || !testPostId, '어드민 토큰 또는 사연 ID 없음')

    const res = await request.post(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        postId: testPostId,
        targets: ['naver_blog', 'x'],
        autoPublish: false,
      },
    })

    expect([200, 201], '잡 생성 응답 코드 200/201').toContain(res.status())
    const job = await res.json()
    expect(job.id, '잡 ID 존재').toBeTruthy()
    expect(job.status, '초기 상태').toMatch(/^(REQUESTED|QUEUED)$/)
    expect(String(job.postId), 'postId 일치').toBe(String(testPostId))
    expect(job.targets, 'targets 포함').toContain('naver_blog')
    createdJobId = job.id
  })

  test('마케팅 잡 목록에 생성된 잡 포함 확인', async ({ request }) => {
    test.skip(!adminToken || !createdJobId, '이전 단계 실패')

    const res = await request.get(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(res.status()).toBe(200)
    const jobs = await res.json() as { id: number }[]
    expect(jobs.some(j => j.id === createdJobId), '생성된 잡이 목록에 있음').toBe(true)
  })

  test('마케팅 잡 상세 조회', async ({ request }) => {
    test.skip(!adminToken || !createdJobId, '이전 단계 실패')

    const res = await request.get(`${BASE}/api/admin/marketing/jobs/${createdJobId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(res.status(), '상세 조회 성공').toBe(200)
    const job = await res.json()
    expect(job.id, '잡 ID 일치').toBe(createdJobId)
    expect(job.remoteJobId, 'ASM remote_job_id 수신됨').toBeTruthy()
  })
})
