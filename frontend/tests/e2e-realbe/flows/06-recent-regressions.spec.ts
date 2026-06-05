/**
 * Flow 06: 최근(6/4~6/5) 변경 회귀 방지
 *
 * 기존 spec(01~05)에 포함되지 않은 최근 프론트엔드 변경을 커버한다.
 *
 * 커버 범위:
 *   A. 로그인 페이지 — "게스트로 둘러보기" 버튼·"계정이 없으신가요?" 문구 삭제 (commit 7897a61)
 *   B. GuestInfoSheet — 로그인 버튼 존재 + /login 이동 (commit 4554e15)
 *   C. 게스트 하단 "알림" 탭 → 게스트 안내 시트 (commit 7897a61)
 *   D. 게스트 하단 "내 활동" 탭 → 게스트 안내 시트 (commit 7897a61)
 *   E. 게스트 투표 지속성 — 투표 후 새로고침해도 완료 유지 (commit 4554e15)
 *   F. 댓글 첫 로드 중복 렌더 없음 (commit bd7589c)
 *
 * 실행 조건: dev docker 스택 가동 중(8090), mock_001 시드 존재.
 * 게스트 테스트는 storageState 미사용 = 비인증 → 페이지가 자동으로 게스트 발급.
 */
import { test, expect } from '@playwright/test'
import {
  USER_CHIP,
  GUEST_INFO_SHEET,
  NAV_NOTIFICATIONS,
  NAV_ACTIVITY,
  STORY_VOTE_BTN,
  VOTE_COMPLETE_BADGE,
  COMMENT_BAR_PLACEHOLDER,
  COMMENT_SUBMIT_BTN,
  COMMENT_MENU_TOGGLE,
  COMMENT_MENU_EDIT,
  COMMENT_MENU_DELETE,
} from '../support/selectors'
import { guestLogin } from '../fixtures/api-helpers'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

