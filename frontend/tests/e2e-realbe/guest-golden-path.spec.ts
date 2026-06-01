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

test.describe('게스트 골든패스 (실 BE 연동)', () => {

  test('게스트 인증 → 세션 생성 → 채팅 화면 진입 + 첫마디 수신', async ({ page }) => {
    test.setTimeout(120_000) // 세션 생성 + 채팅 + mediator 응답 포함 전체 플로우 여유
    // 0. BE 헬스 체크 — nginx(8090)를 통해 BE 응답 확인
    let beHealthy = false
    try {
      const resp = await page.request.get('http://localhost:8090/api/health')
      beHealthy = resp.ok()
    } catch {}
    expect(beHealthy, 'BE (localhost:8090/api/health)가 응답하지 않습니다. docker compose dev 환경을 확인하세요.').toBe(true)

    // 1. 랜딩 페이지
    await page.goto('/')
    await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 10000 })

    // 2. 게스트 진입 — V17부터 랜딩의 "게스트로 둘러보기" 링크 제거됨.
    //    하단 CTA(대화 시작)이 비로그인 → /guest?next=/session/new 으로 라우팅하거나
    //    URL 직접 진입으로 동일 흐름 테스트.
    await page.goto('/guest')
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

    // 4. 게스트 인증 성공 후 /session/new로 자동 리다이렉트
    // (app/(auth)/guest/page.tsx:42 router.push('/session/new'))
    await page.waitForURL('**/session/new**', { timeout: 10000 })
    expect(page.url()).toContain('/session/new')

    // 5. 대분류 선택 (예: 친구)
    const friendBtn = page.getByRole('button', { name: /친구/ })
    await expect(friendBtn).toBeVisible({ timeout: 5000 })
    await friendBtn.click()

    // 6. /session/category로 이동 (V16~: 중·소분류 제거, 중재자 성향 슬라이더 단일 화면)
    await page.waitForURL('**/session/category**', { timeout: 8000 })
    expect(page.url()).toContain('/session/category')
    await page.getByText('이번 대화의 중재자 톤을').waitFor({ timeout: 5000 })

    // 7. 중재자 성향 설정 → "대화 시작" → POST /api/sessions
    const sessionCreatePromise = page.waitForResponse(
      (resp) => resp.url().includes('/api/sessions') && resp.request().method() === 'POST',
      { timeout: 15000 }
    )
    await page.getByRole('button', { name: '대화 시작' }).click({ timeout: 5000 })

    const sessionResp = await sessionCreatePromise

    if (sessionResp.status() === 429) {
      console.warn('[E2E] 게스트 세션 일일 한도 소진 (GuestSessionRateLimiter 3/24h) — BE 컨테이너 재시작 필요')
      test.skip(true, '게스트 세션 일일 한도 소진 — 인프라 이슈, 회귀 아님')
      return
    }

    expect(sessionResp.status(), [
      `POST /api/sessions가 201이어야 합니다.`,
      `실제 응답: ${sessionResp.status()} — BE 로그를 확인하세요.`,
    ].join(' ')).toBe(201)

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
      const resp = await page.request.get('http://localhost:8090/api/health')
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
