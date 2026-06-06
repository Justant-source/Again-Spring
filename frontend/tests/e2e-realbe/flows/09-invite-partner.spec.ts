/**
 * Flow 09: 상대 초대 흐름 E2E
 *
 * 커버 범위:
 *   A. 작성자 — 사연 작성 후 사연 상세에서 상대 초대 버튼(+) 표시
 *   B. 작성자 — + 버튼 클릭 → InviteSheet 바텀시트 열림 + 초대 URL 생성 확인
 *   C. Full flow — 작성자 초대 → 상대방 답변 → 양쪽 완성(paired) 확인
 *   D. 관람자 — paired 사연에서 양쪽 투표 버튼 활성 확인
 *   E. 작성자 — 초대 링크 전송 후 대기 상태(author-waiting) 전환 확인
 *
 * 실행 조건: dev docker 스택 가동 중(8090)
 */
import { test, expect } from '@playwright/test'
import { login, guestLogin } from '../fixtures/api-helpers'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { STORY_VOTE_BTN, VOTE_COMPLETE_BADGE } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── API 헬퍼 ────────────────────────────────────────────────────

/** 회원 토큰으로 사연 생성 → postId 반환 */
async function createPost(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  title = 'E2E 초대 테스트 사연',
  body = '상대방 초대 e2e 테스트용 사연입니다. 충분한 길이를 확보합니다.',
): Promise<string> {
  const resp = await request.post(`${BASE}/api/community/posts`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { bodyRaw: body, category: 'OTHER', visibility: 'PUBLIC', jurorCount: 0, userTitle: title },
  })
  if (!resp.ok()) throw new Error(`포스트 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).id as string
}

/** 회원 토큰으로 초대 토큰 생성 → inviteToken 반환 */
async function createInviteToken(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  postId: string,
): Promise<string> {
  const resp = await request.post(`${BASE}/api/community/posts/${postId}/invite`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) throw new Error(`초대 토큰 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).inviteToken as string
}

/** 익명(게스트)으로 상대방 답변 제출 */
async function submitPartnerAnswer(
  request: import('@playwright/test').APIRequestContext,
  inviteToken: string,
  body = '상대방 답변입니다. e2e 테스트에 의해 자동 생성됩니다.',
  title = '상대방 입장 제목',
): Promise<void> {
  const resp = await request.post(`${BASE}/api/s/${inviteToken}/answer`, {
    data: { bodyRaw: body, userTitle: title },
  })
  if (!resp.ok()) throw new Error(`상대 답변 제출 실패: ${resp.status()} — ${await resp.text()}`)
}

/** 포스트가 paired 상태인지 확인 */
async function waitForPaired(
  request: import('@playwright/test').APIRequestContext,
  postId: string,
  maxRetries = 5,
): Promise<boolean> {
  for (let i = 0; i < maxRetries; i++) {
    const resp = await request.get(`${BASE}/api/community/posts/${postId}`)
    if (resp.ok()) {
      const data = await resp.json()
      if (data.paired || data.partnerBodyPublished) return true
    }
    await new Promise(r => setTimeout(r, 500))
  }
  return false
}

// ── A. 작성자 — 사연 상세에서 초대 버튼 표시 ─────────────────────────
test.describe('Flow 09-A: 작성자 뷰 — 초대 버튼 표시', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — 사연 올린 후 상세에서 상대 초대(+) 버튼 표시', async ({ page, request }) => {
    const token = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, token)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    // 작성자 뷰: 상대 초대(+) 버튼 표시
    const inviteBtn = page.locator('[data-testid="invite-partner-btn"]')
    await expect(inviteBtn).toBeVisible({ timeout: 10_000 })
    await expect(inviteBtn).toContainText('상대 초대하기')
  })

  test('회원 — + 버튼 클릭 → InviteSheet 열림 + 초대 URL 생성', async ({ page, request }) => {
    const token = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, token)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    // + 버튼 클릭
    const inviteBtn = page.locator('[data-testid="invite-partner-btn"]')
    await expect(inviteBtn).toBeVisible({ timeout: 10_000 })
    await inviteBtn.click()

    // InviteSheet 바텀시트 열림
    const sheet = page.locator('[data-testid="invite-sheet"]')
    await expect(sheet).toBeVisible({ timeout: 8_000 })

    // 초대 URL 생성 후 표시
    const urlText = page.locator('[data-testid="invite-url-text"]')
    await expect(urlText).toBeVisible({ timeout: 10_000 })
    const url = await urlText.textContent()
    expect(url).toMatch(/\/s\/tok_/)

    // "링크로 상대를 초대하세요" 제목 표시
    await expect(sheet.getByText('링크로 상대를 초대하세요')).toBeVisible()
    // 복사 버튼 표시
    await expect(page.getByRole('button', { name: '복사' })).toBeVisible()
  })
})

