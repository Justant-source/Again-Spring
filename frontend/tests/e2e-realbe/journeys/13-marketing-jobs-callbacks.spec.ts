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

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)  // test1 = ADMIN

// ── A. PARTIAL 상태 배지 ────────────────────────────────────────────
test.describe('Journey 13-D: PARTIAL 상태 배지 표시', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('PARTIAL 배지 — 노란색 스타일링 확인', async ({ page }) => {
    // 어드민 페이지에서 마케팅 잡 목록 로드
    await page.goto(`${BASE}/admin/marketing`)
    await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })

    // PARTIAL 상태를 가진 배지 찾기 (data-testid 또는 text로 찾음)
    const partialBadges = page.locator('[data-testid="job-status-badge"]:has-text("PARTIAL")')

    // 존재하는 경우 스타일 검증
    if ((await partialBadges.count()) > 0) {
      const firstBadge = partialBadges.first()

      // 배지의 배경색이 노란색 계열인지 확인
      // (CSS class 또는 style 속성으로 yellow/amber 색상 확인)
      const className = await firstBadge.getAttribute('class')
      const style = await firstBadge.getAttribute('style')

      expect(
        className?.includes('yellow') ||
        className?.includes('amber') ||
        className?.includes('warn') ||
        style?.includes('yellow') ||
        style?.includes('amber') ||
        style?.includes('#f59e0b') || // amber-500 hex
        style?.includes('rgb(245, 158, 11)'), // amber-500 rgb
        'PARTIAL 배지가 노란색 스타일링을 가져야 함'
      ).toBe(true)
    } else {
      // PARTIAL 상태 잡이 없으면 테스트 스킵
      test.skip(true, 'PARTIAL 상태 잡이 없음 — 테스트 스킵')
    }
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
