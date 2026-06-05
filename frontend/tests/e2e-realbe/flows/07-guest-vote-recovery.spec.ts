import { test, expect } from '@playwright/test'
import { execSync } from 'node:child_process'
import { STORY_VOTE_BTN, VOTE_COMPLETE_BADGE } from '../support/selectors'
import { guestLogin } from '../fixtures/api-helpers'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

/**
 * 회귀: soft-delete된 게스트(예: 구버전 '게스트 4179')가 유효한 토큰을 들고 재방문해도 투표할 수 있어야 한다.
 *
 * 근본 버그:
 *  - 마이그레이션/탈퇴로 게스트 행이 soft-delete됨. 브라우저엔 그 id의 유효 토큰이 남아있음.
 *  - 투표 → UserDetailsService(findByIdAndDeletedAtIsNull)가 삭제 행을 못 찾아 403.
 *  - guest()는 findById(삭제 포함)로 행을 찾아 재발급만 해 같은 깨진 id 반복 → 영구 403.
 * 수정:
 *  - BE: guest()가 삭제 행을 재활성화(deletedAt=null) → 발급 토큰이 항상 활성 행을 가리킴.
 *  - FE: 인터셉터가 community 401/403에서 게스트 토큰 강제 재발급 + 원요청 1회 재시도.
 */

const SQL = (q: string) =>
  execSync(
    `docker exec againspring-mariadb-dev sh -c ${JSON.stringify(
      'mariadb -uagainspring -pF2etXbugW0EBDZNBMX17Q againspring_dev -N -e ' + JSON.stringify(q),
    )}`,
  ).toString().trim()

/** JWT subject(=guestId) 추출 */
function jwtSub(token: string): string {
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8'))
  return payload.sub as string
}

async function freshVotingPost(request: import('@playwright/test').APIRequestContext): Promise<string> {
  const token = await guestLogin(request, 'REPRO작성자')
  const resp = await request.post(`${BASE}/api/community/posts`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      bodyRaw: 'soft-delete 게스트 투표 재현용 사연 본문입니다. 충분한 길이를 확보합니다.',
      category: 'OTHER',
      visibility: 'PUBLIC',
      jurorCount: 0,
      userTitle: 'REPRO soft-delete 게스트 투표',
    },
  })
  if (!resp.ok()) throw new Error(`포스트 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).id as string
}

test('soft-delete된 게스트(유효 토큰 보유) — 투표 시 자동 복구되어 완료 표시', async ({ page, request }) => {
  const postId = await freshVotingPost(request)

  // 1) 페이지 진입 → 브라우저가 스스로 게스트 자동 발급 (자기 device-id 사용)
  await page.goto(`${BASE}/community/${postId}`)
  await page.waitForFunction(() => !!localStorage.getItem('again-spring-token'), null, { timeout: 10_000 })
  const token = await page.evaluate(() => localStorage.getItem('again-spring-token')!)
  const guestId = jwtSub(token)
  expect(guestId).toBeTruthy()

  // 2) 이 게스트를 soft-delete (마이그레이션/탈퇴 상황 재현).
  //    토큰은 여전히 유효(만료 전) → "유효 토큰인데 사용자 행이 없는" 정확한 버그 상태.
  SQL(`UPDATE users SET deleted_at = NOW() WHERE id = '${guestId}'`)
  expect(SQL(`SELECT deleted_at IS NOT NULL FROM users WHERE id='${guestId}'`)).toBe('1')

  // 3) 재방문 → 삭제된 게스트의 유효 토큰 상태로 시작 (토큰 유지됨)
  await page.goto(`${BASE}/community/${postId}`)
  await page.waitForTimeout(1500)

  // 4) 투표 → 403이 떠도 인터셉터가 게스트 재발급(행 재활성화) + 재시도하여 성공해야 함
  const voteG = page.locator(STORY_VOTE_BTN('g'))
  await expect(voteG).toBeVisible({ timeout: 10_000 })
  await voteG.click()
  await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })

  // 5) BE가 동일 행을 재활성화했는지 확인 (deletedAt=null)
  expect(SQL(`SELECT deleted_at IS NULL FROM users WHERE id='${guestId}'`)).toBe('1')

  // 6) 새로고침 후에도 완료 유지
  await page.reload()
  await page.waitForTimeout(1500)
  await expect(page.locator(VOTE_COMPLETE_BADGE)).toBeVisible({ timeout: 10_000 })
})
