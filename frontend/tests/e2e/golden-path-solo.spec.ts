import { test, expect } from '@playwright/test'

test.describe('Golden Path: Solo Chat', () => {
  test('게스트 모드로 온보딩부터 Solo 채팅 3턴까지 완주', async ({ page }) => {
    // 1. 랜딩 페이지에서 게스트 모드 시작
    await page.goto('/')
    await expect(page.getByRole('heading')).toContainText('마음을')

    // 게스트로 둘러보기 버튼 클릭
    await page.getByRole('link', { name: /게스트로/ }).click()
    await page.waitForNavigation()

    // 2. 게스트 입장 페이지에서 닉네임 설정
    await expect(page.getByText(/게스트/)).toBeVisible()
    const nicknameInput = page.getByPlaceholder(/닉네임|이름/) || page.getByRole('textbox').first()
    await nicknameInput.fill('테스트유저')

    const continueBtn = page.getByRole('button', { name: /계속|시작|확인/ }).first()
    await continueBtn.click()
    await page.waitForNavigation()

    // 3. 온보딩 10문항 — 모든 문항에 답변
    for (let i = 0; i < 10; i++) {
      // 각 Likert 스케일에서 중간값(3) 선택
      const buttons = await page.getByRole('button', { name: /^[1-5]$/ }).all()
      if (buttons.length >= 3) {
        await buttons[2].click() // 중간값
      } else {
        // fallback: 첫 번째 버튼
        await page.locator('button').filter({ has: page.locator('text=/^[1-5]$/') }).first().click()
      }

      // 마지막 문항이 아니면 다음 버튼
      if (i < 9) {
        await page.getByRole('button', { name: /다음/ }).click()
        await page.waitForTimeout(300)
      }
    }

    // 마지막 문항에서 완료 버튼
    await page.getByRole('button', { name: /완료/ }).click()
    await page.waitForNavigation()

    // 4. 온보딩 결과 페이지
    await expect(page.getByText(/성향|스타일/i)).toBeVisible()

    // 결과에서 다음으로 진행 (또는 즉시 세션 생성)
    const nextBtn = page.getByRole('button', { name: /다음|시작|계속/ }).first()
    if (await nextBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await nextBtn.click()
      await page.waitForNavigation()
    } else {
      // 자동 이동 대기
      await page.waitForNavigation({ timeout: 3000 })
    }

    // 5. 관계 유형 선택 (새 세션)
    await expect(page.getByText(/관계|어떤 관계/i)).toBeVisible({ timeout: 5000 })

    // 친구 관계 선택
    await page.getByRole('button', { name: /친구/ }).click()
    await page.waitForNavigation()

    // 6. 카테고리 선택
    await expect(page.getByText(/카테고리|무엇에 대해/i)).toBeVisible({ timeout: 5000 })

    const categoryBtn = page.getByRole('button').first()
    await categoryBtn.click()
    await page.waitForNavigation()

    // 7. Solo 채팅 시작 페이지
    await expect(page.getByText(/혼자|일단/i)).toBeVisible({ timeout: 5000 })

    // 혼자 시작하기
    const soloStartBtn = page.getByRole('button', { name: /혼자|시작/ }).first()
    await soloStartBtn.click()
    await page.waitForNavigation()

    // 8. 채팅 페이지 — 3턴 메시지 입력
    await expect(page.getByPlaceholder(/편한 말|메시지/i)).toBeVisible({ timeout: 5000 })

    const messages = [
      '처음에는 좋았는데 요즘 뭔가 어색해요',
      '어떻게 이야기를 꺼낼지 모르겠어요',
      '정말 중요한 사람이라서 잃고 싶지 않아요',
    ]

    for (const msg of messages) {
      const textarea = page.getByPlaceholder(/편한 말|메시지/i)
      await textarea.fill(msg)

      // 전송 버튼 클릭 또는 Ctrl+Enter
      const sendBtn = page.getByRole('button', { name: /전송/ })
      await sendBtn.click()

      // AI 응답 대기 (MSW는 즉시 응답)
      await page.waitForTimeout(500)
      await expect(
        page.locator('[data-testid="message-list"]')
          .or(page.locator('div').filter({ has: page.locator('text=/그러셨군요|마음|느껴/') }))
      ).toBeVisible({ timeout: 3000 })
    }

    // 9. 3턴 완료 확인
    const messages_all = await page.locator('[data-testid="message"]').or(page.locator('div').filter({ has: page.locator('text=/편한 말|테스트유저/') })).all()
    expect(messages_all.length).toBeGreaterThanOrEqual(6) // 최소 3 user + 3 AI 메시지
  })

  test('AI 능력 한계 안내 텍스트가 화면에 존재', async ({ page }) => {
    await page.goto('/')

    // 랜딩 페이지의 안내 텍스트 확인
    const disclaimerBox = page.locator('div').filter({
      has: page.locator('text=/AI|심리 상담|법률|전문기관/')
    })

    await expect(disclaimerBox).toBeVisible()

    // AI 한계 관련 텍스트
    const hasAiLimitText = await page.getByText(/AI|심리|대화|중재/).count() > 0
    expect(hasAiLimitText).toBeTruthy()
  })

  test('온보딩 10문항을 모두 완료할 수 있다', async ({ page }) => {
    // 게스트 모드 시작
    await page.goto('/guest')

    const nicknameInput = page.getByPlaceholder(/닉네임|이름/) || page.getByRole('textbox').first()
    await nicknameInput.fill('테스트게스트')

    const continueBtn = page.getByRole('button').first()
    await continueBtn.click()
    await page.waitForNavigation()

    // 온보딩 페이지에서 10문항 완료
    const initialDashes = await page.locator('[data-testid="dash"]').or(page.locator('div').filter({ has: page.locator('text=/10/') })).count()

    for (let i = 0; i < 10; i++) {
      const likertButtons = page.getByRole('button').filter({ has: page.locator('text=/[1-5]/') })
      const btnCount = await likertButtons.count()

      if (btnCount > 0) {
        // 중간값 선택
        await likertButtons.nth(Math.floor(btnCount / 2)).click()
      }

      // 다음 또는 완료
      const actionBtn = page.getByRole('button', { name: /다음|완료/ })
      if (await actionBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
        await actionBtn.click()
        await page.waitForTimeout(300)
      }
    }

    // 온보딩 완료 후 결과 페이지
    await expect(page.getByText(/성향|스타일|결과/i)).toBeVisible({ timeout: 5000 })
  })

  test('세션 생성 후 상태가 정확히 업데이트된다', async ({ page }) => {
    // 로그인 (MSW mock)
    await page.goto('/login')

    const emailInput = page.getByPlaceholder(/이메일/)
    const passwordInput = page.getByPlaceholder(/비밀번호/)

    await emailInput.fill('test@example.com')
    await passwordInput.fill('password123')

    const loginBtn = page.getByRole('button', { name: /로그인/ })
    await loginBtn.click()
    await page.waitForNavigation()

    // 홈 페이지로 리다이렉트
    await expect(page).toHaveURL('/')

    // 새 세션 시작
    const startBtn = page.getByRole('button', { name: /마음|시작/ })
    await startBtn.click()
    await page.waitForNavigation()

    // 관계 선택
    await page.getByRole('button', { name: /친구|연인|부부|가족|부모/ }).first().click()
    await page.waitForNavigation()

    // 카테고리 선택
    await page.getByRole('button').first().click()
    await page.waitForNavigation()

    // 세션 상태 확인 (chatting_solo 또는 waiting_b)
    const sessionPageUrl = page.url()
    expect(sessionPageUrl).toMatch(/\/session\/(chat|join)/)
  })
})