/** 댓글 0개인 새 포스트를 게스트 권한으로 생성하고 id 반환 (테스트 격리용) */
async function createFreshPost(request: import('@playwright/test').APIRequestContext): Promise<string> {
  const token = await guestLogin(request, 'E2E작성자')
  const resp = await request.post(`${BASE}/api/community/posts`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      bodyRaw: 'e2e 댓글 수정·삭제 테스트용 사연 본문입니다. 충분한 길이 확보.',
      category: 'OTHER',
      visibility: 'PUBLIC',
      jurorCount: 0,
      userTitle: 'E2E 댓글 수정삭제 포스트',
    },
  })
  if (!resp.ok()) throw new Error(`포스트 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).id as string
}

// 게스트 자동 발급 대기 (useGuestInit이 토큰·user를 채울 시간)
const GUEST_INIT_MS = 2_000

// ── A. 로그인 페이지 게스트 진입 UI 삭제 ──────────────────────────
test.describe('Flow 06-A: 로그인 페이지 정리 (회귀 방지)', () => {

  test('로그인 — "게스트로 둘러보기" 버튼·"계정이 없으신가요?" 문구 없음', async ({ page }) => {
    await page.goto(`${BASE}/login`)
    // 페이지 로드 확인 (이메일 입력 존재)
    await expect(page.getByPlaceholder('이메일')).toBeVisible({ timeout: 8_000 })

    // 삭제된 요소들이 더 이상 존재하지 않아야 함
    await expect(page.getByText('게스트로 둘러보기')).toHaveCount(0)
    await expect(page.getByText('계정이 없으신가요')).toHaveCount(0)

    // 회원가입·비밀번호 찾기 링크는 그대로 유지
    await expect(page.getByRole('link', { name: '회원가입' })).toBeVisible()
    await expect(page.getByRole('link', { name: '비밀번호 찾기' })).toBeVisible()
  })
})

// ── B. GuestInfoSheet 로그인 버튼 ────────────────────────────────
test.describe('Flow 06-B: 게스트 안내 시트 로그인 버튼', () => {

  test('UserChip 시트 — 로그인 버튼 존재 + /login 이동', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForTimeout(GUEST_INIT_MS)

    await page.locator(USER_CHIP).click()
    const sheet = page.locator(GUEST_INFO_SHEET)
    await expect(sheet).toBeVisible({ timeout: 5_000 })

    // 시트 내부 3개 버튼 모두 존재
    await expect(sheet.getByRole('button', { name: '회원가입하기' })).toBeVisible()
    await expect(sheet.getByRole('button', { name: '게스트로 계속하기' })).toBeVisible()
    const loginBtn = sheet.getByRole('button', { name: '로그인' })
    await expect(loginBtn).toBeVisible()

    // 로그인 버튼 → /login 이동
    await loginBtn.click()
    await page.waitForURL(/\/login/, { timeout: 8_000 })
    expect(page.url()).toContain('/login')
  })
})

// ── C·D. 게스트 하단 네비게이션 → 안내 시트 ───────────────────────
test.describe('Flow 06-CD: 게스트 하단 네비게이션 안내 시트', () => {

  test('게스트 — 하단 "알림" 탭 클릭 → 게스트 안내 시트 (이동 없음)', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForTimeout(GUEST_INIT_MS)

    await page.locator(NAV_NOTIFICATIONS).click()
    await expect(page.locator(GUEST_INFO_SHEET)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('게스트로는')).toBeVisible()
    // /notifications로 라우팅되지 않고 시트만 떠야 함
    expect(page.url()).not.toContain('/notifications')
  })

  test('게스트 — 하단 "내 활동" 탭 클릭 → 게스트 안내 시트 (이동 없음)', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForTimeout(GUEST_INIT_MS)

    await page.locator(NAV_ACTIVITY).click()
    await expect(page.locator(GUEST_INFO_SHEET)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('게스트로는')).toBeVisible()
    // /profile로 라우팅(=> /login 리다이렉트)되지 않고 시트만 떠야 함
    expect(page.url()).not.toContain('/profile')
    expect(page.url()).not.toContain('/login')
  })
})

// ── E. 게스트 투표 지속성 ────────────────────────────────────────
test.describe('Flow 06-E: 게스트 투표 지속성', () => {

  test('게스트 — 투표 후 새로고침해도 완료 상태 유지', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001`)
    await page.waitForTimeout(GUEST_INIT_MS)

    // 새 게스트이므로 아직 투표 전 — 완료 배지 없음
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toHaveCount(0)

    // 작성자 쪽 투표 버튼 클릭
    const voteG = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteG).toBeVisible({ timeout: 10_000 })
    await voteG.click()

    // 완료 배지 표시
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 8_000 })

    // 새로고침(재방문) → 완료 상태 유지 (6/5 이전 버그: 즉시 취소/미유지)
    await page.reload()
    await page.waitForTimeout(GUEST_INIT_MS)
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })
  })
})

