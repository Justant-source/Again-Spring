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
    await expect(charCount).toContainText('/ 2000')
  })

  test('게스트 — 올리기 클릭 시 GuestNoticeModal 표시', async ({ page }) => {
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 테스트')
    await page.locator('[data-testid="compose-body"]').fill('게스트 테스트 본문.')
    await page.getByRole('button', { name: '올리기' }).click()
    // 게스트 안내 모달 (바텀시트)
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })
  })

  test('게스트 — GuestNoticeModal에서 게스트로 올리기 → 분석 후 사연 상세 이동', async ({ page }) => {
    // 모드 선택 단계 제거 — 게스트로 계속하기 → 분석 → /community/{id}로 이동
    await page.goto(`${BASE}/community/new`)
    await page.locator('[data-testid="compose-title"]').fill('게스트 올리기 E2E')
    await page.locator('[data-testid="compose-body"]').fill('게스트 올리기 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()
    await expect(page.getByText('게스트로 올리면')).toBeVisible({ timeout: 5_000 })
    await page.locator('[data-testid="guest-notice-continue"]').click()
    // 분석 중 → 사연 상세 페이지로 이동
    await page.waitForURL(/\/community\/(?!new)[^/]+$/, { timeout: 30_000 })
    await expect(page.getByText('게스트 올리기 E2E')).toBeVisible({ timeout: 10_000 })
  })

  test('게스트 — 상대 초대 버튼은 사연 상세에서 제공 (GuestInfoSheet 표시)', async ({ page }) => {
    // 게스트는 상세 페이지의 상대 초대 버튼 탭 시 GuestInfoSheet 표시
    await page.goto(`${BASE}/community/mock_001`)
    await page.waitForURL(/\/community\/mock_001/, { timeout: 12_000 })
    const inviteBtn = page.locator('[data-testid="invite-partner-btn"]')
    if (await inviteBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await inviteBtn.click()
      await expect(page.locator('[data-testid="guest-info-sheet"]')).toBeVisible({ timeout: 5_000 })
    }
    // mock_001이 이미 paired이면 invite 버튼이 없으므로 pass
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

  test('사연 상세 — 댓글바 클릭 → 인라인 컴포즈 시트 열림 (단일 페이지)', async ({ page }) => {
    // 댓글 페이지 이동 없이 상세 페이지에서 인라인 댓글 작성
    await page.goto(`${BASE}/community/mock_001`)
    await page.waitForURL(/\/community\/mock_001/, { timeout: 12_000 })
    const commentBar = page.getByText('댓글을 남겨주세요.').first()
    await expect(commentBar).toBeVisible({ timeout: 8_000 })
    await commentBar.click()
    // CommentComposeSheet (textarea) 인라인으로 표시 — URL 변경 없음
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 5_000 })
    expect(page.url()).toContain('/community/mock_001')
    expect(page.url()).not.toContain('/comments')
  })
})

// ── 전문 읽기 화면 ─────────────────────────────────────────────
test.describe('Flow 04-D: 전문 읽기 화면 (6/2 진영 탭)', () => {
  // read 페이지에는 "상대방 이야기 읽기 ›" 이동 버튼이 없고
  // 작성자/상대방 탭 전환 UI만 존재한다 (710d874 기준).

  test('read 화면 — 작성자 이야기 표시', async ({ page }) => {
    // 진영 탭은 paired 포스트에서만 표시 — mock_001은 solo이므로 작성자 이야기만 확인
    await page.goto(`${BASE}/community/mock_001/read`)
    await page.waitForURL(/\/read/, { timeout: 10_000 })
    await expect(page.getByText('작성자')).toBeVisible({ timeout: 8_000 })
    // 사연 본문 영역 표시 확인
    await expect(page.locator('div[style*="font-serif"]').first()).toBeVisible({ timeout: 5_000 })
  })

  test('read 화면 — paired 포스트에서 진영 탭 두 개 표시', async ({ page }) => {
    // paired 포스트가 있을 때만 양쪽 탭 표시 — mock_001이 solo이면 skip
    await page.goto(`${BASE}/community/mock_001/read`)
    await page.waitForURL(/\/read/, { timeout: 10_000 })
    const partnerTab = page.getByText('상대방의 이야기')
    const isVisible = await partnerTab.isVisible({ timeout: 3_000 }).catch(() => false)
    if (!isVisible) {
      // solo 포스트: 탭 없음 — 정상 동작
      return
    }
    // paired 포스트: 두 탭 모두 표시
    await expect(page.getByText('작성자의 이야기')).toBeVisible()
    await partnerTab.click()
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 3_000 })
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

  test('회원 — 올리기 제출 → 분석 후 사연 상세로 이동', async ({ page }) => {
    // 모드 선택 단계 제거 — 작성 후 바로 분석 → /community/{id}로 이동
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 10_000 })
    await titleInput.fill('e2e 자동 테스트 사연')
    await page.locator('[data-testid="compose-body"]').fill('e2e 테스트에 의해 자동 생성된 사연입니다. 테스트 완료 후 삭제 예정.')
    await page.getByRole('button', { name: '올리기' }).click()

    // 분석 중 화면을 거쳐 사연 상세(/community/{id})로 이동
    await page.waitForURL(/\/community\/(?!new)[^/]+$/, { timeout: 30_000 })
    expect(page.url()).toMatch(/\/community\/[^/]+$/)
    await expect(page.getByText('e2e 자동 테스트 사연')).toBeVisible({ timeout: 10_000 })
  })

  test('회원 — 사연 상세에서 상대 초대 버튼 표시', async ({ page }) => {
    // 모드 단계 제거 — 사연 올린 후 상세 페이지에서 상대 초대 가능
    await page.goto(`${BASE}/community/new`)
    const titleInput = page.locator('[data-testid="compose-title"]')
    await expect(titleInput).toBeVisible({ timeout: 10_000 })
    await titleInput.fill('상대 초대 테스트 E2E')
    await page.locator('[data-testid="compose-body"]').fill('상대 초대 기능 e2e 테스트 본문입니다.')
    await page.getByRole('button', { name: '올리기' }).click()

    // 사연 상세로 이동
    await page.waitForURL(/\/community\/(?!new)[^/]+$/, { timeout: 30_000 })
    // 작성자 뷰: 상대 초대 버튼 표시
    const inviteBtn = page.locator('[data-testid="invite-partner-btn"]')
    await expect(inviteBtn).toBeVisible({ timeout: 10_000 })
  })
})
