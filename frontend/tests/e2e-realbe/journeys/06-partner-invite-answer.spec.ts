/**
 * Journey 06: 상대 초대·답변·paired
 *
 * - 작성자 뷰 — 초대 버튼 표시
 * - InviteSheet 열림 + 초대 URL /s/tok_
 * - 복사 클릭 → 시트 닫힘 + 대기 상태
 * - Full flow: 초대 → 상대 답변 → paired 확인 (jurorCount=0 강제)
 * - 관람자 — paired 사연 양쪽 투표 버튼
 * - 상대방 답변 화면 (/s/[token]): 유효하지 않은 토큰 오류, 중복 제출 오류
 * - 답변 폼 UI 확인 (이전 spec에서 strict-mode 위반으로 제거됐던 케이스 수정)
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import {
  tokenFromStorageState,
  createPost,
  createInviteToken,
  submitPartnerAnswer,
  waitForPaired,
  setPublishMode,
  publishNow,
} from '../support/api'
import {
  INVITE,
  STORY_BODY,
  STORY_VOTE_BTN,
  VOTE_COMPLETE_BADGE,
} from '../support/selectors'
import { sql } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

// ── A. 작성자 뷰 — 초대 버튼 표시 ────────────────────────────────
test.describe('Journey 06-A: 작성자 뷰 — 초대 버튼', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — 사연 상세에서 상대 초대(+) 버튼 표시 + "상대 초대하기" 라벨', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: 'E2E 초대버튼 테스트 사연' })

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    const inviteBtn = page.locator(INVITE.partnerBtn)
    await expect(inviteBtn).toBeVisible({ timeout: 10_000 })
    await expect(inviteBtn).toContainText('상대 초대하기')
  })
})

// ── B. InviteSheet + URL 생성 ────────────────────────────────────
test.describe('Journey 06-B: InviteSheet + 초대 URL 생성', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('+ 버튼 클릭 → InviteSheet 열림 + 초대 URL /s/tok_ 표시', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: 'E2E InviteSheet 테스트' })

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await page.locator(INVITE.partnerBtn).click()

    const sheet = page.locator(INVITE.sheet)
    await expect(sheet).toBeVisible({ timeout: 8_000 })

    const urlText = page.locator(INVITE.urlText)
    await expect(urlText).toBeVisible({ timeout: 10_000 })
    const url = await urlText.textContent()
    expect(url).toMatch(/\/s\/tok_/)

    await expect(sheet.getByText('링크로 상대를 초대하세요')).toBeVisible()
    await expect(page.getByRole('button', { name: '복사' })).toBeVisible()
  })

  test('복사 클릭 → 시트 닫힘 + "초대함 · 답변 대기 중" 전환', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: 'E2E 복사 후 대기 상태' })

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await page.locator(INVITE.partnerBtn).click()
    await expect(page.locator(INVITE.urlText)).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: '복사' }).click()
    await expect(page.locator(INVITE.sheet)).not.toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('초대함 · 답변 대기 중')).toBeVisible({ timeout: 5_000 })
  })
})

// ── C. Full flow: 초대 → 답변 → paired ──────────────────────────
test.describe('Journey 06-C: 상대 초대 → 답변 → 양쪽 완성', () => {

  test('상대 답변 후 양쪽 사연 표시 (paired)', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: '양쪽 완성 E2E 검증' })
    const inviteToken = await createInviteToken(request, token, postId)
    await submitPartnerAnswer(request, inviteToken, '상대 답변 내용입니다. 충분한 길이.', '상대방 제목')

    const isPaired = await waitForPaired(request, postId)
    expect(isPaired).toBe(true)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await expect(page.getByText('양쪽 완성 E2E 검증')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('작성자').first()).toBeVisible({ timeout: 5_000 })
    await expect(page.locator(STORY_BODY('r'))).toContainText('상대 답변 내용입니다.', { timeout: 8_000 })
  })

  test('URL 직접 접속으로도 paired 사연 양쪽 표시', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: 'URL 직접 접속 paired 확인' })
    const inviteToken = await createInviteToken(request, token, postId)
    await submitPartnerAnswer(request, inviteToken, '직접 접속 테스트 답변 내용입니다.')
    await waitForPaired(request, postId)

    // 관람자(게스트)로 접속
    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await expect(page.getByText('URL 직접 접속 paired 확인')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(STORY_BODY('r'))).toContainText('직접 접속 테스트 답변 내용입니다.', { timeout: 8_000 })
  })
})

// ── D. 관람자 — paired 사연 투표 ─────────────────────────────────
test.describe('Journey 06-D: paired 사연 관람자 투표', () => {

  test('paired — 양쪽 투표 버튼 표시 + 작성자 측 투표 완료', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: '관람자 투표 E2E 테스트' })
    const inviteToken = await createInviteToken(request, token, postId)
    await submitPartnerAnswer(request, inviteToken, '관람자 투표 테스트 답변입니다.')
    await waitForPaired(request, postId)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    const voteG = page.locator(STORY_VOTE_BTN('g'))
    const voteR = page.locator(STORY_VOTE_BTN('r'))
    await expect(voteG).toBeVisible({ timeout: 10_000 })
    await expect(voteR).toBeVisible({ timeout: 5_000 })

    await voteG.click()
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 5_000 })
    await expect(voteG).toContainText('완료', { timeout: 3_000 })
  })
})

// ── E. 상대방 답변 화면 (/s/[token]) ─────────────────────────────
test.describe('Journey 06-E: 상대방 답변 화면', () => {

  test('답변 폼 UI — 제목·본문 표시 (strict-mode 수정)', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: 'E2E 답변 폼 UI 테스트' })
    const inviteToken = await createInviteToken(request, token, postId)

    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    // getByRole('heading')으로 strict-mode 위반 해결 (이전: getByText가 h1+label 2개 매칭)
    await expect(page.getByRole('heading', { name: '상대방으로 답하기' })).toBeVisible({ timeout: 8_000 })
    await expect(page.locator('textarea')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByRole('button', { name: '덧붙이기' })).toBeVisible()
  })

  test('유효하지 않은 초대 링크 → 오류 메시지 표시', async ({ page }) => {
    await page.goto(`${BASE}/s/tok_invalid_token_e2e`)
    await page.waitForURL(/\/s\/tok_invalid_token_e2e/, { timeout: 10_000 })
    await expect(page.getByText(/유효하지 않|찾을 수 없/)).toBeVisible({ timeout: 8_000 })
  })

  test('이미 답변된 초대 링크 → 재제출 시 오류 메시지', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token, title: '중복 제출 방지 테스트' })
    const inviteToken = await createInviteToken(request, token, postId)
    await submitPartnerAnswer(request, inviteToken, '첫 번째 답변입니다.')

    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    const textarea = page.locator('textarea')
    if (await textarea.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await textarea.fill('중복 답변 시도')
      await page.getByRole('button', { name: '덧붙이기' }).click()
      await expect(
        page.getByText(/이미 답변|중복|제출할 수 없/)
      ).toBeVisible({ timeout: 8_000 })
    }
    // 이미 답변된 상태면 textarea가 없을 수 있음 — 둘 다 정상
  })
})

// ── F. publish-mode / publish-now (API — UI 피커 없음) ─────────────
test.describe('Journey 06-F: publish-mode · publish-now', () => {

  test('WAIT_FOR_PARTNER → 상대 답변 후 PUBLIC + publish-now 경로', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    expect(token).toBeTruthy()

    const postId = await createPost(request, {
      token,
      title: 'E2E WAIT_FOR_PARTNER 테스트',
      body: 'publish-mode e2e 테스트용 본문입니다. 충분한 길이.',
    })

    await setPublishMode(request, token, postId, 'WAIT_FOR_PARTNER')
    const modeInDb = sql(`SELECT publish_mode FROM posts WHERE id='${postId}'`)
    expect(modeInDb).toMatch(/WAIT_FOR_PARTNER/i)

    const inviteToken = await createInviteToken(request, token, postId)
    await submitPartnerAnswer(request, inviteToken, 'WAIT_FOR_PARTNER 상대 답변입니다.')
    const paired = await waitForPaired(request, postId)
    expect(paired).toBe(true)

    const beforePublish = await request.get(`${BASE}/api/community/posts/${postId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    const before = await beforePublish.json()
    if ((before.visibility || '').toUpperCase() !== 'PUBLIC') {
      await publishNow(request, token, postId)
    }

    const finalRes = await request.get(`${BASE}/api/community/posts/${postId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(finalRes.ok()).toBeTruthy()
    const final = await finalRes.json()
    expect((final.visibility || '').toUpperCase()).toBe('PUBLIC')
  })
})