// ── F. 댓글 첫 로드 중복 없음 ────────────────────────────────────
test.describe('Flow 06-F: 댓글 무한스크롤 중복 방지', () => {

  test('댓글 — 첫 로드 시 댓글 노드 중복 렌더 없음 (bd7589c 회귀 방지)', async ({ page }) => {
    // 댓글 1건 이상 보장 (게스트 작성 가능)
    await page.goto(`${BASE}/community/mock_001/comments`)
    const bar = page.getByText(COMMENT_BAR_PLACEHOLDER)
    await expect(bar).toBeVisible({ timeout: 8_000 })
    await bar.click()
    const ta = page.locator('textarea')
    await expect(ta).toBeVisible({ timeout: 3_000 })
    await ta.fill(`중복방지테스트-${Date.now()}`)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()
    await page.waitForTimeout(1_500)

    // 새로고침 → 첫 로드 경로(observer 즉시 발화) 재현
    await page.reload()
    // 버그가 있으면 page0이 두 번 로드될 시간까지 충분히 대기
    await page.waitForTimeout(2_500)

    // 모든 댓글 DOM 노드(id="comment-N")의 id가 유일해야 함
    // (수정 전: 첫 페이지 댓글이 중복 렌더되어 같은 id가 2개씩 존재)
    const ids = await page
      .locator('[id^="comment-"]')
      .evaluateAll((els) => els.map((e) => e.id))
    expect(ids.length).toBeGreaterThanOrEqual(1)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

// ── G. 댓글 수정·삭제 ────────────────────────────────────────────
test.describe('Flow 06-G: 댓글 수정·삭제 (본인 댓글)', () => {

  test('게스트 — 본인 댓글: ⋯ 메뉴 수정/삭제 노출 + 수정 후 삭제', async ({ page, request }) => {
    // 댓글 0개인 신규 포스트 생성 → 내 댓글이 첫 페이지에 단독 표시되도록 격리
    const postId = await createFreshPost(request)

    await page.goto(`${BASE}/community/${postId}/comments`)
    await page.waitForTimeout(GUEST_INIT_MS)

    // 댓글 작성
    const original = `수정삭제E2E-${Date.now()}`
    const bar = page.getByText(COMMENT_BAR_PLACEHOLDER)
    await expect(bar).toBeVisible({ timeout: 8_000 })
    await bar.click()
    const ta = page.locator('textarea')
    await expect(ta).toBeVisible({ timeout: 3_000 })
    await ta.fill(original)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()
    await expect(page.getByText(original)).toBeVisible({ timeout: 5_000 })

    // 내 댓글 카드 = 본문을 포함하는 comment-N 노드
    const card = page.locator('[id^="comment-"]').filter({ hasText: original }).first()

    // ⋯ 메뉴 열기 → 수정/삭제 노출, 신고는 없음
    await card.locator(COMMENT_MENU_TOGGLE).click()
    await expect(card.locator(COMMENT_MENU_EDIT)).toBeVisible({ timeout: 3_000 })
    await expect(card.locator(COMMENT_MENU_DELETE)).toBeVisible()

    // 수정 클릭 → 컴포즈 시트에 기존 본문이 채워진 "등록 직전" 화면
    await card.locator(COMMENT_MENU_EDIT).click()
    const editTa = page.locator('textarea')
    await expect(editTa).toBeVisible({ timeout: 3_000 })
    await expect(editTa).toHaveValue(original)

    // 본문 수정 후 등록 → 갱신된 본문 표시
    const edited = `${original}-수정완료`
    await editTa.fill(edited)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()
    await expect(page.getByText(edited)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText(original, { exact: true })).toHaveCount(0)

    // 삭제 — confirm 다이얼로그 자동 수락
    page.on('dialog', (d) => d.accept())
    const card2 = page.locator('[id^="comment-"]').filter({ hasText: edited }).first()
    await card2.locator(COMMENT_MENU_TOGGLE).click()
    await card2.locator(COMMENT_MENU_DELETE).click()
    await expect(page.getByText(edited)).toHaveCount(0, { timeout: 5_000 })
  })

  test('타인 댓글 — ⋯ 메뉴에 신고만 노출 (수정/삭제 없음)', async ({ page, request }) => {
    // 다른 게스트가 작성한 댓글이 있는 포스트
    const postId = await createFreshPost(request)
    const otherToken = await guestLogin(request, 'E2E타인')
    await request.post(`${BASE}/api/community/posts/${postId}/comments`, {
      headers: { Authorization: `Bearer ${otherToken}` },
      data: { body: '타인이 작성한 댓글' },
    })

    await page.goto(`${BASE}/community/${postId}/comments`)
    await page.waitForTimeout(GUEST_INIT_MS)

    const card = page.locator('[id^="comment-"]').filter({ hasText: '타인이 작성한 댓글' }).first()
    await expect(card).toBeVisible({ timeout: 8_000 })
    await card.locator(COMMENT_MENU_TOGGLE).click()
    // 신고만, 수정/삭제는 없어야 함
    await expect(card.locator('[data-testid="comment-menu-report"]')).toBeVisible({ timeout: 3_000 })
    await expect(card.locator(COMMENT_MENU_EDIT)).toHaveCount(0)
    await expect(card.locator(COMMENT_MENU_DELETE)).toHaveCount(0)
  })
})
