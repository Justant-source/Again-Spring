/**
 * Journey 05: 댓글 생명주기
 *
 * - 게스트 댓글 작성
 * - 본인 댓글 ⋯ 수정/삭제 (ConfirmDialog)
 * - 타인 댓글 — ⋯ 신고만 표시 (수정/삭제 없음)
 * - 첫 로드 중복 렌더 없음 (bd7589c 회귀 방지)
 */
import { test, expect } from '../support/no-llm-fixture'
import { createPost, guestLogin } from '../support/api'
import {
  COMMENT_BAR_PLACEHOLDER,
  COMMENT_SUBMIT_BTN,
  COMMENT_MENU_TOGGLE,
  COMMENT_MENU_EDIT,
  COMMENT_MENU_DELETE,
  COMMENT_MENU_REPORT,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── A. 댓글 추가 + 수정 + 삭제 ─────────────────────────────────
test.describe('Journey 05-A: 본인 댓글 수정·삭제', () => {

  test('게스트 — 댓글 작성 → 수정 → 삭제', async ({ page, request }) => {
    // 댓글 0개인 격리된 포스트 생성
    const token = await guestLogin(request, 'E2E작성자')
    const postId = await createPost(request, {
      token,
      title: 'E2E 댓글 수정삭제 포스트',
      body: 'e2e 댓글 수정·삭제 테스트용 사연 본문입니다. 충분한 길이.',
    })

    await page.goto(`${BASE}/community/${postId}/comments`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 8_000 })

    // 댓글 작성
    const original = `수정삭제E2E-${Date.now()}`
    const bar = page.getByText(COMMENT_BAR_PLACEHOLDER)
    await expect(bar).toBeVisible({ timeout: 10_000 })
    await bar.click()
    const ta = page.locator('textarea')
    await expect(ta).toBeVisible({ timeout: 5_000 })
    await ta.fill(original)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()
    await expect(page.getByText(original)).toBeVisible({ timeout: 8_000 })

    // 본인 댓글 카드
    const card = page.locator('[id^="comment-"]').filter({ hasText: original }).first()

    // ⋯ 메뉴 → 수정/삭제 노출, 신고 없음
    await card.locator(COMMENT_MENU_TOGGLE).click()
    await expect(card.locator(COMMENT_MENU_EDIT)).toBeVisible({ timeout: 3_000 })
    await expect(card.locator(COMMENT_MENU_DELETE)).toBeVisible()

    // 수정
    await card.locator(COMMENT_MENU_EDIT).click()
    const editTa = page.locator('textarea')
    await expect(editTa).toBeVisible({ timeout: 3_000 })
    await expect(editTa).toHaveValue(original)

    const edited = `${original}-수정완료`
    await editTa.fill(edited)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()
    await expect(page.getByText(edited)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText(original, { exact: true })).toHaveCount(0)

    // 삭제 — 커스텀 ConfirmDialog
    const card2 = page.locator('[id^="comment-"]').filter({ hasText: edited }).first()
    await card2.locator(COMMENT_MENU_TOGGLE).click()
    await card2.locator(COMMENT_MENU_DELETE).click()
    await page.getByRole('button', { name: '삭제' }).click()
    await expect(page.getByText(edited)).toHaveCount(0, { timeout: 5_000 })
  })
})

// ── B. 타인 댓글 — 신고만 ─────────────────────────────────────
test.describe('Journey 05-B: 타인 댓글 — 신고만 노출', () => {

  test('타인 댓글 ⋯ 메뉴 — 신고만 표시 (수정/삭제 없음)', async ({ page, request }) => {
    const authorToken = await guestLogin(request, 'E2E작성자')
    const postId = await createPost(request, {
      token: authorToken,
      title: 'E2E 타인 댓글 포스트',
      body: 'e2e 타인 댓글 테스트용 사연 본문입니다.',
    })

    // 타인(다른 게스트)이 댓글 작성
    const otherToken = await guestLogin(request, 'E2E타인')
    await request.post(`${BASE}/api/community/posts/${postId}/comments`, {
      headers: { Authorization: `Bearer ${otherToken}` },
      data: { body: '타인이 작성한 댓글' },
    })

    // 나(첫 번째 게스트와 다른 브라우저 세션)로 진입
    await page.goto(`${BASE}/community/${postId}/comments`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 8_000 })

    const card = page.locator('[id^="comment-"]').filter({ hasText: '타인이 작성한 댓글' }).first()
    await expect(card).toBeVisible({ timeout: 10_000 })
    await card.locator(COMMENT_MENU_TOGGLE).click()

    await expect(card.locator(COMMENT_MENU_REPORT)).toBeVisible({ timeout: 3_000 })
    await expect(card.locator(COMMENT_MENU_EDIT)).toHaveCount(0)
    await expect(card.locator(COMMENT_MENU_DELETE)).toHaveCount(0)
  })
})

// ── C. 첫 로드 중복 렌더 없음 ────────────────────────────────────
test.describe('Journey 05-C: 댓글 무한스크롤 중복 방지 (bd7589c 회귀)', () => {

  test('댓글 첫 로드 시 comment-N id 중복 없음', async ({ page }) => {
    // mock_001에 댓글 1건 이상 보장 (다른 테스트가 작성)
    await page.goto(`${BASE}/community/mock_001`)

    // 댓글 작성 (존재 보장)
    const bar = page.getByText(COMMENT_BAR_PLACEHOLDER)
    await expect(bar).toBeVisible({ timeout: 10_000 })
    await bar.click()
    const ta = page.locator('textarea')
    await expect(ta).toBeVisible({ timeout: 5_000 })
    await ta.fill(`중복방지-${Date.now()}`)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()

    // 새로고침 → 첫 로드 경로 재현
    await page.reload()
    await expect(page.getByText(COMMENT_BAR_PLACEHOLDER)).toBeVisible({ timeout: 12_000 })

    // 버그 발생 시: 첫 페이지 댓글 2배 중복 → id가 2개씩 존재
    const ids = await page
      .locator('[id^="comment-"]')
      .evaluateAll((els) => els.map((e) => e.id))
    expect(ids.length).toBeGreaterThanOrEqual(1)
    expect(new Set(ids).size).toBe(ids.length)
  })
})
