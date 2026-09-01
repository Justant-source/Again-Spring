/**
 * Journey 14: 마케팅 플랫폼 계정 자격증명 관리 (ASM 암호화 저장)
 *
 * A. 인증 가드 (ASM 불필요) — 항상 실행
 *    - 미인증 GET /credentials → 401/403 (비-어드민 403은 Journey 09-B2)
 *
 * B. UI 설정 탭 (ASM 불필요) — 항상 실행
 *    - /admin/marketing 「설정」탭 — 자격증명 + 플랫폼 자동 게시 + X 운영 섹션
 *
 * C. 자격증명 조회 (ASM 필요) — ASM_STUB_AVAILABLE=true 시 실행 / **읽기 전용**
 *    - 어드민 GET /credentials → 200 + 8개 플랫폼(숏폼영상 공용 설정 포함)
 *    - 마스킹 불변식: secret 필드 값이 `values`에 절대 노출되지 않음
 *
 * 가드레일: LLM 미호출. ASM 자격증명 저장소는 dev·prod 공유 단일 인스턴스이므로
 * 이 저니는 **쓰기(PUT/DELETE)를 수행하지 않는다** (실데이터 오염 방지).
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { tokenFromStorageState } from '../support/api'
import { ADMIN_MARKETING } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email) // test1 = ADMIN
const ASM_AVAILABLE = process.env.ASM_STUB_AVAILABLE === 'true'

const EXPECTED_PLATFORMS = [
  'x',
  'instagram_feed',
  'instagram_reels',
  'naver_blog',
  'naver_clip',
  'youtube_shorts',
  'threads',
  // 릴스·쇼츠가 WaggleBot에서 같은 영상을 공유하므로 나레이션(tts_voice/comment_tts_voices)은
  // 설정 전용 pseudo-platform으로 분리됨(2026-08-10, 로그인/게시 기능 없음).
  'shortform_video',
]

// ── A. 인증 가드 (ASM 불필요) ────────────────────────────────────
test.describe('Journey 14-A: 자격증명 API 인증 가드', () => {
  test('미인증 — GET /api/admin/marketing/credentials → 401/403', async ({ request }) => {
    const res = await request.get(`${BASE}/api/admin/marketing/credentials`)
    expect([401, 403]).toContain(res.status())
  })

  test('미인증 — GET /api/admin/marketing/x-ops → 401/403', async ({ request }) => {
    const res = await request.get(`${BASE}/api/admin/marketing/x-ops`)
    expect([401, 403]).toContain(res.status())
  })

  test('미인증 — POST /api/admin/marketing/x-ops/learn → 401/403', async ({ request }) => {
    const res = await request.post(`${BASE}/api/admin/marketing/x-ops/learn`)
    expect([401, 403]).toContain(res.status())
  })

  test('미인증 — POST /api/admin/marketing/x-ops/outbound → 401/403', async ({ request }) => {
    const res = await request.post(`${BASE}/api/admin/marketing/x-ops/outbound`)
    expect([401, 403]).toContain(res.status())
  })
})

// ── B. UI 설정 탭 (ASM 불필요) ───────────────────────────────────
test.describe('Journey 14-B: 설정 탭 (자격증명·자동 게시)', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('어드민 — /admin/marketing 「설정」탭에 자격증명·자동 게시', async ({ page }) => {
    await page.goto(`${BASE}/admin/marketing`)
    await page.waitForURL(/\/admin\/marketing/, { timeout: 10_000 })

    const settingsTab = page.getByRole('tab', { name: '설정' })
    await expect(settingsTab).toBeVisible({ timeout: 8_000 })
    await settingsTab.click()

    // 플랫폼 자동 게시 섹션 (ASM 없이도 섹션 셸 노출; 목록 로드는 ASM 의존 가능)
    await expect(page.locator(ADMIN_MARKETING.platformAutoSection)).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('자동 게시 대상')).toBeVisible()

    await expect(page.locator(ADMIN_MARKETING.xOpsSection)).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('X 운영')).toBeVisible()
    await expect(page.locator(ADMIN_MARKETING.xOpsMimicryBadge)).toBeVisible()
    await expect(page.getByRole('switch', { name: '아침/밤 글' })).toHaveAttribute(
      'aria-checked',
      'false'
    )
    await expect(page.getByRole('switch', { name: '페르소나 학습' })).toHaveAttribute(
      'aria-checked',
      'true'
    )
    await expect(page.getByRole('switch', { name: '페르소나 채점' })).toHaveAttribute(
      'aria-checked',
      'true'
    )
    await expect(page.getByRole('switch', { name: '원글 자동 작성' })).toHaveAttribute(
      'aria-checked',
      'false'
    )
    await expect(page.getByRole('button', { name: '지금 학습' })).toBeVisible()
    // 섹션 존재·기본 스위치·학습 버튼만. inbound/outbound/ritual/원글 on 클릭·지금 학습 클릭·실 X 게시 없음
    // POST /x-ops/learn · /outbound 인증 호출은 14-A 미인증만. 여기선 트리거하지 않음

    // 숏폼영상 섹션 — 릴스·쇼츠 공용 나레이션 설정 박스 (ASM 없이도 셸 노출)
    await expect(page.locator(ADMIN_MARKETING.shortformVideoSection)).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('숏폼영상')).toBeVisible()

    // 자격증명 안내 — 카드 렌더는 ASM 필요 → 문구만 검증
    await expect(page.getByText(/계정 정보를 입력|암호화되어 저장/)).toBeVisible({ timeout: 8_000 })
  })
})

// ── C. 자격증명 조회 (ASM 필요, 읽기 전용) ───────────────────────
// ASM 미기동 시 describe 미등록 → skipped 카운트에 안 잡힘
if (ASM_AVAILABLE) {
test.describe('Journey 14-C: 자격증명 조회 + 마스킹 불변식 (ASM)', () => {
  test('어드민 — GET /credentials → 200 + 8개 플랫폼, 시크릿 미노출', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    test.skip(!token, 'test1 storageState 없음')

    const res = await request.get(`${BASE}/api/admin/marketing/credentials`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(res.status(), '자격증명 목록 응답').toBe(200)

    const list = (await res.json()) as Array<{
      platform: string
      fields: Array<{ key: string; secret: boolean; required: boolean }>
      configured: boolean
      values: Record<string, string>
      secret_set: Record<string, boolean>
    }>

    expect(Array.isArray(list)).toBe(true)
    const platforms = list.map((c) => c.platform).sort()
    expect(platforms, '7개 플랫폼 모두 노출').toEqual([...EXPECTED_PLATFORMS].sort())

    // 마스킹 불변식: secret 필드 키가 values에 절대 들어있지 않음
    for (const cred of list) {
      const secretKeys = cred.fields.filter((f) => f.secret).map((f) => f.key)
      for (const sk of secretKeys) {
        expect(cred.values, `${cred.platform}.values 에 secret(${sk}) 미노출`).not.toHaveProperty(sk)
        expect(Object.keys(cred.secret_set), `${cred.platform}.secret_set 에 ${sk} 포함`).toContain(sk)
      }
    }
  })
})
}
