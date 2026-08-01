/**
 * Journey 20: 알림
 *
 * - DB 시드 → GET /api/notifications
 * - POST read-all → isRead 반영 (API)
 * - /notifications 페이지 스모크
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { tokenFromStorageState } from '../support/api'
import { sql, runSqlScript } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
const NOTI_ID = 'noti_e2e_journey20'

test.describe('Journey 20: 알림', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test.beforeAll(() => {
    const userId = sql(`SELECT id FROM users WHERE email='test1@again.com' LIMIT 1`)
    if (!userId) throw new Error('test1 user id 없음')
    runSqlScript(`
      DELETE FROM notifications WHERE id='${NOTI_ID}' OR id LIKE 'noti_e2e%';
      INSERT INTO notifications (id, user_id, type, title, subtitle, ref_post_id, is_read, created_at)
      VALUES ('${NOTI_ID}', '${userId}', 'NEW_COMMENT', '댓글이 달렸어요', 'e2e 알림 시드', 'mock_001', 0, NOW(6));
    `)
  })

  test.afterAll(() => {
    try {
      sql(`DELETE FROM notifications WHERE id='${NOTI_ID}'`)
    } catch { /* ignore */ }
  })

  test('시드 알림 API → read-all → 페이지 스모크', async ({ page, request }) => {
    const token = tokenFromStorageState(PERSONA_TEST1.email)
    const headers = { Authorization: `Bearer ${token}` }

    const listRes = await request.get(`${BASE}/api/notifications`, { headers })
    expect(listRes.ok()).toBeTruthy()
    const list = await listRes.json()
    const items = Array.isArray(list) ? list : (list.content ?? list.notifications ?? [])
    const seeded = items.find((n: { id?: string; title?: string }) =>
      n.id === NOTI_ID || n.title === '댓글이 달렸어요',
    )
    expect(seeded, '시드 알림이 목록에 있어야 함').toBeTruthy()

    const readRes = await request.post(`${BASE}/api/notifications/read-all`, { headers })
    expect(readRes.ok()).toBeTruthy()

    const listAfter = await request.get(`${BASE}/api/notifications`, { headers })
    const itemsAfter = await listAfter.json()
    const arr = Array.isArray(itemsAfter) ? itemsAfter : (itemsAfter.content ?? itemsAfter.notifications ?? [])
    const after = arr.find((n: { id?: string; title?: string; isRead?: boolean; read?: boolean }) =>
      n.id === NOTI_ID || n.title === '댓글이 달렸어요',
    )
    if (after) {
      expect(after.isRead === true || after.read === true || after.isRead === 1).toBeTruthy()
    }

    await page.goto(`${BASE}/notifications`)
    await page.waitForURL(/\/notifications/, { timeout: 10_000 })
    await expect(
      page.getByText(/알림|댓글이 달렸어요|아직 알림이 없어요/).first(),
    ).toBeVisible({ timeout: 10_000 })
  })
})
