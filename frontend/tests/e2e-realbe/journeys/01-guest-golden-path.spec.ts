/**
 * Journey 01: 게스트 최초 여정 (골든 패스)
 *
 * 시나리오: 처음 방문한 게스트가 /guest 진입 → 커뮤니티 광장 → 사연 열람 → 투표 → 댓글 작성.
 * 전부 UI 조작. storageState 없음(게스트 자동 발급).
 *
 * @mobile 태그: 랜딩→피드 흐름은 모바일(Pixel 5)에서도 함께 검증.
 * guest-golden-path는 chromium만 전체 실행 (GuestSessionRateLimiter 소진 방지).
 */
import { test, expect } from '../support/no-llm-fixture'
import {
  GUEST_START_BUTTON,
  FEED_POST_LIST,
  STORY_VOTE_BTN,
  VOTE_COMPLETE_BADGE,
  COMMENT_BAR_PLACEHOLDER,
  COMMENT_SUBMIT_BTN,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

// ── A. 게스트 진입 ────────────────────────────────────────────────
test.describe('Journey 01-A: 게스트 /guest 진입', () => {

  test('@mobile 게스트 닉네임 입력 → 커뮤니티 광장 이동 + 토큰 발급', async ({ page }) => {
    await page.goto(`${BASE}/guest`)

    const input = page.locator('input[type="text"]').first()
    await expect(input).toBeVisible({ timeout: 8_000 })
    await input.fill('E2E테스트게스트')
    await page.getByRole('button', GUEST_START_BUTTON).click()

    await page.waitForURL(/\/community/, { timeout: 10_000 })
    expect(page.url()).toContain('/community')

    const token = await page.evaluate(() => localStorage.getItem('again-spring-token'))
    expect(token).toBeTruthy()
  })

  test('게스트 자동 발급 — 커뮤니티 진입만으로 토큰 생성', async ({ page }) => {
    // /guest를 거치지 않고 /community 직접 접근 → useGuestInit()이 토큰 자동 발급
    await page.goto(`${BASE}/community`)
    await expect(page.locator(FEED_POST_LIST)).toBeVisible({ timeout: 12_000 })

    const token = await page.evaluate(() => localStorage.getItem('again-spring-token'))
    expect(token).toBeTruthy()
  })
})

// ── B. 광장 피드 열람 ─────────────────────────────────────────────
test.describe('Journey 01-B: 광장 피드 열람', () => {

  test('@mobile 게스트 — 피드에서 사연 카드 클릭 → 상세 이동', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    const postList = page.locator(FEED_POST_LIST)
    await expect(postList).toBeVisible({ timeout: 12_000 })

    // 첫 번째 사연 카드 클릭
    const firstCard = postList.locator('a[href*="/community/"]').first()
    await expect(firstCard).toBeVisible({ timeout: 5_000 })
    await firstCard.click()

    // 사연 상세 페이지로 이동
    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 10_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
  })
})

// ── C. 투표 ──────────────────────────────────────────────────────
test.describe('Journey 01-C: 게스트 투표', () => {

  test('게스트 — mock_001 사연에서 작성자 쪽 투표 → 완료 배지 표시', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001`)
    // useGuestInit 완료 대기 (USER_CHIP 대신 투표 버튼 가시성으로 확인)
    const voteG = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteG).toBeVisible({ timeout: 12_000 })
    await voteG.click()

    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 8_000 })
  })
})

// ── D. 댓글 작성 ─────────────────────────────────────────────────
test.describe('Journey 01-D: 게스트 댓글 작성', () => {

  test('게스트 — 사연 상세에서 댓글 작성 → 댓글 목록에 표시', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001`)

    const commentText = `게스트댓글-${Date.now()}`

    const bar = page.getByText(COMMENT_BAR_PLACEHOLDER)
    await expect(bar).toBeVisible({ timeout: 12_000 })
    await bar.click()

    const ta = page.locator('textarea')
    await expect(ta).toBeVisible({ timeout: 5_000 })
    await ta.fill(commentText)
    await page.getByRole('button', COMMENT_SUBMIT_BTN).click()

    await expect(page.getByText(commentText)).toBeVisible({ timeout: 8_000 })
  })
})
