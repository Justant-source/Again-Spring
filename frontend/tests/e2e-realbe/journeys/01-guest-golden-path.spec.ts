/**
 * Journey 01: 게스트 최초 여정 (골든 패스)
 *
 * 시나리오: 처음 방문한 게스트가 /guest 진입 → 커뮤니티 광장 → 사연 열람.
 * 투표/댓글은 Journey 04/05에 위임 (중복 제거).
 *
 * @mobile 태그: 랜딩→피드 흐름은 모바일(Pixel 5)에서도 함께 검증.
 * guest-golden-path는 chromium만 전체 실행 (GuestSessionRateLimiter 소진 방지).
 */
import { test, expect } from '../support/no-llm-fixture'
import {
  GUEST_START_BUTTON,
  FEED_POST_LIST,
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
