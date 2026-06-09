/**
 * Phase V — 마케팅 잡 e2e (ASM M0 스텁 대상)
 *
 * 전제: ASM M0 스텁이 http://100.115.252.61:8200 에서 실행 중이어야 한다.
 * 스텁을 기다리지 않는 환경(CI, ASM 다운)에서는 @skip 처리.
 *
 * 가드레일: LLM 미호출, ASM 스텁만 사용. 실 GPU/Claude 불필요.
 */
import { test, expect } from '../support/no-llm-fixture'

const SKIP = process.env.ASM_STUB_AVAILABLE !== 'true'

// ASM 스텁 없이 실행 시 모든 테스트를 건너뜀
test.describe.configure({ mode: 'serial' })

test.describe('마케팅 잡 생성·폴링·게시 흐름 (ASM M0 스텁)', () => {
  test.skip(SKIP, 'ASM_STUB_AVAILABLE=true 환경 변수 필요 — ASM M0 스텁이 실행 중이어야 함')

  let adminToken: string
  let createdPostId: string

  test.beforeAll(async ({ request }) => {
    // 어드민 로그인
    const loginRes = await request.post(`${process.env.E2E_BASE_URL ?? 'http://localhost:8090'}/api/auth/login`, {
      data: { email: process.env.E2E_ADMIN_EMAIL ?? 'againspring2026@gmail.com', password: process.env.E2E_ADMIN_PASSWORD ?? '' },
    })
    if (!loginRes.ok()) {
      test.skip(true, '어드민 로그인 실패 — E2E_ADMIN_PASSWORD 필요')
      return
    }
    const body = await loginRes.json()
    adminToken = body.accessToken ?? body.token

    // 테스트용 사연 조회 (첫 번째 PUBLIC 사연 사용)
    const feedRes = await request.get(`${process.env.E2E_BASE_URL ?? 'http://localhost:8090'}/api/community/posts?page=0&size=1&status=PUBLIC`)
    if (feedRes.ok()) {
      const feedBody = await feedRes.json()
      const posts = feedBody.content ?? feedBody.posts ?? []
      if (posts.length > 0) {
        createdPostId = posts[0].id ?? posts[0].postId
      }
    }
  })

  test('마케팅 잡 생성 — POST /api/admin/marketing/jobs', async ({ request }) => {
    if (!adminToken || !createdPostId) {
      test.skip(true, '어드민 토큰 또는 사연 ID 없음')
      return
    }

    const res = await request.post(
      `${process.env.E2E_BASE_URL ?? 'http://localhost:8090'}/api/admin/marketing/jobs`,
      {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: {
          postId: createdPostId,
          targets: ['naver_blog', 'x'],
          autoPublish: false,
        },
      },
    )

    expect(res.status(), '잡 생성 응답 코드').toBe(200)
    const job = await res.json()
    expect(job.id, '잡 ID 존재').toBeTruthy()
    expect(job.status, '초기 상태는 REQUESTED 또는 QUEUED').toMatch(/^(REQUESTED|QUEUED)$/)
    expect(job.postId.toString(), '요청한 postId 일치').toBe(createdPostId.toString())

    // 다음 테스트에서 사용할 수 있도록 전역 저장
    process.env._TEST_MARKETING_JOB_ID = job.id.toString()
  })

  test('마케팅 잡 목록 조회 — GET /api/admin/marketing/jobs', async ({ request }) => {
    if (!adminToken) {
      test.skip(true, '어드민 토큰 없음')
      return
    }

    const res = await request.get(
      `${process.env.E2E_BASE_URL ?? 'http://localhost:8090'}/api/admin/marketing/jobs`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    )

    expect(res.status(), '목록 조회 성공').toBe(200)
    const jobs = await res.json()
    expect(Array.isArray(jobs), '응답이 배열').toBe(true)
  })

  test('마케팅 잡 상세 조회 — GET /api/admin/marketing/jobs/{id}', async ({ request }) => {
    const jobId = process.env._TEST_MARKETING_JOB_ID
    if (!adminToken || !jobId) {
      test.skip(true, '어드민 토큰 또는 잡 ID 없음 — 이전 테스트 먼저 실행 필요')
      return
    }

    const res = await request.get(
      `${process.env.E2E_BASE_URL ?? 'http://localhost:8090'}/api/admin/marketing/jobs/${jobId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    )

    expect(res.status(), '상세 조회 성공').toBe(200)
    const job = await res.json()
    expect(job.id.toString(), '잡 ID 일치').toBe(jobId)
  })
})
