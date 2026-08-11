/**
 * Journey 06b: 상대 초대 소유권 · claim · tombstone · 완전 삭제
 *
 * 계약: docs/frontend/ux/flows/09-partner-invite-ownership.md
 *
 * 1. /s/{token} → 로그인 → 같은 토큰 URL 복귀 (홈 금지)
 * 2. 무인증/게스트 답변 후 회원 재방문 → unowned claim CTA
 * 3. 작성자가 자기 초대 링크 → 답변 차단
 * 4. 상대 삭제 → partner tombstone
 * 5. 작성자 삭제(상대 ACTIVE) → author tombstone만
 * 6. 양쪽 삭제 → 삭제된 게시글 + 광장 버튼
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import {
  PERSONA_TEST1,
  PERSONA_TESTER_A,
  PERSONA_TESTER_B,
} from '../fixtures/personas'
import {
  tokenFromStorageState,
  createPost,
  createInviteToken,
  submitPartnerAnswer,
  waitForPaired,
  getInviteByToken,
  claimInvite,
  deletePartnerAnswer,
  deletePost,
  getPostDetail,
  guestLogin,
} from '../support/api'
import {
  INVITE,
  TOMBSTONE,
  DELETED_POST,
  STORY_BODY,
  EMAIL_INPUT_PLACEHOLDER,
  PASSWORD_INPUT_PLACEHOLDER,
  LOGIN_BUTTON,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── A. 로그인 next=/s/{token} 복귀 ────────────────────────────────
test.describe('Journey 06b-A: /s 로그인 복귀', () => {

  test('/s/{token}에서 로그인 → 같은 토큰 URL로 복귀 (홈·광장 아님)', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 로그인 복귀 초대' })
    const inviteToken = await createInviteToken(request, authorToken, postId)

    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    const loginLink = page.getByTestId('invite-login-link').or(
      page.getByRole('link', { name: /로그인|회원가입/ }),
    )
    await expect(loginLink.first()).toBeVisible({ timeout: 10_000 })
    await loginLink.first().click()
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toMatch(/next=%2Fs%2F|next=\/s\//)

    await page.getByPlaceholder(EMAIL_INPUT_PLACEHOLDER).fill(PERSONA_TESTER_A.email)
    await page.getByPlaceholder(PASSWORD_INPUT_PLACEHOLDER).fill(PERSONA_TESTER_A.password)
    await page.getByRole('button', LOGIN_BUTTON).click()

    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 12_000 })
    expect(page.url()).toContain(`/s/${inviteToken}`)
    expect(page.url()).not.toMatch(/\/community\/?$/)
    await expect(
      page.getByRole('heading', { name: '상대방으로 답하기' }).or(
        page.locator(INVITE.authorBlocked),
      ).first(),
    ).toBeVisible({ timeout: 8_000 })
  })
})

// ── B. unowned → claim CTA ───────────────────────────────────────
test.describe('Journey 06b-B: unowned claim CTA', () => {
  test.use({ storageState: authStatePath(PERSONA_TESTER_A.email) })

  test('게스트 답변 후 회원 재방문 → ownership=UNOWNED · claim CTA', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const memberToken = tokenFromStorageState(PERSONA_TESTER_A.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E unowned claim' })
    const inviteToken = await createInviteToken(request, authorToken, postId)

    // 무인증(또는 게스트) 답변 → UNOWNED
    await submitPartnerAnswer(request, inviteToken, 'unowned 상대 답변입니다. 충분한 길이.')
    await waitForPaired(request, postId)

    const preview = await getInviteByToken(request, inviteToken, memberToken)
    expect(preview.deleted).not.toBe(true)
    expect(preview.partnerState).toBe('ACTIVE')
    expect(preview.ownership).toBe('UNOWNED')
    expect(preview.canClaim).toBe(true)

    await page.goto(`${BASE}/s/${inviteToken}`)
    await page.waitForURL(new RegExp(`/s/${inviteToken}`), { timeout: 10_000 })

    await expect(page.locator(INVITE.claimBtn)).toBeVisible({ timeout: 10_000 })
    await page.locator(INVITE.claimBtn).click()
    await expect(page.locator(INVITE.editBtn)).toBeVisible({ timeout: 10_000 })

    const after = await getInviteByToken(request, inviteToken, memberToken)
    expect(after.ownership).toBe('OWNED')
    expect(after.canClaim).toBe(false)
  })

  test('API claimInvite — unowned → OWNED', async ({ request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const memberToken = tokenFromStorageState(PERSONA_TESTER_A.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E API claim' })
    const inviteToken = await createInviteToken(request, authorToken, postId)

    await guestLogin(request, 'E2Eclaim게스트')
    await submitPartnerAnswer(request, inviteToken, 'API claim용 게스트 답변입니다.')
    await waitForPaired(request, postId)

    await claimInvite(request, inviteToken, memberToken)
    const after = await getInviteByToken(request, inviteToken, memberToken)
    expect(after.ownership).toBe('OWNED')
    expect(after.canClaim).toBe(false)
  })
})

// ── C. 작성자 자기 초대 차단 ─────────────────────────────────────
test.describe('Journey 06b-C: 작성자 자기 초대 차단', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('작성자가 /s/{token} 열면 답변 폼 대신 차단 UI · canWrite=false', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 작성자 자기초대' })
    const inviteToken = await createInviteToken(request, authorToken, postId)

    const preview = await getInviteByToken(request, inviteToken, authorToken)
    expect(preview.ownership).toBe('AUTHOR')
    expect(preview.canWrite).toBe(false)

    await page.goto(`${BASE}/s/${inviteToken}`)
    await expect(page.locator(INVITE.authorBlocked)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(INVITE.claimBtn)).toHaveCount(0)
  })
})

// ── D. 상대 삭제 → partner tombstone ─────────────────────────────
test.describe('Journey 06b-D: 상대 삭제 tombstone', () => {

  test('상대 DELETE answer → partnerBodyDeleted · UI tombstone · 토큰 유지', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 상대 tombstone' })
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '곧 삭제될 상대 답변입니다.')
    await waitForPaired(request, postId)

    await deletePartnerAnswer(request, inviteToken)

    const invite = await getInviteByToken(request, inviteToken)
    expect(invite.deleted).not.toBe(true)
    expect(invite.partnerState).toBe('TOMBSTONE')
    expect(invite.canWrite).toBe(true) // 재작성

    const { status, data } = await getPostDetail(request, postId, authorToken)
    expect(status).toBe(200)
    expect(data?.partnerBodyDeleted).toBe(true)
    expect(data?.authorBodyDeleted).not.toBe(true)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })
    await expect(page.locator(TOMBSTONE.partner)).toBeVisible({ timeout: 10_000 })

    // /s 에서도 재작성 경로
    await page.goto(`${BASE}/s/${inviteToken}`)
    await expect(
      page.locator(INVITE.rewriteBtn)
        .or(page.getByRole('heading', { name: '상대방으로 답하기' }))
        .first(),
    ).toBeVisible({ timeout: 10_000 })
  })
})

// ── E. 작성자 삭제(상대 ACTIVE) → author tombstone만 ─────────────
test.describe('Journey 06b-E: 작성자 tombstone (상대 유지)', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('작성자 DELETE post(상대 ACTIVE) → author tombstone · 상대 본문 유지', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 작성자 tombstone' })
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(
      request,
      inviteToken,
      '작성자 삭제 후에도 남는 상대 답변입니다.',
    )
    await waitForPaired(request, postId)

    await deletePost(request, authorToken, postId)

    const { status, data } = await getPostDetail(request, postId, authorToken)
    expect(status).toBe(200)
    expect(data?.deleted).not.toBe(true)
    expect(data?.authorBodyDeleted).toBe(true)
    expect(data?.partnerBodyDeleted).not.toBe(true)
    expect(data?.partnerBodyPublished || data?.paired).toBeTruthy()

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })
    await expect(page.locator(TOMBSTONE.author)).toBeVisible({ timeout: 10_000 })
    await expect(page.locator(STORY_BODY('r'))).toContainText('남는 상대 답변', { timeout: 8_000 })
  })
})

// ── F. 양쪽 삭제 → 삭제된 게시글 ─────────────────────────────────
test.describe('Journey 06b-F: 양쪽 삭제 → deleted post', () => {

  test('상대 tombstone 후 작성자 삭제 → deleted 페이지 + 광장 버튼', async ({ page, request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 양쪽 삭제' })
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(request, inviteToken, '양쪽 삭제용 상대 답변입니다.')
    await waitForPaired(request, postId)

    await deletePartnerAnswer(request, inviteToken)
    await deletePost(request, authorToken, postId)

    const invite = await getInviteByToken(request, inviteToken)
    expect(invite.deleted).toBe(true)

    const { status, data } = await getPostDetail(request, postId, authorToken)
    // 200 + deleted:true 권장 (404/410도 완전 삭제로 허용)
    if (status === 200) {
      expect(data?.deleted).toBe(true)
    } else {
      expect([404, 410]).toContain(status)
    }

    await page.goto(`${BASE}/community/${postId}`)
    await expect(page.locator(DELETED_POST.page)).toBeVisible({ timeout: 12_000 })
    await expect(page.locator(DELETED_POST.plazaBtn)).toBeVisible({ timeout: 8_000 })

    await page.goto(`${BASE}/s/${inviteToken}`)
    await expect(page.locator(DELETED_POST.message).or(page.locator(DELETED_POST.page)).first()).toBeVisible({
      timeout: 12_000,
    })
  })

  test('작성자 tombstone 후 상대 삭제 → 완전 삭제', async ({ request }) => {
    const authorToken = tokenFromStorageState(PERSONA_TEST1.email)
    const partnerToken = tokenFromStorageState(PERSONA_TESTER_B.email)
    const postId = await createPost(request, { token: authorToken, title: 'E2E 작성자 먼저 tombstone' })
    const inviteToken = await createInviteToken(request, authorToken, postId)
    await submitPartnerAnswer(
      request,
      inviteToken,
      '작성자 먼저 삭제한 뒤 상대도 지웁니다.',
      '상대',
      { token: partnerToken },
    )
    await waitForPaired(request, postId)

    await deletePost(request, authorToken, postId)
    const mid = await getPostDetail(request, postId, authorToken)
    expect(mid.status).toBe(200)
    expect(mid.data?.authorBodyDeleted).toBe(true)

    await deletePartnerAnswer(request, inviteToken, { token: partnerToken })

    const invite = await getInviteByToken(request, inviteToken)
    expect(invite.deleted).toBe(true)
  })
})
