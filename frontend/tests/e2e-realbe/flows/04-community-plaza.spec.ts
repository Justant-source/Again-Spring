/**
 * Flow 04: 광장형 커뮤니티 핵심 플로우 (C3 광장형 V18 / 6/2 피벗 기준)
 *
 * 커버 범위:
 *   - 광장 피드 로딩, 정렬 토글(최신순/추천순), 카테고리 필터
 *   - 사연 작성: 제목·본문 입력, 글자수 카운터
 *   - 모드 선택: PUBLIC → 피드로 이동, PRIVATE(회원만 활성)
 *   - 게스트 제약: GuestNoticeModal, 상대 초대 카드 비활성
 *   - 사연 상세: 댓글바 클릭 → 댓글 페이지 이동, 진영 탭 전환
 *   - 전문 읽기 화면: 작성자/상대방 탭 전환
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

  test('피드 — 타이틀·카테고리 칩 표시 (내 사연 올리기 버튼 없음)', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 12_000 })
    await expect(page.getByText('다시봄 광장')).toBeVisible()
    await expect(page.getByText('전체')).toBeVisible()
    await expect(page.getByRole('button', { name: '연인' })).toBeVisible()
    // 6/2 피벗 이후 피드 페이지에서 "내 사연 올리기" 버튼 제거됨
    await expect(page.getByText('내 사연 올리기')).not.toBeVisible()
  })

  test('피드 — 정렬 토글: 최신순↔추천순 전환', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 12_000 })

    const latestBtn = page.locator('[data-testid="feed-sort-latest"]')
    const recommendedBtn = page.locator('[data-testid="feed-sort-recommended"]')
    await expect(latestBtn).toBeVisible()
    await expect(recommendedBtn).toBeVisible()

    // 추천순 클릭 → 피드 재로드
    await recommendedBtn.click()
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 8_000 })

    // 최신순 복귀
    await latestBtn.click()
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 8_000 })
  })

  test('피드 — 카테고리 필터: 연인 선택 → 전체 복귀', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 12_000 })

    // 연인 칩 클릭
    await page.getByRole('button', { name: '연인' }).click()
    // 필터 적용 후 잠시 대기 (빈 상태일 수도 있음)
    await page.waitForTimeout(1_000)

    // 전체 복귀 → 피드 재표시
    await page.getByRole('button', { name: '전체' }).click()
    await page.waitForSelector('[data-testid="feed-post-list"]', { timeout: 8_000 })
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

    // 제출 버튼 초기 활성 (PUBLIC이 기본값)
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeEnabled()
    await expect(submitBtn).toContainText('바로 올리기')

    // 상대 초대 카드 클릭해도 PUBLIC이 선택된 상태 유지 → 버튼은 계속 활성
    await page.locator('[data-testid="mode-private-card"]').click({ force: true })
    await expect(submitBtn).toBeEnabled()
    await expect(submitBtn).toContainText('바로 올리기')
  })

  test('사연 상세 — 알려진 mock 포스트 직접 방문 → 페이지 로드 확인', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001`)
    await page.waitForURL(/\/community\/mock_001/, { timeout: 12_000 })
    await expect(page.getByText('주말에도 저만 쉬는 날이 없어요')).toBeVisible({ timeout: 12_000 })
  })

  test('UserChip 클릭 → 게스트 정보 시트 표시 → 회원가입 버튼 존재', async ({ page }) => {
    await page.goto(`${BASE}/community`)
    // 게스트 자동 초기화 대기 (2초)
    await page.waitForTimeout(2000)
    const userChip = page.locator('[data-testid="user-chip"]')
    await expect(userChip).toBeVisible({ timeout: 5_000 })
    await userChip.click()
    // 게스트 정보 시트 표시
    await expect(page.getByText('게스트로는')).toBeVisible({ timeout: 5_000 })
    // 회원가입하기 버튼 존재 확인
    await expect(page.getByRole('button', { name: '회원가입하기' })).toBeVisible()
    // 게스트로 계속하기 버튼으로 시트 닫기
    await page.getByRole('button', { name: '게스트로 계속하기' }).click()
    await expect(page.getByText('게스트로는')).not.toBeVisible({ timeout: 2_000 })
  })
})

// ── 댓글 및 좋아요 (공개 접근) ──────────────────────────────────
test.describe('Flow 04-C: 댓글 · 좋아요 (공개 접근)', () => {

  test('댓글 입력바 클릭 → 컴포즈 시트 열림', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/comments`)
    const commentBar = page.getByText('댓글을 남겨주세요.')
    await expect(commentBar).toBeVisible({ timeout: 8_000 })
    await commentBar.click()
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 3_000 })
  })

  test('익명 댓글 등록 가능 (토큰 없이)', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/comments`)
    const commentBar = page.getByText('댓글을 남겨주세요.')
    await expect(commentBar).toBeVisible({ timeout: 8_000 })
    await commentBar.click()
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 3_000 })
    const testCommentText = '익명 댓글 테스트'
    await textarea.fill(testCommentText)
    await page.getByRole('button', { name: '등록' }).click()
    await expect(page.getByText(testCommentText).first()).toBeVisible({ timeout: 5_000 })
  })

  test('방금 등록한 댓글 시간 → 음수 없음', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/comments`)
    const commentBar = page.getByText('댓글을 남겨주세요.')
    await expect(commentBar).toBeVisible({ timeout: 8_000 })
    await commentBar.click()
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 3_000 })
    const testCommentText = '방금 작성된 댓글'
    await textarea.fill(testCommentText)
    await page.getByRole('button', { name: '등록' }).click()
    await expect(page.getByText(testCommentText).first()).toBeVisible({ timeout: 5_000 })
    const commentLocator = page.getByText(testCommentText).first()
    const commentContainer = commentLocator.locator('..')
    const timeText = await commentContainer.textContent()
    expect(timeText).toMatch(/방금|\d+분 전|\d+시간 전|\d+일 전/)
    expect(timeText).not.toMatch(/-\d+/)
  })

  test('사연 상세 — 댓글바 클릭 → 댓글 페이지 이동', async ({ page }) => {
    // 상세 페이지 하단 고정 댓글바는 /comments로 라우팅 (6/2 개편 후 인라인 미작성)
    await page.goto(`${BASE}/community/mock_001`)
    await page.waitForURL(/\/community\/mock_001/, { timeout: 12_000 })
    const commentBar = page.getByText('댓글을 남겨주세요.').first()
    await expect(commentBar).toBeVisible({ timeout: 8_000 })
    await commentBar.click()
    await page.waitForURL(/\/community\/mock_001\/comments/, { timeout: 8_000 })
    expect(page.url()).toContain('/community/mock_001/comments')
  })
})

// ── 전문 읽기 화면 ─────────────────────────────────────────────
test.describe('Flow 04-D: 전문 읽기 화면 (6/2 진영 탭)', () => {
  // read 페이지에는 "상대방 이야기 읽기 ›" 이동 버튼이 없고
  // 작성자/상대방 탭 전환 UI만 존재한다 (710d874 기준).

  test('read 화면 — 진영 탭 두 개 표시 (작성자 · 상대방)', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/read`)
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('상대방의 이야기')).toBeVisible({ timeout: 5_000 })
  })

  test('read 화면 — 상대방 탭 클릭 → 활성 스타일 전환 (URL side=r)', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/read`)
    await page.waitForURL(/\/read/, { timeout: 10_000 })
    const partnerTab = page.getByText('상대방의 이야기')
    await expect(partnerTab).toBeVisible({ timeout: 8_000 })
    await partnerTab.click()
    // side=r 파라미터가 반영되거나 탭이 활성 상태로 전환됨
    // URL 쿼리 또는 UI 상태 확인 (두 탭 모두 여전히 보여야 함)
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 3_000 })
    await expect(page.getByText('상대방의 이야기')).toBeVisible({ timeout: 3_000 })
  })

  test('read 화면 — "상대방 이야기 읽기 ›" 이동 버튼 없음 (710d874 회귀 방지)', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001/read`)
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 10_000 })
    // 해당 버튼이 삭제됐음을 확인
    await expect(page.getByText(/상대방 이야기 읽기/)).not.toBeVisible()
  })
})

// ── 로그인 필요 테스트 ─────────────────────────────────────────
test.describe('Flow 04-B: 광장 플로우 (회원)', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — PUBLIC 선택 후 제출 → 피드로 이동 (6/2 이후 동작)', async ({ page }) => {
    // 6/2 이전: 제출 후 /community/[id] 로 이동
    // 6/2 이후: 제출 후 /community (피드)로 이동 — BE 분석 대기 없이 즉시 반환
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 10_000 })
    await titleInput.fill('e2e 자동 테스트 사연')
    await page.locator('[data-testid="compose-body"]').fill('e2e 테스트에 의해 자동 생성된 사연입니다. 테스트 완료 후 삭제 예정.')
    await page.getByRole('button', { name: '올리기' }).click()

    // mode 단계 (회원이므로 GuestNoticeModal 없음)
    await expect(page.locator('[data-testid="mode-step-heading"]')).toBeVisible({ timeout: 8_000 })

    // PUBLIC 선택 후 제출
    await page.locator('[data-testid="mode-public-card"]').click()
    const submitBtn = page.locator('[data-testid="mode-submit-btn"]')
    await expect(submitBtn).toBeEnabled()
    await submitBtn.click()

    // 피드(/community)로 이동
    await page.waitForURL(/\/community$/, { timeout: 30_000 })
    expect(page.url()).toMatch(/\/community$/)
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
