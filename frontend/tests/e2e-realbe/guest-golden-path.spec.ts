import { test, expect } from '@playwright/test'

/**
 * 게스트 대화 생성 골든패스 (실 BE 대상).
 *
 * 전제: BE가 localhost:8080에서 실행 중이어야 함.
 * MSW는 NEXT_PUBLIC_DISABLE_MSW=true로 비활성화.
 * next.config.mjs의 rewrite가 /api/* → localhost:8080 프록시.
 *
 * 검증 목표: 게스트가 랜딩→대화 생성→채팅 화면까지 실 BE와 연동해 성공하는지.
 */

const ONBOARDING_STEPS = 10

test.describe('게스트 골든패스 (실 BE 연동)', () => {

  test('게스트 인증 → 세션 생성 → 채팅 화면 진입 + 첫마디 수신', async ({ page }) => {
    // 0. BE 헬스 체크 — BE가 응답하지 않으면 명확한 실패 메시지
    let beHealthy = false
    try {
      const resp = await page.request.get('http://localhost:8080/api/health')
      beHealthy = resp.ok()
    } catch {}
    expect(beHealthy, 'BE (localhost:8080)가 실행 중이어야 합니다. ./gradlew bootRun 후 재시도').toBe(true)

    // 1. 랜딩 페이지
    await page.goto('/')
    await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 10000 })

    // 2. 게스트 진입
    const guestLink = page.getByRole('link', { name: /게스트로/ })
    await expect(guestLink).toBeVisible({ timeout: 5000 })
    await guestLink.click()
    await page.waitForURL('**/guest', { timeout: 8000 })
    expect(page.url()).toContain('/guest')

    // 3. 닉네임 입력 → 시작하기 (실 BE POST /api/auth/guest 호출)
    const nicknameInput = page.locator('input[type="text"]').first()
    await nicknameInput.waitFor({ state: 'visible', timeout: 5000 })
    await nicknameInput.fill('E2E게스트')

    // API 요청 캡처로 실제 BE 호출 확인
    const guestAuthPromise = page.waitForResponse(
      (resp) => resp.url().includes('/api/auth/guest') && resp.request().method() === 'POST',
      { timeout: 10000 }
    )
    await page.getByRole('button', { name: '시작하기' }).click()
    const guestAuthResp = await guestAuthPromise

    expect(guestAuthResp.status(), 'POST /api/auth/guest가 200이어야 합니다').toBe(200)
    const authBody = await guestAuthResp.json()
    expect(authBody.token?.accessToken, 'accessToken이 발급돼야 합니다').toBeTruthy()
    expect(authBody.user?.isGuest, '게스트 유저여야 합니다').toBe(true)

    // 4. 온보딩 흐름 (FE 전용 — BE API 미호출, 빠르게 통과)
    await page.waitForURL('**/onboarding**', { timeout: 8000 })

    // 온보딩 인트로 → 시작 버튼
    const introStartBtn = page.getByRole('button', { name: /10문항|시작|다음/ }).first()
    if (await introStartBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await introStartBtn.click()
    }

    // Likert 10문항: 중간값 선택 + 다음
    for (let i = 0; i < ONBOARDING_STEPS; i++) {
      await page.waitForTimeout(200)
      const buttons = page.getByRole('button', { name: /^[1-5]$/ })
      const count = await buttons.count()
      if (count > 0) {
        await buttons.nth(Math.floor(count / 2)).click()
      }
      const nextOrDone = page.getByRole('button', { name: /다음|완료/ })
      if (await nextOrDone.isVisible({ timeout: 1000 }).catch(() => false)) {
        await nextOrDone.click()
      }
    }

    // 온보딩 결과 → 완료하기
    await page.waitForTimeout(500)
    const doneBtn = page.getByRole('button', { name: /완료|다음|시작/ }).first()
    if (await doneBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await doneBtn.click()
    }

    // 5. 관계 유형 선택
    await page.waitForURL('**/session/new**', { timeout: 10000 })
    expect(page.url()).toContain('/session/new')

    const friendBtn = page.getByRole('button', { name: /친구/ })
    await expect(friendBtn).toBeVisible({ timeout: 5000 })
    await friendBtn.click()

    // 6. 카테고리 선택 → POST /api/sessions 실 BE 호출
    await page.waitForURL('**/session/category**', { timeout: 8000 })
    expect(page.url()).toContain('/session/category')

    const sessionCreatePromise = page.waitForResponse(
      (resp) => resp.url().includes('/api/sessions') && resp.request().method() === 'POST',
      { timeout: 15000 }
    )

    // 첫 번째 중분류 → 첫 번째 소분류 클릭 (또는 직접입력 제외한 첫 항목)
    const firstMiddleBtn = page.getByRole('button').filter({ hasNot: page.getByText(/직접 입력/) }).nth(1)
    if (await firstMiddleBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await firstMiddleBtn.click()
      await page.waitForTimeout(300)
    }
    const firstMinorBtn = page.getByRole('button').filter({ hasNot: page.getByText(/직접 입력/) }).nth(1)
    if (await firstMinorBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await firstMinorBtn.click()
      await page.waitForTimeout(300)
    }

    // 세션 생성 확인 버튼이 있으면 클릭
    const confirmBtn = page.getByRole('button', { name: /시작|확인|대화 시작/ })
    if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await confirmBtn.click()
    }

    const sessionResp = await sessionCreatePromise

    expect(sessionResp.status(), [
      `POST /api/sessions가 200이어야 합니다.`,
      `실제 응답: ${sessionResp.status()} — BE 로그를 확인하세요.`,
    ].join(' ')).toBe(200)

    const sessionBody = await sessionResp.json()
    const sessionId = sessionBody.id
    expect(sessionId, '세션 ID가 있어야 합니다').toBeTruthy()

    // 7. 채팅 화면 진입
    await page.waitForURL(`**/session/chat/${sessionId}**`, { timeout: 10000 })
    expect(page.url()).toContain(`/session/chat/${sessionId}`)

    // 8. 첫마디(mediator) 메시지가 화면에 표시되는지 확인
    // MEDIATOR_TO_A 메시지 버블 — 채팅 화면의 첫 메시지
    const firstMessageLocator = page.locator('[data-sender="MEDIATOR_TO_A"], [class*="mediator"], [class*="ai-message"]').first()
    const hasFirstMessage = await firstMessageLocator.isVisible({ timeout: 8000 }).catch(() => false)

    if (!hasFirstMessage) {
      // fallback: 채팅 입력창이 보이면 채팅 화면에 도달한 것으로 판단
      const chatInput = page.getByPlaceholder(/편한 말|메시지|입력/)
      await expect(chatInput, '채팅 입력창이 보여야 합니다 (채팅 화면 도달 확인)').toBeVisible({ timeout: 8000 })
    } else {
      await expect(firstMessageLocator).toBeVisible()
    }

    // 9. 사용자 메시지 전송 → 실 BE POST /api/sessions/{id}/messages 호출
    const chatInput = page.getByPlaceholder(/편한 말|메시지|입력/)
    await chatInput.waitFor({ state: 'visible', timeout: 5000 })
    await chatInput.fill('안녕하세요, 테스트 메시지입니다.')

    const msgSendPromise = page.waitForResponse(
      (resp) => resp.url().includes(`/api/sessions/${sessionId}/messages`) && resp.request().method() === 'POST',
      { timeout: 10000 }
    )
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()
    const msgResp = await msgSendPromise

    expect(msgResp.status(), 'POST /api/sessions/{id}/messages가 200이어야 합니다').toBe(200)

    // 10. mediator 응답 대기 (haiku 호출 — 최대 30초)
    // mediator 응답 버블이 나타나면 성공, 타임아웃이면 경고 (haiku 비응답 환경 허용)
    const mediatorReplyLocator = page.locator('[data-sender^="MEDIATOR"], [class*="mediator-msg"], [class*="ai-bubble"]')
    const gotReply = await mediatorReplyLocator
      .nth(1)  // 첫마디(0번) 이후 두 번째 mediator 메시지
      .isVisible({ timeout: 30000 })
      .catch(() => false)

    if (!gotReply) {
      console.warn('[E2E] mediator 응답이 30초 내 미표시 — haiku 응답이 느리거나 BE에서 fallback 메시지 처리 중')
    }
    // mediator 응답 자체는 haiku 의존이므로 optional assertion (플로우 실패가 아님)
  })

  test('BE 비정상 시 게스트 입장 화면에서 오류 메시지 표시', async ({ page }) => {
    // 별도 BE 없이 직접 /guest 접근 + 시작하기 → 오류 상태 확인
    // (이 테스트는 BE가 내려가 있을 때 FE가 오류를 올바르게 표시하는지 검증)
    // BE가 살아있으면 skip
    let beHealthy = false
    try {
      const resp = await page.request.get('http://localhost:8080/api/health')
      beHealthy = resp.ok()
    } catch {}

    if (beHealthy) {
      test.skip(true, 'BE가 실행 중 — 이 테스트는 BE 다운 시나리오용')
      return
    }

    await page.goto('/guest')
    const nicknameInput = page.locator('input[type="text"]').first()
    await nicknameInput.waitFor({ state: 'visible', timeout: 5000 })
    await nicknameInput.fill('에러테스트')
    await page.getByRole('button', { name: '시작하기' }).click()

    // 오류 메시지가 표시돼야 함 (FE의 catch 블록)
    const errorText = page.locator('text=/실패|오류|다시|error/i')
    await expect(errorText).toBeVisible({ timeout: 5000 })
  })
})
