/**
 * Journey 22: 시드 배심원 (LLM 미호출)
 *
 * 광장 C3StoryDetail에는 JurySection이 아직 미연결 — UI 대신
 * 작성자 GET /jury + juror_count 시드 계약을 검증한다.
 * JurySection 컴포넌트는 vitest(component)에서 커버.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { createPost, tokenFromStorageState } from '../support/api'
import { runSqlScript, sql } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

test.describe('Journey 22: 시드 배심원 API', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('SQL 시드 → 작성자 GET /jury 에 AI 의견·법적 고지', async ({ request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const postId = await createPost(request, {
      token,
      title: 'E2E 시드 배심원 API',
      body: '배심원 API e2e용 사연 본문입니다. LLM을 호출하지 않습니다.',
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

    const count = sql(`SELECT juror_count FROM posts WHERE id='${postId}'`)
    expect(Number(count)).toBe(3)

    const detail = await request.get(`${BASE}/api/community/posts/${postId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(detail.ok()).toBeTruthy()
    const post = await detail.json()
    expect(post.jurorCount).toBe(3)
    expect(post.isAuthor).toBe(true)

    const juryRes = await request.get(`${BASE}/api/community/posts/${postId}/jury`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(juryRes.ok()).toBeTruthy()
    const jury = await juryRes.json()
    expect(Array.isArray(jury.jurors)).toBeTruthy()
    expect(jury.jurors.length).toBeGreaterThanOrEqual(3)
    expect(String(jury.legalNotice || '')).toMatch(/공감|법적|과실|판결/)
    expect(String(jury.jurors[0].empathyComment || jury.jurors[0].comment || '')).toContain('e2e 시드')
  })
})
