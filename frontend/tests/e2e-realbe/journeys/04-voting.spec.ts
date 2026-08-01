/**
 * Journey 04: 투표
 *
 * - 회원 투표 + 취소 + 재투표
 * - 게스트 투표 지속성 (reload 후 완료 유지)
 * - 게스트 soft-delete 복구 회귀 (07 spec에서 이전, DB 비밀번호 인라인 제거)
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { createPost, guestLogin } from '../support/api'
import { softDeleteUser, isUserActive } from '../support/db'
import {
  STORY_VOTE_BTN,
  VOTE_COMPLETE_BADGE,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

// ── A. 게스트 투표 지속성 ─────────────────────────────────────────
test.describe('Journey 04-A: 게스트 투표 지속성', () => {

  test('게스트 — 투표 후 새로고침해도 완료 상태 유지', async ({ page }) => {
    await page.goto(`${BASE}/community/mock_001`)

    const voteG = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteG).toBeVisible({ timeout: 12_000 })

    // 아직 투표 전이면 완료 배지 없음 (새 게스트)
    // 이미 투표된 게스트이면 배지가 있을 수 있음 — 없는 경우만 클릭
    const alreadyVoted = await page.locator(VOTE_COMPLETE_BADGE).isVisible({ timeout: 1_000 }).catch(() => false)
    if (!alreadyVoted) {
      await voteG.click()
      await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 8_000 })
    }

    // 새로고침 → 완료 상태 유지 (6/5 이전 버그: 즉시 취소됨)
    await page.reload()
    const voteGAfter = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteGAfter).toBeVisible({ timeout: 12_000 })
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })
  })
})

// ── B. 회원 투표 ─────────────────────────────────────────────────
test.describe('Journey 04-B: 회원 투표', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('회원 — 게시글에서 투표 + 완료 배지 표시', async ({ page, request }) => {
    const token = await import('../support/api').then(m => m.tokenFromStorageState(PERSONA_TEST1.email))
    const postId = await createPost(request, { token, title: '회원 투표 E2E' })

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    const voteG = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteG).toBeVisible({ timeout: 10_000 })
    await voteG.click()
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 8_000 })
  })
})

// ── C. 게스트 soft-delete 복구 회귀 ─────────────────────────────
test.describe('Journey 04-C: 게스트 soft-delete 투표 복구 (회귀)', () => {

  test('soft-delete된 게스트(유효 토큰) — 투표 시 자동 복구 + 완료 배지', async ({ page, request }) => {
    // 셋업: 게스트 포스트 생성
    const guestToken = await guestLogin(request, 'REPRO작성자')
    const postId = await createPost(request, {
      token: guestToken,
      title: 'REPRO soft-delete 게스트 투표',
      body: 'soft-delete 게스트 투표 재현용 사연 본문입니다. 충분한 길이를 확보합니다.',
    })

    // 페이지 진입 → 브라우저가 자체 deviceId로 게스트 자동 발급
    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForFunction(() => !!localStorage.getItem('again-spring-token'), null, { timeout: 10_000 })
    const token = await page.evaluate(() => localStorage.getItem('again-spring-token')!)

    // JWT sub 추출 (= guestId)
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8'))
    const guestId = payload.sub as string
    expect(guestId).toBeTruthy()

    // soft-delete (마이그레이션/탈퇴 버그 재현)
    softDeleteUser(guestId)

    // 재방문 → 삭제된 게스트 토큰 상태
    await page.goto(`${BASE}/community/${postId}`)

    // 투표 → 인터셉터가 게스트 재발급(행 재활성화) + 재시도 → 성공
    const voteG = page.locator(STORY_VOTE_BTN('g'))
    await expect(voteG).toBeVisible({ timeout: 12_000 })
    await voteG.click()
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })

    // BE가 동일 행을 재활성화했는지 확인
    expect(isUserActive(guestId)).toBe(true)

    // 새로고침 후에도 완료 유지
    await page.reload()
    await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })
  })
})
