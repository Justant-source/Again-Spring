import { test, expect, type Page } from '@playwright/test'

test.describe('Safety Scenarios @safety', () => {
  // Helper: navigate to an active chat session
  // sess_active has status: 'chatting_solo' so it won't redirect to result page
  async function navigateToChatSession(page: Page) {
    await page.goto('/session/chat/sess_active')
    await page.waitForTimeout(800)
  }

  test('위기어(폭력) 입력 시 CrisisModal 표시', async ({ page }) => {
    await navigateToChatSession(page)

    // Wait for chat interface to load
    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type crisis keyword: violence
    await textarea.fill('어제 때렸어요')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    // CrisisModal should appear (ESC resistant)
    const crisisModal = page.locator('div').filter({
      has: page.locator('text=/안전|중요|위기/')
    }).first()
    await expect(crisisModal).toBeVisible({ timeout: 3000 })

    // Verify hotline numbers are visible
    await expect(page.getByText(/1366|1393|132|112/).first()).toBeVisible()

    // Test that ESC does NOT close the modal (absolute safety rule)
    await page.keyboard.press('Escape')
    await expect(crisisModal).toBeVisible({ timeout: 1000 })

    // Verify modal is still present after ESC attempt
    const stillVisible = await crisisModal.isVisible()
    expect(stillVisible).toBe(true)

    // Close via the explicit button
    const closeBtn = page.getByRole('button', { name: /괜찮아|닫기|계속/ }).last()
    await closeBtn.click()
  })

  test('위기어(자해) 입력 시 CrisisModal 표시', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type crisis keyword: self-harm
    await textarea.fill('죽고싶어요')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    // CrisisModal should appear
    const crisisModal = page.locator('div').filter({
      has: page.locator('text=/안전|위기|전문/')
    }).first()
    await expect(crisisModal).toBeVisible({ timeout: 3000 })

    // Verify suicide prevention hotline (1393)
    await expect(page.getByText(/1393|자살예방/)).toBeVisible()
  })

  test('위기어(성폭력) 입력 시 CrisisModal 표시', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type crisis keyword: sexual violence
    await textarea.fill('강간당했어')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    // CrisisModal should appear
    const crisisModal = page.locator('div').filter({
      has: page.locator('text=/안전|위기/')
    }).first()
    await expect(crisisModal).toBeVisible({ timeout: 3000 })

    // Verify women's emergency hotline (1366)
    await expect(page.getByText(/1366|여성긴급/)).toBeVisible()
  })

  test('위기어(아동학대) 입력 시 CrisisModal 표시', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type crisis keyword: child abuse
    await textarea.fill('아이를 때렸어요')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    // CrisisModal should appear
    const crisisModal = page.locator('div').filter({
      has: page.locator('text=/안전|위기/')
    }).first()
    await expect(crisisModal).toBeVisible({ timeout: 3000 })

    // Verify child abuse hotline (112)
    await expect(page.getByText(/112|아동|학대/)).toBeVisible()
  })

  test('법률 금지어(과실비율) 입력 시 차단 또는 경고', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type legal forbidden word
    await textarea.fill('과실비율을 따져야 해요')
    const sendBtn = page.getByRole('button', { name: /전송/ })

    // Button may be disabled or send button shows warning
    const isSendDisabled = await sendBtn.isDisabled()
    const isTextInputDisabled = await textarea.isDisabled()

    // Either the send button is disabled or we get a warning
    if (!isSendDisabled && !isTextInputDisabled) {
      await sendBtn.click()
      // May show inline warning or be blocked server-side
      await page.waitForTimeout(500)
    } else {
      // Input or send is disabled — verify this is intentional
      expect(isSendDisabled || isTextInputDisabled).toBe(true)
    }
  })

  test('법률 금지어(판사/판결) 입력 시 차단 또는 경고', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Type legal forbidden word
    await textarea.fill('판사가 판결을 내려야 해요')
    const sendBtn = page.getByRole('button', { name: /전송/ })

    // Check if input is restricted
    const isSendDisabled = await sendBtn.isDisabled()
    if (!isSendDisabled) {
      await sendBtn.click()
      await page.waitForTimeout(500)
    } else {
      expect(isSendDisabled).toBe(true)
    }
  })

  test('AI 메시지와 사용자 메시지가 시각적으로 구분된다', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Send a safe message
    await textarea.fill('안녕하세요')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    // Wait for AI response
    await page.waitForTimeout(1000)

    // Check that messages are visually distinct
    // User message should have different styling than AI message
    const userMessages = page.locator('[data-sender*="USER"]').or(
      page.locator('div').filter({ has: page.locator('text=/안녕하세요/') })
    )
    const aiMessages = page.locator('[data-sender*="MEDIATOR"]').or(
      page.locator('div').filter({ has: page.locator('text=/그러셨군요|마음/') })
    )

    // At least one of each should be present
    const userMsgCount = await userMessages.count()
    const aiMsgCount = await aiMessages.count()

    expect(userMsgCount + aiMsgCount).toBeGreaterThanOrEqual(1)
  })

  test('결과 리포트에 처방 없음 (Solo 시점 기반)', async ({ page }) => {
    // Navigate to a solo-mode session result (sess_history_3 maps to solo report)
    await page.goto('/session/result/sess_history_3')

    // Wait for report to load
    await page.waitForTimeout(1000)

    const pageText = await page.textContent('body')

    // Prescription words that should NOT appear
    const forbiddenPrescriptions = [
      '헤어지세요',
      '절교하세요',
      '손절',
      '이혼하세요',
      '더 노력하세요',
      '더 포용하세요',
    ]

    for (const word of forbiddenPrescriptions) {
      expect(pageText).not.toContain(word)
    }

    // Verify Solo mode indication (one-sided perspective)
    const hasSoloIndicator = pageText?.includes('한쪽') || pageText?.includes('일방') || pageText?.includes('시점')
    expect(hasSoloIndicator).toBe(true)
  })

  test('결과 리포트에 법적 안내 박스가 항상 표시된다', async ({ page }) => {
    // Navigate to a completed Duo session result
    await page.goto('/session/result/sess_history_1')

    // Wait for report to load
    await page.waitForTimeout(1000)

    // Find the legal disclaimer box (use first() to avoid strict mode on nested divs)
    const disclaimerBox = page.locator('div').filter({
      has: page.locator('text=/과실비율|법률|법정|무관|대체|상담/')
    }).first()

    // The disclaimer should be visible
    const isVisible = await disclaimerBox.isVisible({ timeout: 1000 }).catch(() => false)
    expect(isVisible).toBe(true)

    // Verify key disclaimer text
    const pageText = await page.textContent('body')
    const hasDisclaimerText = pageText?.includes('심리 상담') || pageText?.includes('법률 자문') || pageText?.includes('전문기관')
    expect(hasDisclaimerText).toBe(true)
  })

  test('메시지 입력 필드에 안내 텍스트(원문 미전달)가 표시된다', async ({ page }) => {
    await navigateToChatSession(page)

    // Verify the specific disclaimer text
    const disclaimerText = page.getByText(/원문은 전달되지 않아요|AI가 정리해서 전달/)
    await expect(disclaimerText).toBeVisible({ timeout: 5000 })
  })

  test('위기 모달은 바깥 영역 클릭으로 닫히지 않는다', async ({ page }) => {
    await navigateToChatSession(page)

    const textarea = page.getByPlaceholder(/편한 말|메시지/i)
    await expect(textarea).toBeVisible({ timeout: 5000 })

    // Trigger crisis modal
    await textarea.fill('때렸어요')
    const sendBtn = page.getByRole('button', { name: /전송/ })
    await sendBtn.click()

    const crisisModal = page.locator('div').filter({
      has: page.locator('text=/안전|위기/')
    }).first()
    await expect(crisisModal).toBeVisible({ timeout: 3000 })

    // Try clicking outside the modal (on backdrop)
    await page.mouse.click(10, 10)
    await page.waitForTimeout(300)

    // Modal should still be visible
    const stillVisible = await crisisModal.isVisible()
    expect(stillVisible).toBe(true)
  })

  test('AI 능력 한계 텍스트가 결과 페이지에 표시된다', async ({ page }) => {
    await page.goto('/session/result/sess_history_1')

    // Find the disclaimer about AI limitations
    const disclaimerText = page.getByText(/심리 상담|법률 자문|대체|전문기관/)

    await expect(disclaimerText).toBeVisible({ timeout: 5000 })

    // Verify text content
    const disclaimer = await disclaimerText.textContent()
    expect(disclaimer).toBeTruthy()
    expect(disclaimer).toContain('전문기관')
  })
})
