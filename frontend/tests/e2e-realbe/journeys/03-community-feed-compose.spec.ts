/**
 * Journey 03: 광장 피드 + 사연 작성
 *
 * - 피드 목록 로드 / 정렬 토글 / 카테고리 필터 (단일 세션)
 * - 작성 폼: 제목·본문 입력, 글자수 카운터
 * - 게스트 작성 → GuestNoticeModal → 게스트로 올리기
 * - 회원 작성 → 사연 상세 이동 확인
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import {
  FEED_POST_LIST,
  FEED_SORT_LATEST,
  FEED_SORT_RECOMMENDED,
  COMPOSE_TITLE,
  COMPOSE_BODY,
  COMPOSE_CHAR_COUNT,
  GUEST_NOTICE_CONTINUE,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── A. 피드 공개 열람 (단일 goto) ────────────────────────────────
test.describe('Journey 03-A: 광장 피드 공개 열람', () => {

  test('@mobile 피드 — 목록·헤더·정렬·카테고리', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    const postList = page.locator(FEED_POST_LIST)
    await expect(postList).toBeVisible({ timeout: 12_000 })
    const cards = postList.locator('a[href*="/community/"]')
    await expect(cards.first()).toBeVisible({ timeout: 5_000 })
    expect(await cards.count()).toBeGreaterThanOrEqual(1)

    await expect(page.getByText('다시봄 광장')).toBeVisible()
    await expect(page.getByRole('button', { name: '전체' })).toBeVisible()
    await expect(page.getByRole('button', { name: '연인' })).toBeVisible()
    await expect(page.getByText('내 사연 올리기')).not.toBeVisible()

    const latestBtn = page.locator(FEED_SORT_LATEST)
    const recommendedBtn = page.locator(FEED_SORT_RECOMMENDED)
    await expect(latestBtn).toBeVisible()
    await expect(recommendedBtn).toBeVisible()
    await recommendedBtn.click()
    await expect(page.locator(FEED_POST_LIST)).toBeVisible({ timeout: 8_000 })
    await latestBtn.click()
    await expect(page.locator(FEED_POST_LIST)).toBeVisible({ timeout: 8_000 })

    await page.getByRole('button', { name: '연인' }).click()
    await expect(page.locator(FEED_POST_LIST)).toBeVisible({ timeout: 5_000 })
    await page.getByRole('button', { name: '전체' }).click()
    await expect(page.locator(FEED_POST_LIST)).toBeVisible({ timeout: 8_000 })
  })
})

// ── B. 작성 폼 UI ────────────────────────────────────────────────
test.describe('Journey 03-B: 작성 폼 UI', () => {

  test('compose — 제목·본문 입력 + 글자수 카운터', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator(COMPOSE_TITLE)
    await expect(titleInput).toBeVisible({ timeout: 8_000 })
    await titleInput.fill('테스트 제목')
    await page.locator(COMPOSE_BODY).fill('테스트 본문입니다.')
    await expect(page.locator(COMPOSE_CHAR_COUNT)).toContainText('/ 2000')
  })

  test('게스트 — 올리기 클릭 시 GuestNoticeModal 표시', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator(COMPOSE_TITLE).fill('게스트 테스트 제목')
    await page.locator(COMPOSE_BODY).fill('게스트 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })
  })

  test('게스트 — GuestNoticeModal에서 "게스트로 올리기" → 사연 상세 이동', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator(COMPOSE_TITLE).fill('게스트 올리기 E2E')
    await page.locator(COMPOSE_BODY).fill('게스트 올리기 테스트 본문입니다. 충분한 길이.')
    await page.getByRole('button', { name: '올리기' }).click()
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })

    const continueBtn = page.locator(GUEST_NOTICE_CONTINUE)
    if (await continueBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await continueBtn.click()
    } else {
      await page.getByRole('button', { name: '게스트로 계속하기' }).click()
    }

    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 20_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
  })
})

// ── C. 회원 작성 ─────────────────────────────────────────────────
test.describe('Journey 03-C: 회원 사연 작성', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — 사연 작성 → 사연 상세 페이지 이동 확인', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await expect(page.locator(COMPOSE_TITLE)).toBeVisible({ timeout: 8_000 })
    await page.locator(COMPOSE_TITLE).fill('회원 작성 E2E 테스트')
    await page.locator(COMPOSE_BODY).fill('회원이 작성한 e2e 테스트 사연 본문입니다. 충분한 길이.')
    await page.getByRole('button', { name: '올리기' }).click()

    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 20_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
  })
})
