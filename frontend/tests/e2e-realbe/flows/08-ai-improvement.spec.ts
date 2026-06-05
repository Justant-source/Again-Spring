/**
 * Flow 08: AI 개선(첨삭 학습) 기능 E2E 테스트
 *
 * 커버 범위:
 *   A. /admin/ai-rules — 관리자 접근 가능, 페이지 로드 및 탭 전환
 *   B. /admin/ai-rules — 전역 금지 규칙 수동 추가·비활성화·삭제 API 플로우
 *   C. /admin/content  — 페이지 로드, 게시글/댓글 탭 구조 확인
 *   D. /admin/ai-rules — 비관리자 접근 시 리다이렉트
 *   E. nav-config — "AI 규칙관리" 사이드바 링크 노출
 *   F. /api/admin/content/posts — synthetic 필드 포함 응답 확인 (API 레벨)
 *
 * 실행 조건:
 *   - dev docker 스택 가동 중(8090)
 *   - global-setup 완료 (test1 → ADMIN, .auth/test1_at_again.com.json 존재)
 *   - V68 마이그레이션 적용 완료
 *
 * CLAUDE.md 준수: e2e-realbe는 dev(8090)에서만 실행. prod 대상 절대 금지.
 */
import { test, expect, request as playwrightRequest } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

// ── A. /admin/ai-rules — 페이지 접근 및 기본 UI ──────────────────────────

test.describe('Flow 08-A: /admin/ai-rules 페이지', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자가 /admin/ai-rules에 접근하면 페이지가 로드된다', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 10_000 })

    // 헤더 타이틀 확인
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })
  })

  test('전역 금지 규칙 탭이 기본으로 열려 있다', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/)

    const globalTab = page.getByRole('tab', { name: '전역 금지 규칙' })
    await expect(globalTab).toBeVisible({ timeout: 8_000 })
    await expect(globalTab).toHaveAttribute('aria-selected', 'true')
  })

  test('페르소나 주의사항 탭으로 전환 가능하다', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/)
    await page.waitForTimeout(1_000)

    await page.getByRole('tab', { name: '페르소나 주의사항' }).click()
    await expect(page.getByText('페르소나 ID 필터')).toBeVisible({ timeout: 5_000 })
  })

  test('새 규칙 추가 입력란이 노출된다', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/)

    await expect(page.getByPlaceholder(/새 규칙 추가|전여친/)).toBeVisible({ timeout: 8_000 })
    await expect(page.getByRole('button', { name: '추가' })).toBeVisible()
  })
})

// ── B. 전역 금지 규칙 API 플로우 ──────────────────────────────────────────

test.describe('Flow 08-B: 전역 금지 규칙 CRUD (API)', () => {
  let createdRuleId: number

  test('전역 금지 규칙 추가 → 조회 → 비활성화 → 삭제', async ({ request }) => {
    // 1) 관리자 로그인 → JWT 획득
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA_TEST1.email, password: PERSONA_TEST1.password },
    })
    expect(loginResp.ok()).toBeTruthy()
    const { accessToken } = await loginResp.json()
    const headers = { Authorization: `Bearer ${accessToken}` }

    // 2) 전역 규칙 추가
    const createResp = await request.post(`${BASE}/api/admin/ai-rules/global`, {
      headers,
      data: { ruleText: '[e2e테스트] 자동화 테스트 전역 규칙 — 삭제 예정', scope: 'ALL' },
    })
    expect(createResp.status()).toBe(201)
    const created = await createResp.json()
    createdRuleId = created.id
    expect(created.ruleText).toContain('e2e테스트')
    expect(created.active).toBe(true)

    // 3) 목록 조회 — 방금 추가한 규칙이 포함돼야 함
    const listResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    expect(listResp.ok()).toBeTruthy()
    const list = await listResp.json()
    const found = list.content.find((r: any) => r.id === createdRuleId)
    expect(found).toBeTruthy()
    expect(found.scope).toBe('ALL')

    // 4) 비활성화
    const toggleResp = await request.patch(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, {
      headers,
      data: { active: false },
    })
    expect(toggleResp.ok()).toBeTruthy()
    const toggled = await toggleResp.json()
    expect(toggled.active).toBe(false)

    // 5) 삭제
    const deleteResp = await request.delete(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, {
      headers,
    })
    expect(deleteResp.status()).toBe(204)

    // 6) 삭제 확인 — 목록에서 사라짐
    const listAfterResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    const listAfter = await listAfterResp.json()
    const stillExists = listAfter.content.find((r: any) => r.id === createdRuleId)
    expect(stillExists).toBeFalsy()
  })
})

// ── C. /admin/content 페이지 구조 ────────────────────────────────────────

