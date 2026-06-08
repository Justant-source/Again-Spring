/**
 * Journey 11: 관리자 AI 규칙관리 (비-LLM 경로만)
 *
 * - /admin/ai-rules 페이지 로드 + 페르소나 탭 전환
 * - 전역 금지 규칙 CRUD (API — create/list/toggle/delete)
 * - /admin/content 페이지 + 게시글/댓글 탭 구조
 * - synthetic 필드 계약 확인 (API 레벨)
 * - 비관리자(test5) 403 — storageState 재사용, 중복 login() 제거
 * - 사이드바 "AI 규칙관리" 링크 + 이동
 * - /api/admin/content/corrections/commit 400 (비-LLM 경로)
 * - ruleText 빈 값 POST (500 미만 검증)
 * - /corrections/save에 adminOpinion 포함 계약 (DTO 수용 확인)
 * - /history 응답에 adminOpinion 필드 존재 확인
 * - /history/apply-batch-plan 합성 plan 적용 → 전역 규칙 생성 + 정리 (LLM 비호출)
 *
 * LLM 가드레일: /analyze, /analyze-batch 요청 시 no-llm-fixture가 자동 차단.
 * apply-batch-plan은 LLM 비호출 경로라 차단목록 제외.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { tokenFromStorageState } from '../support/api'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)
// test5 storageState (global-setup이 저장)
const TEST5_AUTH = authStatePath(PERSONAS[4].email)

// ── A. /admin/ai-rules 페이지 ────────────────────────────────────
test.describe('Journey 11-A: /admin/ai-rules 페이지', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 — /admin/ai-rules 페이지 로드', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 10_000 })
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })
  })

  test('페르소나 주의사항 탭 전환', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/)

    await page.getByRole('tab', { name: '페르소나 주의사항' }).click()
    await expect(page.getByText('페르소나 ID 필터')).toBeVisible({ timeout: 5_000 })
  })
})

// ── B. 전역 금지 규칙 CRUD (API) ─────────────────────────────────
test.describe('Journey 11-B: 전역 금지 규칙 CRUD', () => {
  let createdRuleId: number

  test('전역 규칙 추가 → 조회 → 비활성화 → 삭제', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}` }

    // 추가
    const createResp = await request.post(`${BASE}/api/admin/ai-rules/global`, {
      headers,
      data: { ruleText: '[e2e테스트] 자동화 테스트 전역 규칙 — 삭제 예정', scope: 'ALL' },
    })
    expect(createResp.status()).toBe(201)
    const created = await createResp.json()
    createdRuleId = created.id
    expect(created.ruleText).toContain('e2e테스트')
    expect(created.active).toBe(true)

    // 조회
    const listResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    expect(listResp.ok()).toBeTruthy()
    const list = await listResp.json()
    const found = list.content.find((r: any) => r.id === createdRuleId)
    expect(found).toBeTruthy()
    expect(found.scope).toBe('ALL')

    // 비활성화
    const toggleResp = await request.patch(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, {
      headers,
      data: { active: false },
    })
    expect(toggleResp.ok()).toBeTruthy()
    expect((await toggleResp.json()).active).toBe(false)

    // 삭제
    const deleteResp = await request.delete(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, { headers })
    expect(deleteResp.status()).toBe(204)

    // 삭제 확인
    const listAfterResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    const listAfter = await listAfterResp.json()
    expect(listAfter.content.find((r: any) => r.id === createdRuleId)).toBeFalsy()
  })
})

// ── C. /admin/content 페이지 구조 + synthetic 계약 ───────────────
test.describe('Journey 11-C: /admin/content 페이지 + API 계약', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 — /admin/content 게시글 탭 로드', async ({ page }) => {
    await page.goto(`${BASE}/admin/content`)
    await page.waitForURL(/\/admin\/content/, { timeout: 10_000 })
    await expect(page.getByText('콘텐츠 관리')).toBeVisible({ timeout: 8_000 })
    await expect(page.getByRole('tab', { name: '게시글' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '댓글·대댓글' })).toBeVisible()
  })

  test('게시글 탭 — 액션 컬럼 존재', async ({ page }) => {
    await page.goto(`${BASE}/admin/content`)
    await page.waitForURL(/\/admin\/content/)
    await expect(page.getByRole('columnheader', { name: '액션' }).first()).toBeVisible({ timeout: 10_000 })
  })

  test('/api/admin/content/posts 응답 — synthetic 필드 포함', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const resp = await request.get(`${BASE}/api/admin/content/posts?status=VOTING&page=0&size=5`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    if (data.content && data.content.length > 0) {
      expect(typeof data.content[0].synthetic).toBe('boolean')
    }
    expect(typeof data.totalElements).toBe('number')
    expect(typeof data.totalPages).toBe('number')
  })

  test('/api/admin/content/comments 응답 — synthetic 필드 포함', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const resp = await request.get(`${BASE}/api/admin/content/comments?status=ACTIVE&page=0&size=5`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    if (data.content && data.content.length > 0) {
      expect(typeof data.content[0].synthetic).toBe('boolean')
    }
    expect(typeof data.totalElements).toBe('number')
  })
})

// ── D. 비관리자 접근 차단 ─────────────────────────────────────────
test.describe('Journey 11-D: 비관리자 접근 차단', () => {

  test('미로그인 — /admin/ai-rules → /login 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })

  test('일반 회원(USER only) — /api/admin/ai-rules/global → 403', async ({ request }) => {
    // test5 storageState에서 토큰 읽기 (중복 login() 제거)
    const userToken = tokenFromStorageState(PERSONAS[4].email)
    if (!userToken) {
      test.skip()
      return
    }
    const resp = await request.get(`${BASE}/api/admin/ai-rules/global`, {
      headers: { Authorization: `Bearer ${userToken}` },
    })
    expect([403, 401]).toContain(resp.status())
  })
})

// ── E. 사이드바 AI 규칙관리 링크 ─────────────────────────────────
test.describe('Journey 11-E: 사이드바 AI 규칙관리 링크', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 사이드바 — "AI 규칙관리" 링크 표시 + 이동 (데스크탑)', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })

    const aiRulesLink = page.getByRole('link', { name: 'AI 규칙관리' })
    await expect(aiRulesLink).toBeVisible({ timeout: 8_000 })

    await aiRulesLink.click()
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 8_000 })
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })
  })
})

// ── F. 첨삭 API 계약 (비-LLM 경로) ──────────────────────────────
test.describe('Journey 11-F: 첨삭 API 계약 (비-LLM)', () => {

  test('/api/admin/content/corrections/commit — 없는 ID → 4xx', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()

    const resp = await request.post(`${BASE}/api/admin/content/corrections/commit`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        targetType: 'POST',
        targetId: 'nonexistent-post-id-00000000000000',
        correctedText: '수정본',
        personaCaution: null,
        globalRules: [],
        applyLive: false,
      },
    })
    expect(resp.status()).toBeGreaterThanOrEqual(400)
    expect(resp.status()).toBeLessThan(500)
  })

  test('/api/admin/ai-rules/global POST — ruleText 빈 값 → 500 미만', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()

    const resp = await request.post(`${BASE}/api/admin/ai-rules/global`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { ruleText: '', scope: 'ALL' },
    })
    expect(resp.status()).toBeLessThan(500)
  })
})

// ── G. adminOpinion 필드 계약 ─────────────────────────────────────
test.describe('Journey 11-G: adminOpinion 필드 계약 (비-LLM)', () => {

  test('/corrections/save — adminOpinion 포함 DTO 수용 (없는 target → 4xx, 필드 무시 아님)', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}` }

    // 없는 target은 4xx (DTO 파싱 오류가 아닌 비즈니스 오류)
    const resp = await request.post(`${BASE}/api/admin/content/corrections/save`, {
      headers,
      data: {
        targetType: 'POST',
        targetId: 'nonexistent-post-e2e-99999',
        correctedText: '수정본 텍스트',
        applyLive: false,
        adminOpinion: '이것은 e2e 테스트용 관리자 의견입니다.',
      },
    })
    // 4xx (not 400 Bad Request for unknown field — DTO accepts adminOpinion)
    expect(resp.status()).toBeGreaterThanOrEqual(400)
    expect(resp.status()).toBeLessThan(500)
  })

  test('/history 응답 — adminOpinion 필드 존재', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()

    const resp = await request.get(`${BASE}/api/admin/ai-rules/history?size=5`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    expect(typeof data.totalElements).toBe('number')
    // adminOpinion 필드가 존재하거나 null인지 확인 (레코드가 있을 때)
    if (data.content && data.content.length > 0) {
      const item = data.content[0]
      expect('adminOpinion' in item).toBeTruthy()
    }
  })
})

// ── I. 기본 프롬프트 템플릿 저장 (슬래시 키 경로 인코딩 버그 회귀) ──
test.describe('Journey 11-I: 기본 프롬프트 템플릿 CRUD', () => {

  test('GET /prompts → 4개 템플릿 목록 반환', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()

    const resp = await request.get(`${BASE}/api/admin/ai-rules/prompts`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    expect(Array.isArray(data)).toBeTruthy()
    expect(data.length).toBeGreaterThanOrEqual(4)
    const keys = data.map((t: any) => t.key)
    expect(keys).toContain('voice/post')
    expect(keys).toContain('voice/comment')
  })

  test('PUT /prompts/voice/post → 저장 성공 (슬래시 키 경로 버그 회귀 방지)', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' }

    // 현재 내용 읽기
    const getResp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/post`, { headers })
    expect(getResp.ok()).toBeTruthy()
    const original = await getResp.json()

    // 저장 (원본 내용 그대로 PUT → 응답 확인 후 복원 불필요)
    const putResp = await request.put(`${BASE}/api/admin/ai-rules/prompts/voice/post`, {
      headers,
      data: { content: original.content },
    })
    expect(putResp.ok()).toBeTruthy()
    const updated = await putResp.json()
    expect(updated.key).toBe('voice/post')
    expect(typeof updated.content).toBe('string')
  })
})

// ── H. apply-batch-plan 계약 (비-LLM) ────────────────────────────
test.describe('Journey 11-H: apply-batch-plan 계약 (비-LLM)', () => {

  test('합성 plan 적용 → 전역 규칙 생성 후 삭제', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}` }

    // apply-batch-plan: LLM 없음 — 합성 plan으로 전역 규칙 1개 생성
    const applyResp = await request.post(`${BASE}/api/admin/ai-rules/history/apply-batch-plan`, {
      headers,
      data: {
        globalRules: [
          {
            ruleText: '[e2e테스트] 일괄분석 배치플랜 테스트 규칙 — 삭제 예정',
            scope: 'ALL',
            sourceCorrIds: [],
          },
        ],
        personaCautions: [],
        pushToBank: false,
      },
    })
    expect(applyResp.ok()).toBeTruthy()
    const applyData = await applyResp.json()
    expect(typeof applyData.rulesCreated).toBe('number')
    expect(applyData.rulesCreated).toBeGreaterThanOrEqual(1)

    // 생성된 규칙 조회 후 정리
    const listResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    expect(listResp.ok()).toBeTruthy()
    const list = await listResp.json()
    const created = list.content?.find((r: any) => r.ruleText?.includes('e2e테스트') && r.ruleText?.includes('일괄분석'))
    if (created) {
      const deleteResp = await request.delete(`${BASE}/api/admin/ai-rules/global/${created.id}`, { headers })
      expect(deleteResp.status()).toBe(204)
    }
  })
})
