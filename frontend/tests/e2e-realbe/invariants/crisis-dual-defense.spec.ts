/**
 * 절대 불변 규칙: 위기 키워드는 FE + BE 이중으로 차단된다.
 * 어느 한쪽 제거 시 회귀.
 *
 * 권위본:
 *   shared/docs/policies/crisis-detection.md
 *   frontend/docs/ux/principles.md §2.2 (위기 이중방어)
 *   frontend/README.md — 절대 불변 규칙 #2
 *
 * API 계약 (검증 완료):
 *   POST /api/sessions/{id}/messages + 위기어 → 409, body {success:false, crisisLevel:1}, error code 없음
 *   POST /api/sessions + description 위기어 → 422, body {error:{code:"CRISIS_DETECTED"}}
 *   메시지는 409 응답 시 DB 미저장 (CancelableChatService 라인 109-113)
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { login, createSession, sendMessage, getMessages } from '../fixtures/api-helpers'
import { cleanup } from '../fixtures/cleanup'
import { PERSONA_TEST1 } from '../fixtures/personas'
import {
  CRISIS_MODAL,
  CHAT_INPUT_PLACEHOLDER,
  SEND_BUTTON,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('절대 불변: Crisis 이중 방어', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  let token: string
  let sessionId: string

  test.beforeAll(async ({ request }) => {
    cleanup(BASE)
    token = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const session = await createSession(request, token)
    sessionId = session.id
  })

  test('경로 A — FE 차단: 위기어 입력 시 CrisisModal 표시 + 메시지 미저장', async ({
    page,
    request,
  }) => {
    await page.goto(`${BASE}/session/chat/${sessionId}`)

    const input = page.getByPlaceholder(CHAT_INPUT_PLACEHOLDER)
    await expect(input).toBeVisible({ timeout: 10_000 })

    const crisisContent = `폭행 테스트 ${Date.now()}`
    await input.fill(crisisContent)
    await page.getByRole('button', SEND_BUTTON).click()

    // CrisisModal 표시
    await expect(page.locator(CRISIS_MODAL)).toBeVisible({ timeout: 8_000 })

    // 메시지 미저장 확인 (FE 차단 → BE 요청 미전송이거나 409 → 저장 안 됨)
    const msgs = await getMessages(request, token, sessionId)
    const leaked = (msgs as Record<string, unknown>[]).find(
      (m) => typeof m.content === 'string' && (m.content as string).includes('폭행'),
    )
    expect(leaked).toBeUndefined()
  })

  test('경로 B — BE 우회: fetch로 직접 전송 → 409 + crisisLevel 1 + 미저장', async ({
    request,
  }) => {
    const { status, body } = await sendMessage(request, token, sessionId, '자살하고 싶어요')

    // BE 계약: 409, body {success:false, crisisLevel:1}, error code 없음
    expect(status).toBe(409)
    expect(body.success).toBe(false)
    expect(body.crisisLevel).toBe(1)
    expect(body).not.toHaveProperty('error')

    // 메시지 미저장
    const msgs = await getMessages(request, token, sessionId)
    const leaked = (msgs as Record<string, unknown>[]).find(
      (m) => typeof m.content === 'string' && (m.content as string).includes('자살'),
    )
    expect(leaked).toBeUndefined()
  })

  test('세션 생성 경로 — description 위기어 → 422 CRISIS_DETECTED', async ({ request }) => {
    const { status, body } = await sendMessage(request, token, sessionId, '정상 메시지')
    // 세션 생성 경로 별도 요청
    const resp = await request.post(`${BASE}/api/sessions`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        relationType: 'friend',
        category: { majorId: 'friend', middleId: 'friend_communication', minorId: 'contact_lack' },
        description: '아이를 때렸어요',
        soloMode: true,
      },
    })

    // BE 계약: 422, error.code === "CRISIS_DETECTED"
    expect(resp.status()).toBe(422)
    const body2 = await resp.json()
    expect(body2.error?.code).toBe('CRISIS_DETECTED')
  })
})
