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
import { sql } from '../support/db'
import {
  ADMIN_MARKETING,
  holdingRow,
  holdingPinBtn,
  holdingUnpinBtn,
  holdingPinFormatSelect,
  completedDroppedRow,
  completedPublishedRow,
  completedForceModeSelect,
  completedForceExecuteBtn,
  completedPublicationJobLink,
} from '../support/selectors'

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

    // 완료 탭 재설계 — published/dropped 보드로 대체, 잡보드·플랫폼성과·타임라인 제거.
    // (아래 completed* testid는 assumption — HoldingBoard 담당 에이전트와 미합의, selectors.ts 참조)
    await completedTab.click()
    await expect(page.locator(ADMIN_MARKETING.completedPublishedBoard), '완료 탭 — 게시 보드(assumption testid)').toBeVisible({ timeout: 10_000 })
    await expect(page.locator(ADMIN_MARKETING.completedDroppedBoard), '완료 탭 — 탈락 보드(assumption testid)').toBeVisible({ timeout: 10_000 })
    await expect(page.locator(ADMIN_MARKETING.jobBoard), '완료 탭에 구 잡보드 없음').toHaveCount(0)
    await expect(page.locator(ADMIN_MARKETING.platformPerformance), '완료 탭에 플랫폼 성과 없음').toHaveCount(0)
    await expect(page.locator(ADMIN_MARKETING.timeline), '완료 탭에 구 타임라인 없음').toHaveCount(0)

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

// ── Journey 13-F: 대기 탭 — 핀 인라인 셀렉트 (window.prompt 제거) ──────
// ASM 불필요 — pin/unpin은 marketing_holding 직접 조작(BE 내부 API), ASM 미의존.
// 핀: 「핀」클릭 → 인라인 Select(영상|글) → 선택 즉시 onPin (confirm 버튼 없음).
test.describe('Journey 13-F: 대기 탭 — 핀 인라인 셀렉트 (window.prompt 제거)', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('핀 — VIDEO/TEXT 인라인 선택으로 확정, window.prompt/confirm 미사용', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const postId = await createPost(request, {
      token,
      title: 'E2E 핀 인라인 셀렉트 시드 사연',
      body: 'e2e marketing pin inline-select seed post body — cleanup targets this author.',
    })

    sql(`
      INSERT INTO marketing_holding (post_id, status, score_snapshot, rank_snapshot, created_at, updated_at)
      VALUES ('${postId}', 'IN_POOL', 10.0, 1, NOW(), NOW());
    `)

    const dialogMessages: string[] = []
    page.on('dialog', (dialog) => {
      dialogMessages.push(dialog.message())
      dialog.dismiss().catch(() => {})
    })

    try {
      await page.goto(`${BASE}/admin/marketing`)
      await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })
      await expect(page.locator(ADMIN_MARKETING.holdingBoard)).toBeVisible({ timeout: 10_000 })

      const row = page.locator(holdingRow(postId))
      await expect(row, '대기 보드에 시드 행 노출').toBeVisible({ timeout: 10_000 })

      await page.locator(holdingPinBtn(postId)).click()

      const formatSelect = page.locator(holdingPinFormatSelect(postId))
      await expect(formatSelect, 'PIN 인라인 포맷 셀렉트 노출').toBeVisible({ timeout: 8_000 })

      await formatSelect.click()
      await page.getByRole('option', { name: /영상/ }).click()

      // 선택 즉시 PINNED — confirm 버튼 없음. 「핀 해제」로 검증.
      await expect(
        page.locator(holdingUnpinBtn(postId)),
        '핀 확정 후 핀 해제 버튼 노출(PINNED 전환)',
      ).toBeVisible({ timeout: 8_000 })

      expect(dialogMessages, '핀 흐름에서 window.prompt/confirm이 호출되면 안 됨').toEqual([])
    } finally {
      sql(`DELETE FROM marketing_holding WHERE post_id='${postId}'`)
    }
  })
})