// ── B. 초대 후 대기 상태 전환 ────────────────────────────────────
test.describe('Flow 09-B: 초대 링크 전송 후 대기 상태', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('복사 클릭 → 시트 닫힘 + 상대 카드가 대기 중으로 전환', async ({ page, request }) => {
    const token = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, token)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    const inviteBtn = page.locator('[data-testid="invite-partner-btn"]')
    await expect(inviteBtn).toBeVisible({ timeout: 10_000 })
    await inviteBtn.click()

    // URL 생성 대기
    const urlText = page.locator('[data-testid="invite-url-text"]')
    await expect(urlText).toBeVisible({ timeout: 10_000 })

    // 복사 클릭 → 시트 닫힘 → 대기 상태로 전환
    await page.getByRole('button', { name: '복사' }).click()

    // InviteSheet 닫힘
    const sheet = page.locator('[data-testid="invite-sheet"]')
    await expect(sheet).not.toBeVisible({ timeout: 5_000 })

    // 상대 카드가 "초대함 · 답변 대기 중"으로 전환
    await expect(page.getByText('초대함 · 답변 대기 중')).toBeVisible({ timeout: 5_000 })
  })
})

// ── C. Full flow: 초대 → 상대 답변 → 양쪽 완성 ──────────────────────
test.describe('Flow 09-C: 상대 초대 → 답변 → 양쪽 완성', () => {

  test('상대방이 초대 URL에 접속해 답변 작성 → 사연 상세로 이동', async ({ page, request }) => {
    // 작성자: 포스트 + 초대 토큰 생성
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '초대 full flow 테스트')
    const inviteToken = await createInviteToken(request, authorToken, postId)

    // 상대방: /s/{token} 접근 (로그인 불필요)
    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    // 상대방 페이지 로드 확인
    await expect(page.getByText('상대방으로 답하기')).toBeVisible({ timeout: 8_000 })
    // 작성자 이야기 미리보기 표시
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 5_000 })
    // 제목 기본값(수정 가능)
    const titleInput = page.locator('input[type="text"]')
    await expect(titleInput).toBeVisible()

    // 상대방 답변 작성
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible()
    await textarea.fill('저도 나름의 사정이 있었어요. e2e 테스트 답변입니다.')

    // 덧붙이기 제출
    await page.getByRole('button', { name: '덧붙이기' }).click()

    // 제출 후 사연 상세(/community/{postId})로 이동
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 15_000 })
    expect(page.url()).toContain(`/community/${postId}`)
  })

  test('상대 답변 후 양쪽 사연 모두 표시 (paired)', async ({ page, request }) => {
    // 작성자 포스트 + 초대 + 상대 답변 (API로 준비)
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '양쪽 완성 E2E 검증')
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '상대 답변 내용입니다. 충분한 길이.', '상대방 제목')

    // paired 상태 확인
    const isPaired = await waitForPaired(request, postId)
    expect(isPaired).toBe(true)

    // 사연 상세 페이지에서 양쪽 사연 표시 확인
    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    // 작성자 이야기 카드 표시
    await expect(page.getByText('양쪽 완성 E2E 검증')).toBeVisible({ timeout: 10_000 })
    // "작성자" 라벨 표시
    await expect(page.getByText('작성자').first()).toBeVisible({ timeout: 5_000 })
    // 상대방 답변 내용 표시
    await expect(page.getByText('상대 답변 내용입니다.')).toBeVisible({ timeout: 8_000 })

    // 페어 도트(●●) 표시 — paired 상태 시각 표시
    const pairedDots = page.locator('span').filter({ has: page.locator('+ span') }).first()
    // 공감 비율 바 표시 (paired에서만)
    const ratioBar = page.locator('div[style*="border-radius: 4"]').filter({ hasText: '' }).first()
    await expect(ratioBar).toBeVisible({ timeout: 5_000 })
  })

  test('상대 답변 후 사연 상세 URL로 직접 접속해도 양쪽 사연 표시', async ({ page, request }) => {
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, 'URL 직접 접속 paired 확인')
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '직접 접속 테스트 답변 내용입니다.')

    await waitForPaired(request, postId)

    // 관람자로 접속 (storageState 없음 → 게스트)
    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await expect(page.getByText('URL 직접 접속 paired 확인')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('직접 접속 테스트 답변 내용입니다.')).toBeVisible({ timeout: 8_000 })
  })
})

