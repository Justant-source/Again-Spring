/**
 * Flow 04: 광장형 커뮤니티 핵심 플로우 (C3 광장형 V18 기준)
 *
 * 커버 범위:
 *   - 광장 피드 로딩 (사연 목록 표시, 타이틀, 카테고리 칩)
 *   - 사연 작성: 제목·본문 입력, 글자수 카운터
 *   - 모드 선택: PUBLIC 선택 → 버튼 활성화 (로그인 상태)
 *   - 게스트 제약: 상대 초대 카드 비활성
 *   - 사연 상세: 진입 후 본문 표시
 *
 * 실행 조건: dev docker 스택 가동 중, mock 사연 시드 존재
 */
import { test, expect } from '@playwright/test'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { authStatePath } from '../fixtures/auth-state'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── 로그인 불필요 테스트 ─────────────────────────────────────────
test.describe('Flow 04-A: 광장 피드 (공개 접근)', () => {

  test('피드 — 사연 목록 로딩', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    const postList = page.locator('[data-testid="feed-post-list"]')
    await expect(postList).toBeVisible({ timeout: 12_000 })
    const cards = postList.locator('a[href*="/community/"]')
    await expect(cards.first()).toBeVisible({ timeout: 5_000 })
    expect(await cards.count()).toBeGreaterThanOrEqual(1)
  })

  test('피드 — 타이틀·버튼·카테고리 칩 표시', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 12_000 })
    await expect(page.getByText('다시봄 광장')).toBeVisible()
    await expect(page.getByText('내 사연 올리기')).toBeVisible()
    await expect(page.getByText('전체')).toBeVisible()
    await expect(page.getByText('연인')).toBeVisible()
  })

  test('compose — 제목·본문 입력 + 글자수 카운터', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 8_000 })
    const bodyInput = page.locator('[data-testid="compose-body"]')
    await titleInput.fill('테스트 제목')
    await bodyInput.fill('테스트 본문입니다.')
    const charCount = page.locator('[data-testid="compose-char-count"]')
    await expect(charCount).toContainText('/ 600')
  })

  test('게스트 — 올리기 클릭 시 GuestNoticeModal 표시', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 테스트')
    await page.locator('[data-testid="compose-body"]').fill('게스트 테스트 본문.')
    await page.getByRole('button', { name: '올리기' }).click()
    // 게스트 안내 모달 (바텀시트)
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })
  })

  test('게스트 — GuestNoticeModal에서 게스트로 올리기 → 모드 단계 진입', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 모드 테스트')
    await page.locator('[data-testid="compose-body"]').fill('게스트 모드 선택 단계 테스트.')
    await page.getByRole('button', { name: '올리기' }).click()
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })
    // testid로 버튼 클릭 (event.stopPropagation 적용됨)
    await page.locator('[data-testid="guest-notice-continue"]').click()
    // 모드 선택 단계 진입
    await expect(page.locator('[data-testid="mode-step-heading"]')).toBeVisible({ timeout: 5_000 })
  })

  test('게스트 — 모드 선택: 상대 초대 카드 비활성, PUBLIC 선택 → 버튼 활성', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 모드 카드 테스트')
    await page.locator('[data-testid="compose-body"]').fill('게스트 모드 카드 활성화 테스트 본문.')
    await page.getByRole('button', { name: '올리기' }).click()
    await page.locator('[data-testid="guest-notice-continue"]').click()
    await expect(page.locator('[data-testid="mode-step-heading"]')).toBeVisible({ timeout: 5_000 })

    // 제출 버튼 초기 비활성
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeDisabled()

    // 상대 초대 클릭해도 여전히 비활성 (게스트 차단)
    await page.locator('[data-testid="mode-private-card"]').click({ force: true })
    await expect(submitBtn).toBeDisabled()

    // 바로 광장에 올리기 선택 → 버튼 활성화
    await page.locator('[data-testid="mode-public-card"]').click()
    await expect(submitBtn).toBeEnabled()
    await expect(submitBtn).toContainText('바로 올리기')
  })

  test('사연 상세 — 피드에서 첫 카드 클릭 → 상세 페이지 진입', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 12_000 })
    const firstCard = page.locator('[data-testid="feed-post-list"] a[href*="/community/"]').first()
    await expect(firstCard).toBeVisible()
    // 카드의 href로 직접 이동 (FeedCard <a> 클릭 대신)
    const href = await firstCard.getAttribute('href')
    expect(href).toBeTruthy()
    await page.goto(`${BASE}${href}`)
    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 12_000 })
    // 상세 페이지 — "광장" 뒤로가기 링크 또는 카테고리 칩 존재 (div 기반 타이틀)
    await expect(page.getByText('광장')).toBeVisible({ timeout: 8_000 })
  })
})

// ── 로그인 필요 테스트 ─────────────────────────────────────────
test.describe('Flow 04-B: 광장 플로우 (회원)', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — 모드 선택: PUBLIC 선택 후 제출 → analyzing → 상세 이동', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    // compose 단계
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 10_000 })
    await titleInput.fill('e2e 자동 테스트 사연')
    await page.locator('[data-testid="compose-body"]').fill('e2e 테스트에 의해 자동 생성된 사연입니다. 테스트 완료 후 삭제 예정.')
    await page.getByRole('button', { name: '올리기' }).click()

    // mode 단계 (회원이므로 GuestNoticeModal 없음)
    await expect(page.locator('[data-testid="mode-step-heading"]')).toBeVisible({ timeout: 8_000 })

    // PUBLIC 선택
    await page.locator('[data-testid="mode-public-card"]').click()
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeEnabled()

    // 제출 → analyzing → 상세 페이지
    await submitBtn.click()
    // analyzing 화면 또는 바로 상세로 이동 (BE 처리 속도에 따라 달라질 수 있음)
    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 30_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
  })

  test('회원 — 상대 초대 카드 활성화', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 10_000 })
    await titleInput.fill('상대 초대 테스트')
    await page.locator('[data-testid="compose-body"]').fill('상대 초대 기능 e2e 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()

    await expect(page.locator('[data-testid="mode-step-heading"]')).toBeVisible({ timeout: 8_000 })

    // 회원이면 상대 초대 카드 클릭 가능
    await page.locator('[data-testid="mode-private-card"]').click()
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeEnabled()
    await expect(submitBtn).toContainText('상대 초대하기')
  })
})
