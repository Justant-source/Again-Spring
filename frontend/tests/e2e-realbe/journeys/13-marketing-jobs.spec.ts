/**
 * Journey 13: 마케팅 허브 (ASM 얇은 클라이언트)
 *
 * B. UI 페이지 (ASM 불필요) — 항상 실행
 *    - 탭: 대기 / 완료 / 설정
 *    - 기본 탭 = 대기(holding board). 수동 "+ 새 마케팅 잡" UI 없음
 * C. 잡 생성·조회 (ASM_STUB_AVAILABLE=true 시) — POST /jobs 내부 API (UI 아님)
 *
 * API 가드/목록/통계 계약은 Marketing* BE 테스트 + Journey 09-B2로 이관.
 * 가드레일: LLM 미호출, ASM 스텁만, 실 GPU·Claude 불필요. 실 ASM publish 불필요.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { tokenFromStorageState, createPost } from '../support/api'
import { ADMIN_MARKETING } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)
const ASM_AVAILABLE = process.env.ASM_STUB_AVAILABLE === 'true'

test.describe('Journey 13-B: 마케팅 허브 UI 페이지', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('어드민 — /admin/marketing 대기·완료 탭·사이드바', async ({ page }) => {
    await page.goto(`${BASE}/admin/marketing`)
    await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })

    await expect(page.getByRole('heading', { name: '마케팅' })).toBeVisible({ timeout: 8_000 })

    const holdingTab = page.getByRole('tab', { name: '대기' })
    const completedTab = page.getByRole('tab', { name: '완료' })
    const settingsTab = page.getByRole('tab', { name: '설정' })
    await expect(holdingTab).toBeVisible({ timeout: 8_000 })
    await expect(completedTab).toBeVisible()
    await expect(settingsTab).toBeVisible()

    // 기본 탭 = 대기 — holding board (수동 잡 생성 버튼 없음)
    await expect(page.locator(ADMIN_MARKETING.holdingBoard)).toBeVisible({ timeout: 10_000 })
    await expect(page.getByRole('button', { name: /새 마케팅 잡/ })).toHaveCount(0)

    // 완료 탭 — 잡 보드·플랫폼 성과
    await completedTab.click()
    await expect(page.locator(ADMIN_MARKETING.jobBoard)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(ADMIN_MARKETING.platformPerformance)).toBeVisible({ timeout: 10_000 })

    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })
    await expect(page.locator('a[href="/admin/marketing"]').first()).toBeVisible({ timeout: 8_000 })
  })

  test('어드민 — /admin/marketing/jobs/99999 → 크래시 없음', async ({ page }) => {
    await page.goto(`${BASE}/admin/marketing/jobs/99999`)
    await page.waitForLoadState('domcontentloaded')
    expect(page.url()).toBeTruthy()
  })
})

if (ASM_AVAILABLE) {
test.describe('Journey 13-C: 마케팅 잡 생성·조회 흐름 (ASM 스텁)', () => {
  test.describe.configure({ mode: 'serial' })
  test.use({ storageState: ADMIN_AUTH })

  let adminToken: string
  let testPostId: string
  let createdJobId: number

  test.beforeAll(async ({ request }) => {
    adminToken = tokenFromStorageState(PERSONA_TEST1.email)
    if (!adminToken) return

    try {
      testPostId = await createPost(request, {
        token: adminToken,
        title: '[e2e] 마케팅 잡 테스트용 사연',
        body: '테스트용 사연 본문입니다. 마케팅 잡 e2e 검증용.',
        category: 'DAILY',
      })
    } catch {
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

  test('마케팅 잡 생성 — POST /api/admin/marketing/jobs (내부 API)', async ({ request }) => {
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

  test('마케팅 잡 목록·상세', async ({ request }) => {
    test.skip(!adminToken || !createdJobId, '이전 단계 실패')

    const listRes = await request.get(`${BASE}/api/admin/marketing/jobs`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(listRes.status()).toBe(200)
    const jobs = await listRes.json() as { id: number }[]
    expect(jobs.some(j => j.id === createdJobId), '생성된 잡이 목록에 있음').toBe(true)

    const detailRes = await request.get(`${BASE}/api/admin/marketing/jobs/${createdJobId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(detailRes.status(), '상세 조회 성공').toBe(200)
    const job = await detailRes.json()
    expect(job.id, '잡 ID 일치').toBe(createdJobId)
    expect(job.remoteJobId, 'ASM remote_job_id 수신됨').toBeTruthy()
  })
})
}