// ── Journey 13-G: 완료 탭 — 강제 배포 모드 셀렉트 (window.prompt 제거) ──
// 모드 셀렉트 + 「강제 배포」실행 버튼이 행에 상시 노출. window.prompt/confirm 없음.
test.describe('Journey 13-G: 완료 탭 — 강제 배포 모드 셀렉트 (window.prompt 제거)', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('강제 배포 — 모드 셀렉트 + 실행, window.prompt/confirm 미사용', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const postId = await createPost(request, {
      token,
      title: 'E2E 강제 배포 모드 셀렉트 시드 사연',
      body: 'e2e marketing force-deploy mode-select seed post body — cleanup targets this author.',
    })

    sql(`
      INSERT INTO marketing_holding (post_id, status, score_snapshot, rank_snapshot, locked_at, created_at, updated_at)
      VALUES ('${postId}', 'DROPPED', 5.0, 99, NOW(), NOW(), NOW());
    `)

    const dialogMessages: string[] = []
    page.on('dialog', (dialog) => {
      dialogMessages.push(dialog.message())
      dialog.dismiss().catch(() => {})
    })

    // 실 ASM 강제 배포는 이 스펙 범위 밖 — API만 stub하고 UI/다이얼로그 불변식 검증.
    await page.route(`**/api/admin/marketing/completed/${postId}/force`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          postId,
          status: 'COMMITTED',
          format: 'TEXT',
          jobIds: [],
          targets: [],
        }),
      })
    })

    try {
      await page.goto(`${BASE}/admin/marketing?tab=completed`)
      await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })
      await expect(
        page.locator(ADMIN_MARKETING.completedDroppedBoard),
        '완료 탭 — 탈락 보드',
      ).toBeVisible({ timeout: 10_000 })

      const row = page.locator(completedDroppedRow(postId))
      await expect(row, '탈락 보드에 시드 행 노출').toBeVisible({ timeout: 10_000 })

      const modeSelect = row.locator(completedForceModeSelect(postId))
      await expect(modeSelect, '강제 배포 모드 셀렉트 노출').toBeVisible({ timeout: 8_000 })
      await modeSelect.selectOption('TEXT_ONLY')

      const executeBtn = row.locator(completedForceExecuteBtn(postId))
      await executeBtn.click()

      expect(dialogMessages, '강제 배포 흐름에서 window.prompt/confirm이 호출되면 안 됨').toEqual([])
    } finally {
      sql(`DELETE FROM marketing_holding WHERE post_id='${postId}'`)
    }
  })
})

// ── Journey 13-H: 완료 탭 — 게시 상세 다이얼로그 ───────────────────────
// ⚠️ FE 미병합 시점 작성: completedPublicationDialog 오픈 트리거는 unknown — 행 클릭으로 가정.
// 실제 트리거가 별도 버튼이면 이 테스트의 클릭 대상만 교체하면 됨(testid는 selectors.ts 그대로 사용).
test.describe('Journey 13-H: 완료 탭 — 게시 상세 다이얼로그', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('확정(게시) 보드 항목 클릭 → 게시 상세 다이얼로그 노출', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const postId = await createPost(request, {
      token,
      title: 'E2E 게시 상세 다이얼로그 시드 사연',
      body: 'e2e marketing publication dialog seed post body — cleanup targets this author.',
    })

    const remoteJobId = `e2e-pub-dialog-${Date.now()}`
    sql(`
      INSERT INTO marketing_job (
        remote_job_id, post_id, status, phase, progress, targets, auto_publish,
        requested_by, poll_fail_count, scheduled_publish_at, created_at, updated_at, idempotency_key
      ) VALUES (
        '${remoteJobId}', '${postId}', 'RUNNING', 'RENDER', 0.4,
        '["youtube_shorts"]', 0, 'e2epersona01', 0, NOW(), NOW(), NOW(), '${remoteJobId}'
      );
    `)
    sql(`
      INSERT INTO marketing_holding (post_id, status, score_snapshot, rank_snapshot, locked_at, created_at, updated_at)
      VALUES ('${postId}', 'COMMITTED', 20.0, 1, NOW(), NOW(), NOW());
    `)
    const jobId = sql(
      `SELECT id FROM marketing_job WHERE remote_job_id='${remoteJobId}' LIMIT 1`,
    )

    try {
      await page.goto(`${BASE}/admin/marketing?tab=completed`)
      await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })
      await expect(
        page.locator(ADMIN_MARKETING.completedPublishedBoard),
        '완료 탭 — 게시 보드(assumption testid)',
      ).toBeVisible({ timeout: 10_000 })

      const row = page.locator(completedPublishedRow(postId))
      await expect(row, '게시 보드에 시드 행 노출').toBeVisible({ timeout: 10_000 })
      await row.click()

      await expect(
        page.locator(ADMIN_MARKETING.completedPublicationDialog),
        `게시 상세 다이얼로그 노출 필요 (assumption testid: ${ADMIN_MARKETING.completedPublicationDialog})`,
      ).toBeVisible({ timeout: 8_000 })

      const jobLink = page.locator(completedPublicationJobLink(jobId))
      await expect(jobLink, '잡 상세 링크').toBeVisible({ timeout: 8_000 })
      await expect(jobLink).toHaveAttribute('href', `/admin/marketing/jobs/${jobId}`)
      await jobLink.click()
      await page.waitForURL(new RegExp(`/admin/marketing/jobs/${jobId}`), {
        timeout: 10_000,
      })
    } finally {
      sql(`DELETE FROM marketing_job WHERE remote_job_id='${remoteJobId}'`)
      sql(`DELETE FROM marketing_holding WHERE post_id='${postId}'`)
    }
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
        targets: ['x_thread'],
        autoPublish: false,
      },
    })

    expect([200, 201], '잡 생성 응답 코드 200/201').toContain(res.status())
    const job = await res.json()
    expect(job.id, '잡 ID 존재').toBeTruthy()
    expect(job.status, '초기 상태').toMatch(/^(REQUESTED|QUEUED)$/)
    expect(String(job.postId), 'postId 일치').toBe(String(testPostId))
    expect(job.targets, 'targets 포함').toContain('x_thread')
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
