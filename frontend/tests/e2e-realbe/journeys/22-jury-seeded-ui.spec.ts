/**
 * Journey 22: 시드 배심원 UI (LLM 미호출)
 *
 * createPost(jurorCount=0) 후 SQL로 juror_count·jurors 삽입.
 * 작성자(test1) 세션에서만 JurySection이 로드됨.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { createPost, tokenFromStorageState } from '../support/api'
import { runSqlScript, sql } from '../support/db'
import { JURY } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

test.describe('Journey 22: 시드 배심원 UI', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('작성자 — AI 배심원 섹션·카드·법적 고지 표시', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'E2E 시드 배심원 UI',
      body: '배심원 UI e2e용 사연 본문입니다. LLM을 호출하지 않습니다.',
    })

    const optionId = sql(
      `SELECT id FROM vote_options WHERE post_id='${postId}' AND order_idx=0 LIMIT 1`,
    )
    expect(optionId).toBeTruthy()

    runSqlScript(`
      UPDATE posts SET juror_count=3 WHERE id='${postId}';
      DELETE FROM jurors WHERE post_id='${postId}';
      INSERT INTO jurors (post_id, persona, chosen_option_id, empathy_comment, created_at) VALUES
      ('${postId}', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"관계중시"}',
       ${optionId}, 'e2e 시드 공감 의견입니다. 작성자 쪽 관점을 살펴볼 필요가 있어요.', NOW()),
      ('${postId}', '{"ageGroup":"40대","gender":"남성","disposition":"분석형","valueOrientation":"균형중시"}',
       ${optionId}, 'e2e 시드 분석 의견입니다. 상대방의 피로는 별도로 볼 여지가 있습니다.', NOW()),
      ('${postId}', '{"ageGroup":"20대","gender":"여성","disposition":"직관형","valueOrientation":"자기표현중시"}',
       ${optionId}, 'e2e 시드 직관 의견입니다. 대화의 장을 다시 여는 편이 나아 보여요.', NOW());
    `)

    await page.goto(`${BASE}/community/${postId}`)
    await page.waitForURL(new RegExp(`/community/${postId}$`), { timeout: 12_000 })

    await expect(page.locator(JURY.section)).toBeVisible({ timeout: 12_000 })
    await expect(page.getByText(/AI 배심원/)).toBeVisible({ timeout: 5_000 })
    await expect(page.locator(JURY.card).first()).toBeVisible({ timeout: 8_000 })
    await expect(page.locator(JURY.card).first()).toContainText('AI')
    await expect(page.locator(JURY.legalNotice)).toBeVisible({ timeout: 5_000 })
    await expect(page.locator(JURY.legalNotice)).toContainText(/공감|법적|과실|판결/)
  })
})
