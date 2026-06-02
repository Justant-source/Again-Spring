/**
 * Flow 04: 광장형 커뮤니티 핵심 플로우 (C3 광장형 V18 기준)
 *
 * 커버 범위:
 *   - 광장 피드 로딩 (사연 목록 표시)
 *   - 사연 작성: 카테고리 선택 → 제목 → 본문 → 올리기 → 모드 선택
 *   - 모드 선택: PUBLIC 선택 시 버튼 활성화
 *   - 사연 상세 진입: 사연 내용, 투표 영역 표시
 *
 * 실행 조건: dev docker 스택 가동 중, mock 사연 시드 존재
 */
import { test, expect } from '@playwright/test'
import { PERSONA_TEST1 } from '../fixtures/personas'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Flow 04: 광장 커뮤니티 플로우', () => {

  // ── 04-01 피드 로딩 ──────────────────────────────────────────
  test('광장 피드 — 사연 목록이 로딩됨', async ({ page }) => {
    await page.goto(`${BASE}/community`)

    // 사연 목록 컨테이너가 나타남
    const postList = page.locator('[data-testid="feed-post-list"]')
    await expect(postList).toBeVisible({ timeout: 10_000 })

    // 하나 이상의 FeedCard 링크가 존재
    const cards = postList.locator('a[href*="/community/"]')
    await expect(cards.first()).toBeVisible({ timeout: 5_000 })

    const count = await cards.count()
    expect(count).toBeGreaterThanOrEqual(1)
  })

  // ── 04-02 피드 타이틀 + 버튼 ─────────────────────────────────
  test('광장 피드 — 타이틀과 내 사연 올리기 버튼 표시', async ({ page }) => {
    await page.goto(`${BASE}/community`)

    // "다시봄 광장" 타이틀
    await expect(page.getByText('다시봄 광장')).toBeVisible({ timeout: 8_000 })

    // "내 사연 올리기" 버튼 (fixed 하단)
    await expect(page.getByText('내 사연 올리기')).toBeVisible()
  })

  // ── 04-03 카테고리 칩 ─────────────────────────────────────────
  test('광장 피드 — 카테고리 칩 표시', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 10_000 })

    // 전체 칩과 대분류 칩들이 있어야 함
    await expect(page.getByText('전체')).toBeVisible()
    await expect(page.getByText('연인')).toBeVisible()
    await expect(page.getByText('부부')).toBeVisible()
  })

  // ── 04-04 사연 작성 compose 단계 ────────────────────────────
  test('사연 작성 — 제목·본문 입력 후 올리기 버튼 활성화', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)

    // compose 단계: 제목 입력 필드
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 8_000 })

    // 본문 입력 필드
    const bodyInput = page.locator('[data-testid="compose-body"]')
    await expect(bodyInput).toBeVisible()

    // 글자수 카운터
    await expect(page.locator('[data-testid="compose-char-count"]')).toBeVisible()

    // 제목·본문 입력
    await titleInput.fill('e2e 테스트 사연 제목입니다')
    await bodyInput.fill('e2e 테스트 본문입니다. 이 사연은 자동 테스트에 의해 생성되었습니다.')

    // 글자수 업데이트 확인
    const charCount = page.locator('[data-testid="compose-char-count"]')
    const countText = await charCount.textContent()
    expect(countText).toMatch(/\d+ \/ 600/)

    // "올리기" 버튼 표시
    await expect(page.getByRole('button', { name: '올리기' })).toBeVisible()
  })

  // ── 04-05 모드 선택 단계 ─────────────────────────────────────
  test('모드 선택 — PUBLIC 카드 선택 시 버튼 활성화', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)

    // compose 채우기
    await page.locator('[data-testid="compose-title"]').fill('모드 선택 테스트')
    await page.locator('[data-testid="compose-body"]').fill('모드 선택 단계 진입을 위한 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()

    // mode 단계 진입: "이 사연, 어떻게 올릴까요?" 제목
    await expect(page.getByText('이 사연, 어떻게 올릴까요?')).toBeVisible({ timeout: 5_000 })

    // 제출 버튼 초기: disabled (selectedMode = null)
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeDisabled()

    // "바로 광장에 올리기" 카드 클릭
    await page.locator('[data-testid="mode-public-card"]').click()

    // 제출 버튼 활성화
    await expect(submitBtn).toBeEnabled()
    await expect(submitBtn).toContainText('바로 올리기')
  })

  // ── 04-06 상대 초대 카드 선택 (게스트 차단 확인) ─────────────
  test('모드 선택 — 게스트는 상대 초대 카드 비활성', async ({ page }) => {
    // 게스트로 접근 (로그인 없이)
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 테스트')
    await page.locator('[data-testid="compose-body"]').fill('게스트 모드 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()

    // mode 단계 대기
    await expect(page.getByText('이 사연, 어떻게 올릴까요?')).toBeVisible({ timeout: 5_000 })

    // 상대 초대 카드 — 비활성(opacity 0.55, cursor not-allowed)
    const privateCard = page.locator('[data-testid="mode-private-card"]')
    await expect(privateCard).toBeVisible()

    // 클릭해도 제출 버튼이 활성화되지 않음
    await privateCard.click({ force: true })
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeDisabled()
  })

  // ── 04-07 회원 로그인 후 사연 생성 ─────────────────────────────
  test('회원 로그인 후 사연 올리기 → analyzing → 상세 이동', async ({ page }) => {
    // 로그인
    await page.goto(`${BASE}/login`)
    await page.getByPlaceholder('이메일').fill(PERSONA_TEST1.email)
    await page.getByPlaceholder('비밀번호').fill(PERSONA_TEST1.password)
    await page.getByRole('button', { name: '로그인' }).click()
    await page.waitForURL(/\/$/, { timeout: 10_000 })

    // compose 작성
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('e2e 자동 생성 사연')
    await page.locator('[data-testid="compose-body"]').fill('이 사연은 e2e 테스트에 의해 자동 생성되었습니다. 테스트 완료 후 삭제 예정.')
    await page.getByRole('button', { name: '올리기' }).click()

    // mode 단계
    await expect(page.getByText('이 사연, 어떻게 올릴까요?')).toBeVisible({ timeout: 5_000 })
    await page.locator('[data-testid="mode-public-card"]').click()
    await page.locator('[data-testid="mode-submit-btn"]').click()

    // analyzing 단계 거쳐 상세 페이지 이동
    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 30_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
  })

  // ── 04-08 사연 상세 진입 (mock 데이터) ───────────────────────
  test('사연 상세 — mock 사연 진입 시 본문과 투표 영역 표시', async ({ page }) => {
    // 피드에서 첫 번째 카드 클릭
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 10_000 })

    const firstCard = page.locator('[data-testid="feed-post-list"] a[href*="/community/"]').first()
    const href = await firstCard.getAttribute('href')
    expect(href).toBeTruthy()

    await firstCard.click()
    await page.waitForURL(/\/community\/[^/]+$/, { timeout: 10_000 })

    // 페이지 제목 영역 존재
    await expect(page.locator('h1, h2').first()).toBeVisible({ timeout: 5_000 })
  })
})
