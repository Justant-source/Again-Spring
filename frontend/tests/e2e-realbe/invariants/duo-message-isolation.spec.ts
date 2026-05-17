/**
 * 절대 불변 규칙: Duo 모드에서 상대방 메시지 원문은 DOM·API에 노출되지 않는다.
 *
 * 권위본:
 *   frontend/docs/ux/principles.md §2.2
 *   frontend/docs/ux/hax-checklist.md B17
 *   frontend/docs/ux/flows/06-duo.md
 *   frontend/README.md — 절대 불변 규칙 #4 (데이터 격리)
 *
 * API 계약 (검증 완료):
 *   GET /api/sessions/{id}/partner-messages → MessageMetadataResponse[]
 *   응답 필드: {id, sender, charCount, createdAt} — content 필드 구조적 부재
 *   (MessageMetadataResponse.java에 content 프로퍼티 없음)
 *
 * 사전 조건:
 *   test2(TESTER_A), test3(TESTER_B)는 globalSetup이 TESTER role 부여.
 *   app.features.duo-mode=false 환경에서도 ROLE_TESTER로 invite/join 허용됨.
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { login, createSession, sendMessage, invitePartner, joinSession, getPartnerMessages } from '../fixtures/api-helpers'
import { cleanup } from '../fixtures/cleanup'
import { PERSONA_TESTER_A, PERSONA_TESTER_B } from '../fixtures/personas'
import { assertNoContentField } from '../support/assertions'
import { BLURRED_BUBBLE } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('절대 불변: Duo 메시지 격리', () => {
  let tokenA: string
  let tokenB: string
  let sessionId: string
  let aMessageContent: string

  test.beforeAll(async ({ request }) => {
    cleanup(BASE)

    tokenA = await login(request, PERSONA_TESTER_A.email, PERSONA_TESTER_A.password)
    tokenB = await login(request, PERSONA_TESTER_B.email, PERSONA_TESTER_B.password)

    // A가 세션 생성
    const session = await createSession(request, tokenA, { relationType: 'friend' })
    sessionId = session.id

    // A가 파트너 초대
    const inviteToken = await invitePartner(request, tokenA, sessionId)

    // B 참여 (tokenB 전달 필수 — DUO_MODE_DISABLED 게이트가 TESTER 역할 확인)
    await joinSession(request, inviteToken, PERSONA_TESTER_B.nickname, tokenB)

    // A가 메시지 전송 (위기어 제외)
    aMessageContent = `A의 원문 메시지 ${Date.now()}`
    await sendMessage(request, tokenA, sessionId, aMessageContent)
  })

  test('partner-messages API 응답에 content 필드 없음', async ({ request }) => {
    const msgs = await getPartnerMessages(request, tokenB, sessionId)
    expect(msgs.length).toBeGreaterThan(0)
    assertNoContentField(msgs)
  })

  test('B 페이지 DOM에 A 원문 미노출 + BlurredBubble 표시', async ({ page }) => {
    // B 페르소나 토큰을 저장된 storageState에서 읽어 주입 (page.context().storageState()는 반환값 무시)
    const fs = await import('fs')
    const bStatePath = authStatePath(PERSONA_TESTER_B.email)
    const bState = JSON.parse(fs.readFileSync(bStatePath, 'utf-8'))
    const bToken = (bState.origins?.[0]?.localStorage ?? []).find(
      (e: { name: string; value: string }) => e.name === 'again-spring-token'
    )?.value as string | undefined

    await page.goto(BASE)
    if (bToken) {
      await page.evaluate((t: string) => localStorage.setItem('again-spring-token', t), bToken)
    }
    await page.goto(`${BASE}/session/chat/${sessionId}`)

    // BlurredBubble이 나타날 때까지 대기 (메시지 fetch 완료 후 렌더링)
    const bubbles = page.locator(BLURRED_BUBBLE)
    await bubbles.first().waitFor({ state: 'visible', timeout: 10_000 })

    // A 원문이 DOM에 없음
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).not.toContain(aMessageContent)

    // BlurredBubble DOM에 존재
    const bubbleCount = await bubbles.count()
    expect(bubbleCount).toBeGreaterThan(0)
  })

  test('B 페이지 storageState로 파트너 패널 진입', async ({ browser }) => {
    const contextB = await browser.newContext({
      storageState: authStatePath(PERSONA_TESTER_B.email),
    })
    const page = await contextB.newPage()
    await page.goto(`${BASE}/session/chat/${sessionId}`)
    await page.waitForTimeout(2_000)

    // 파트너 패널에 블러 버블만 있어야 함
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).not.toContain(aMessageContent)

    await page.close()
    await contextB.close()
  })
})
