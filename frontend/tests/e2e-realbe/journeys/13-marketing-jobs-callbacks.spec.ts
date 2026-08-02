/**
 * Journey 13 Extended: 마케팅 잡 콜백 + PARTIAL 상태
 *
 * - PARTIAL 상태 배지 — 노란색 스타일 확인
 * - POST /api/internal/marketing/callback 인증
 *   - 유효한 토큰 + payload → 204 No Content
 *   - 잘못된 토큰 → 401 Unauthorized
 *   - 누락된 Authorization 헤더 → 401 Unauthorized
 *
 * 가드레일: LLM 미호출, 실 ASM·Claude 불필요.
 * 콜백 엔드포인트는 내부용이므로 FE 인증이 아닌 Bearer 토큰(ASM 콜백 시크릿) 검증.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { tokenFromStorageState, createPost } from '../support/api'
import { sql } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)  // test1 = ADMIN

// ── A. PARTIAL 상태 배지 ────────────────────────────────────────────
test.describe('Journey 13-D: PARTIAL 상태 배지 표시', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('PARTIAL 배지 — 노란색 스타일링 확인', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'E2E PARTIAL 배지 시드 사연',
      body: 'e2e marketing PARTIAL badge seed post body — cleanup targets this author.',
    })

    // marketing_job 행 직접 시드 (ASM 불필요). teardown cleanup이 테스트 포스트와 함께 삭제.
    sql(`
      INSERT INTO marketing_job (
        remote_job_id, post_id, status, phase, progress, targets, auto_publish,
        requested_by, poll_fail_count, created_at, updated_at, idempotency_key
      ) VALUES (
        'e2e-partial-${Date.now()}', '${postId}', 'PARTIAL', 'PUBLISH', 1.0,
        '["x"]', 0, 'e2epersona01', 0, NOW(), NOW(), 'e2e-partial-${Date.now()}'
      );
    `)

    await page.goto(`${BASE}/admin/marketing`)
    await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })

    const partialBadge = page
      .locator('[data-testid="job-status-badge"][data-status="PARTIAL"], .bg-yellow-500')
      .filter({ hasText: 'PARTIAL' })
      .first()
    await expect(partialBadge).toBeVisible({ timeout: 10_000 })

    const className = (await partialBadge.getAttribute('class')) ?? ''
    expect(
      className.includes('yellow') || className.includes('amber'),
      `PARTIAL 배지 노란색 클래스 필요, got: ${className}`,
    ).toBe(true)
  })
})

// ── B. 콜백 엔드포인트 인증 ────────────────────────────────────────
test.describe('Journey 13-E: 콜백 엔드포인트 인증', () => {

  const CALLBACK_URL = `${BASE}/api/internal/marketing/callback`
  const VALID_TOKEN = process.env.ASM_CALLBACK_TOKEN ?? 'asm-callback-token-dev'
  const INVALID_TOKEN = 'invalid-token-xyz'

  // 콜백 payload 샘플 — artifacts는 Map<String,Object>(JSON object), publications는 List<Map>(JSON array)
  const VALID_PAYLOAD = {
    job_id: 'remote-job-e2e-' + Date.now(),
    status: 'PUBLISHED',
    event: 'PUBLISHED',
    artifacts: {},
    publications: [],
  }

  test('콜백 엔드포인트 — 잘못된 토큰은 401 반환', async ({ request }) => {
    const res = await request.post(CALLBACK_URL, {
      headers: {
        'Authorization': `Bearer ${INVALID_TOKEN}`,
        'Content-Type': 'application/json',
      },
      data: VALID_PAYLOAD,
    })

    expect(
      res.status(),
      '잘못된 Bearer 토큰으로 콜백 요청 시 401 반환',
    ).toBe(401)
  })

  test('콜백 엔드포인트 — 누락된 Authorization 헤더는 401 반환', async ({ request }) => {
    const res = await request.post(CALLBACK_URL, {
      headers: {
        'Content-Type': 'application/json',
      },
      data: VALID_PAYLOAD,
    })

    expect(
      res.status(),
      'Authorization 헤더 누락 시 401 반환',
    ).toBe(401)
  })

  test('콜백 엔드포인트 — 유효한 토큰과 payload는 204 반환', async ({ request }) => {
    const res = await request.post(CALLBACK_URL, {
      headers: {
        'Authorization': `Bearer ${VALID_TOKEN}`,
        'Content-Type': 'application/json',
      },
      data: VALID_PAYLOAD,
    })

    expect(
      res.status(),
      '유효한 Bearer 토큰과 payload로 콜백 요청 시 204 반환',
    ).toBe(204)

    // 204 No Content는 body가 없어야 함
    const text = await res.text()
    expect(text.length, '204 응답은 body 없음').toBe(0)
  })

  test('콜백 엔드포인트 — 유효한 토큰, Authorization 형식 검증', async ({ request }) => {
    // Authorization 헤더 형식 검증 (Bearer 접두사 필수)
    const resNoBearer = await request.post(CALLBACK_URL, {
      headers: {
        'Authorization': VALID_TOKEN, // "Bearer " 접두사 없음
        'Content-Type': 'application/json',
      },
      data: VALID_PAYLOAD,
    })

    expect(
      resNoBearer.status(),
      'Bearer 접두사 없으면 401 반환',
    ).toBe(401)
  })
})
