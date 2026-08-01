/**
 * Journey 20: 알림 페이지
 *
 * - 회원 /notifications 진입
 * - DB 시드 알림 표시
 * - "모두 읽음" → read-all
 *
 * AI 배심원 알림 타입은 없음 — LLM 미호출.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { sql, runSqlScript } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const NOTI_ID = 'noti_e2e_journey20'

test.describe('Journey 20: 알림', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test.beforeAll(() => {
    const userId = sql(`SELECT id FROM users WHERE email='test1@again.com' LIMIT 1`)
    if (!userId) throw new Error('test1 user id 없음')
    runSqlScript(`
      DELETE FROM notifications WHERE id='${NOTI_ID}';
      INSERT INTO notifications (id, user_id, type, title, subtitle, ref_post_id, is_read, created_at)
      VALUES ('${NOTI_ID}', '${userId}', 'NEW_COMMENT', '댓글이 달렸어요', 'e2e 알림 시드', 'mock_001', 0, NOW(6));
    `)
  })

  test.afterAll(() => {
    try {
      sql(`DELETE FROM notifications WHERE id='${NOTI_ID}'`)
    } catch { /* ignore */ }
  })

  test('시드 알림 표시 → 모두 읽음', async ({ page }) => {
    await page.goto(`${BASE}/notifications`)
    await page.waitForURL(/\/notifications/, { timeout: 10_000 })

    await expect(page.getByText('댓글이 달렸어요').first()).toBeVisible({ timeout: 10_000 })

    const readAll = page.getByRole('button', { name: '모두 읽음' })
    if (await readAll.isVisible({ timeout: 3_000 }).catch(() => false)) {
      const resp = page.waitForResponse(
        (r) => r.url().includes('/api/notifications/read-all') && r.request().method() === 'POST',
        { timeout: 8_000 },
      )
      await readAll.click()
      await resp
    }

    const isRead = sql(`SELECT is_read FROM notifications WHERE id='${NOTI_ID}'`)
    expect(isRead === '1' || isRead === 'true' || Number(isRead) === 1).toBeTruthy()
  })
})