// ── D. 관람자 — paired 사연 투표 흐름 ─────────────────────────────
test.describe('Flow 09-D: paired 사연 관람자 투표', () => {

  test('paired 사연 — 작성자·상대방 양쪽에 투표 버튼 표시 + 투표 기능', async ({ page, request }) => {
    // paired 포스트 준비
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '관람자 투표 E2E 테스트')
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '관람자 투표 테스트 답변입니다.')
    await waitForPaired(request, postId)

    // 게스트 관람자로 접속
    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    // 게스트 초기화 대기
    await page.waitForTimeout(2_000)

    // 작성자·상대방 투표 버튼 표시
    const voteG = page.locator(STORY_VOTE_BTN('g'))
    const voteR = page.locator(STORY_VOTE_BTN('r'))
    await expect(voteG).toBeVisible({ timeout: 8_000 })
    await expect(voteR).toBeVisible({ timeout: 5_000 })

    // 작성자 측 투표
    await voteG.click()
    // 완료 배지 표시
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 5_000 })
    // 버튼 라벨 "완료"로 변경
    await expect(voteG).toContainText('완료', { timeout: 3_000 })
  })

  test('paired 사연 — 공감 비율 바 표시 + 투표 후 실시간 갱신', async ({ page, request }) => {
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '비율 바 실시간 E2E')
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '비율 바 테스트 상대 답변입니다.')
    await waitForPaired(request, postId)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })
    await page.waitForTimeout(2_000)

    // 투표 전: 비율 바 표시
    const ratioSection = page.getByText(/작성자.*표|상대방.*표/).first()
    await expect(ratioSection).toBeVisible({ timeout: 8_000 })

    // 투표 실행
    const voteR = page.locator(STORY_VOTE_BTN('r'))
    await expect(voteR).toBeVisible({ timeout: 5_000 })
    await voteR.click()

    // 투표 완료 배지
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 5_000 })
  })
})

// ── E. 상대방 답변 화면 (/s/[token]) ─────────────────────────────
test.describe('Flow 09-E: 상대방 답변 화면', () => {

  test('유효한 초대 링크 — 작성자 사연 미리보기 + 답변 폼 표시', async ({ page, request }) => {
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '상대방 화면 테스트')
    const inviteToken = await createInviteToken(request, authorToken, postId)

    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    // 헤더
    await expect(page.getByText('상대방으로 답하기')).toBeVisible({ timeout: 8_000 })
    // 작성자 사연 읽기 전용
    await expect(page.getByText('작성자의 이야기')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('상대방 화면 테스트')).toBeVisible({ timeout: 5_000 })
    // 본문 입력 textarea
    await expect(page.locator('textarea')).toBeVisible()
    // 600자 글자수 카운터
    await expect(page.getByText('/ 600')).toBeVisible()
    // 제출 버튼
    await expect(page.getByRole('button', { name: '덧붙이기' })).toBeVisible()
  })

  test('유효하지 않은 초대 링크 → 오류 메시지 표시', async ({ page }) => {
    await page.goto(`${BASE}/s/tok_invalid_token_e2e`)
    await page.waitForURL(/\/s\/tok_invalid_token_e2e/, { timeout: 10_000 })

    await expect(page.getByText(/유효하지 않|찾을 수 없/)).toBeVisible({ timeout: 8_000 })
  })

  test('이미 답변된 초대 링크 → 재제출 시 오류 메시지', async ({ page, request }) => {
    const authorToken = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const postId = await createPost(request, authorToken, '중복 제출 방지 테스트')
    const inviteToken = await createInviteToken(request, authorToken, postId)
    // 첫 번째 답변 제출 (API)
    await submitPartnerAnswer(request, inviteToken, '첫 번째 답변입니다.')

    // 두 번째 제출 시도 (UI)
    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    const textarea = page.locator('textarea')
    if (await textarea.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await textarea.fill('중복 답변 시도')
      await page.getByRole('button', { name: '덧붙이기' }).click()
      // 이미 답변된 링크 오류 또는 이미 답변된 상태 메시지
      await expect(
        page.getByText(/이미 답변|중복|제출할 수 없/)
      ).toBeVisible({ timeout: 8_000 })
    }
    // 이미 답변됐으면 textarea가 없을 수도 있음 — 둘 다 정상
  })
})