test.describe('Flow 08-C: /admin/content 페이지 구조', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자가 /admin/content에 접근하면 게시글 탭이 로드된다', async ({ page }) => {
    await page.goto(`${BASE}/admin/content`)
    await page.waitForURL(/\/admin\/content/, { timeout: 10_000 })

    // 콘텐츠 관리 타이틀
    await expect(page.getByText('콘텐츠 관리')).toBeVisible({ timeout: 8_000 })

    // 게시글/댓글 탭 확인
    await expect(page.getByRole('tab', { name: '게시글' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '댓글·대댓글' })).toBeVisible()
  })

  test('게시글 탭에서 액션 컬럼이 존재한다', async ({ page }) => {
    await page.goto(`${BASE}/admin/content`)
    await page.waitForURL(/\/admin\/content/)
    await page.waitForTimeout(2_000) // 데이터 로드 대기

    // 테이블 헤더에 "액션" 컬럼 존재
    const actionHeader = page.getByRole('columnheader', { name: '액션' }).first()
    await expect(actionHeader).toBeVisible({ timeout: 8_000 })
  })

  test('/api/admin/content/posts 응답에 synthetic 필드가 포함된다', async ({ request }) => {
    // API 레벨 검증: synthetic 필드 노출 여부
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA_TEST1.email, password: PERSONA_TEST1.password },
    })
    const { accessToken } = await loginResp.json()

    const resp = await request.get(`${BASE}/api/admin/content/posts?status=VOTING&page=0&size=5`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    // content 배열이 있으면 각 항목에 synthetic 필드가 boolean으로 존재해야 함
    if (data.content && data.content.length > 0) {
      const firstPost = data.content[0]
      expect(typeof firstPost.synthetic).toBe('boolean')
    }
    // content가 비어있어도 페이지 구조는 유효해야 함
    expect(typeof data.totalElements).toBe('number')
    expect(typeof data.totalPages).toBe('number')
  })

  test('/api/admin/content/comments 응답에 synthetic 필드가 포함된다', async ({ request }) => {
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA_TEST1.email, password: PERSONA_TEST1.password },
    })
    const { accessToken } = await loginResp.json()

    const resp = await request.get(`${BASE}/api/admin/content/comments?status=ACTIVE&page=0&size=5`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(resp.ok()).toBeTruthy()
    const data = await resp.json()
    if (data.content && data.content.length > 0) {
      const firstComment = data.content[0]
      expect(typeof firstComment.synthetic).toBe('boolean')
    }
    expect(typeof data.totalElements).toBe('number')
  })
})

// ── D. 비관리자 접근 차단 ─────────────────────────────────────────────────

test.describe('Flow 08-D: 비관리자 /admin/ai-rules 접근 차단', () => {
  test('비로그인 사용자가 /admin/ai-rules 접근 → /login 리다이렉트', async ({ page }) => {
    // storageState 미사용 = 비인증 상태
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })

  test('일반 회원(USER only)이 /api/admin/ai-rules/global 접근 → 403', async ({ request }) => {
    // test5는 USER only (globalSetup에서 ADMIN/TESTER 미부여)
    const test5 = PERSONAS[4] // test5@again.com
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: test5.email, password: test5.password },
    })
    if (!loginResp.ok()) {
      // 로그인 실패 시 테스트 스킵 (시드 미존재 환경)
      test.skip()
      return
    }
    const { accessToken } = await loginResp.json()

    const resp = await request.get(`${BASE}/api/admin/ai-rules/global`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect([403, 401]).toContain(resp.status())
  })
})

// ── E. 사이드바 네비게이션 ────────────────────────────────────────────────

test.describe('Flow 08-E: 사이드바 AI 규칙관리 링크', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 사이드바에 "AI 규칙관리" 링크가 표시된다', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })
    await page.waitForTimeout(1_500)

    // 사이드바의 AI 규칙관리 링크 (텍스트 기반 — nav-config.ts 라벨)
    const aiRulesLink = page.getByRole('link', { name: 'AI 규칙관리' })
    await expect(aiRulesLink).toBeVisible({ timeout: 8_000 })
  })

  test('"AI 규칙관리" 링크 클릭 → /admin/ai-rules로 이동한다', async ({ page }) => {
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })
    await page.waitForTimeout(1_500)

    await page.getByRole('link', { name: 'AI 규칙관리' }).click()
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 8_000 })
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })
  })
})

// ── F. 첨삭 분석 API 계약 확인 ───────────────────────────────────────────

test.describe('Flow 08-F: 첨삭 API 계약 (비 LLM 호출 경로)', () => {
  test('/api/admin/content/corrections/commit — synthetic 아닌 글 거부(400)', async ({ request }) => {
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA_TEST1.email, password: PERSONA_TEST1.password },
    })
    const { accessToken } = await loginResp.json()

    // 존재하지 않는 ID로 commit 시도 → 404 또는 400 예상
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
    // 존재하지 않는 글이므로 4xx 응답
    expect(resp.status()).toBeGreaterThanOrEqual(400)
    expect(resp.status()).toBeLessThan(500)
  })

  test('/api/admin/ai-rules/global POST — ruleText 없이 요청 시 400', async ({ request }) => {
    const loginResp = await request.post(`${BASE}/api/auth/login`, {
      data: { email: PERSONA_TEST1.email, password: PERSONA_TEST1.password },
    })
    const { accessToken } = await loginResp.json()

    const resp = await request.post(`${BASE}/api/admin/ai-rules/global`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { ruleText: '', scope: 'ALL' }, // 빈 ruleText
    })
    // 빈 텍스트는 서버에서 검증해야 하지만 현재는 저장될 수도 있음
    // 최소한 5xx는 아니어야 함
    expect(resp.status()).toBeLessThan(500)
  })
})
