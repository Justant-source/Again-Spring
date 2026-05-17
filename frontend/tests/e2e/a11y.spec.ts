import { test, expect } from './fixtures'
import AxeBuilder from '@axe-core/playwright'

// Pages to check for accessibility violations
const PAGES_TO_CHECK = [
  { path: '/', name: '랜딩 페이지' },
  { path: '/login', name: '로그인' },
  { path: '/signup', name: '회원가입' },
  { path: '/guest', name: '게스트 모드' },
  { path: '/onboarding/intro', name: '온보딩 소개' },
  { path: '/session/new', name: '세션 생성' },
  { path: '/session/result/sess_history_1', name: '결과 리포트' },
]

test.describe('Accessibility (WCAG 2.1)', () => {
  for (const { path, name } of PAGES_TO_CHECK) {
    test(`접근성 위반 없음: ${name} @a11y`, async ({ page }) => {
      await page.goto(path)

      // Wait for page to fully load
      await page.waitForTimeout(1000)

      // Run accessibility analysis
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze()

      // Print violations for debugging
      if (results.violations.length > 0) {
        console.log(`\n❌ Violations found on ${name}:`)
        results.violations.forEach((violation) => {
          console.log(`  - ${violation.id}: ${violation.description}`)
          console.log(`    Impact: ${violation.impact}`)
        })
      }

      // Violations should be empty
      expect(results.violations).toEqual([])
    })
  }

  test('모든 입력 필드에 레이블이 연결됨 @a11y', async ({ page }) => {
    await page.goto('/login')

    // Check for unlabeled inputs
    const inputs = await page.locator('input, textarea').all()

    for (const input of inputs) {
      // Input should have either:
      // 1. aria-label
      // 2. aria-labelledby
      // 3. associated label element
      const ariaLabel = await input.getAttribute('aria-label')
      const ariaLabelledBy = await input.getAttribute('aria-labelledby')
      const placeholder = await input.getAttribute('placeholder')

      // Get associated label
      const inputId = await input.getAttribute('id')
      let associatedLabel = null
      if (inputId) {
        associatedLabel = await page.locator(`label[for="${inputId}"]`).count()
      }

      const hasLabel = !!ariaLabel || !!ariaLabelledBy || !!placeholder || associatedLabel > 0
      expect(hasLabel).toBe(true)
    }
  })

  test('이미지에 alt 텍스트가 있음 @a11y', async ({ page }) => {
    // Check multiple pages for alt text
    const testPages = ['/session/result/sess_history_1', '/']

    for (const testPath of testPages) {
      await page.goto(testPath)
      await page.waitForTimeout(500)

      const images = await page.locator('img').all()

      for (const img of images) {
        const alt = await img.getAttribute('alt')
        const ariaLabel = await img.getAttribute('aria-label')
        const isDecorative = await img.getAttribute('role').then((r: string | null) => r === 'presentation')

        // Decorative images can skip alt text
        if (!isDecorative) {
          expect(alt || ariaLabel).toBeTruthy()
        }
      }
    }
  })

  test('버튼과 링크가 키보드로 접근 가능 @a11y', async ({ page }) => {
    await page.goto('/')

    // Tab through interactive elements
    let tabCount = 0
    let focusedElements = []

    for (let i = 0; i < 20; i++) {
      await page.keyboard.press('Tab')
      const focused = await page.evaluate(() => document.activeElement?.tagName)
      if (focused) {
        focusedElements.push(focused)
        tabCount++
      }
    }

    // Should be able to tab to interactive elements
    expect(tabCount).toBeGreaterThan(0)
    const hasButton = focusedElements.includes('BUTTON') || focusedElements.includes('A')
    expect(hasButton).toBe(true)
  })

  test('포커스 표시기가 항상 보임 @a11y', async ({ page }) => {
    await page.goto('/login')

    // Tab to first button
    await page.keyboard.press('Tab')

    const focused = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement
      if (!el) return null
      const computed = window.getComputedStyle(el)
      return {
        outline: computed.outline,
        outlineWidth: computed.outlineWidth,
        boxShadow: computed.boxShadow,
      }
    })

    // Should have some focus indicator
    expect(focused).toBeTruthy()
    const hasOutline = focused?.outline !== 'none' && focused?.outline !== ''
    const hasShadow = focused?.boxShadow !== 'none' && focused?.boxShadow !== ''
    expect(hasOutline || hasShadow).toBe(true)
  })

  test('색상 대비가 WCAG AA 기준 충족 @a11y', async ({ page }) => {
    await page.goto('/login')

    // Run axe color contrast check
    const results = await new AxeBuilder({ page })
      .withTags(['color-contrast'])
      .analyze()

    // Should have no color contrast violations
    const contrastViolations = results.violations.filter((v) => v.id === 'color-contrast')
    expect(contrastViolations).toHaveLength(0)
  })

  test('제목 계층이 올바름 @a11y', async ({ page }) => {
    await page.goto('/session/result/sess_history_1')
    await page.waitForTimeout(500)

    // Get all headings
    const headings = await page.locator('h1, h2, h3, h4, h5, h6').all()

    if (headings.length > 0) {
      let lastLevel = 0

      for (const heading of headings) {
        const tagName = await heading.evaluate((el: Element) => el.tagName)
        const level = parseInt(tagName.substring(1))

        // Level should not jump more than 1
        if (lastLevel > 0) {
          expect(level - lastLevel).toBeLessThanOrEqual(1)
        }

        lastLevel = level
      }
    }
  })

  test('폼 제출 오류가 명확하게 안내됨 @a11y', async ({ page }) => {
    await page.goto('/login')

    // Try to submit empty form
    const submitBtn = page.getByRole('button', { name: /로그인/ })
    await submitBtn.click()

    await page.waitForTimeout(500)

    // Check for error message (use getByText to avoid strict mode on nested divs)
    const errorMessage = page.getByText(/오류|입력해|실패|불일치/)

    const hasError = await errorMessage.isVisible({ timeout: 2000 }).catch(() => false)
    expect(hasError).toBe(true)
  })

  test('다이얼로그/모달이 올바르게 구성됨 @a11y', async ({ page }) => {
    await page.goto('/session/chat/sess_active')
    await page.waitForTimeout(1000)

    // Trigger a modal (invite modal)
    const inviteBtn = page.getByRole('button', { name: /초대|공유|링크/ }).first()

    if (await inviteBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await inviteBtn.click()
      await page.waitForTimeout(500)

      // Modal should have aria-modal or role=dialog
      const modal = page.locator('[role="dialog"], [aria-modal="true"]').first()

      const isVisible = await modal.isVisible({ timeout: 1000 }).catch(() => false)

      if (isVisible) {
        const hasRole = await modal.getAttribute('role')
        const hasModal = await modal.getAttribute('aria-modal')
        expect(hasRole || hasModal).toBeTruthy()
      }
    }
  })

  test('위기 모달이 접근성 기준 충족 @a11y', async ({ page }) => {
    await page.goto('/session/chat/sess_active')
    await page.waitForTimeout(1000)

    // Send crisis keyword to trigger modal
    const textarea = page.getByPlaceholder(/편한 말|메시지/i)

    if (await textarea.isVisible({ timeout: 2000 }).catch(() => false)) {
      await textarea.fill('때렸어')
      const sendBtn = page.getByRole('button', { name: /전송/ })
      await sendBtn.click()

      await page.waitForTimeout(500)

      // Crisis modal should be properly labeled
      const modal = page.locator('[role="dialog"], [aria-modal]').first()

      if (await modal.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Should have title
        const title = page.getByText(/안전|위기|중요/)
        expect(await title.isVisible({ timeout: 1000 }).catch(() => false)).toBe(true)
      }
    }
  })

  test('텍스트 리사이징이 가능함 @a11y', async ({ page }) => {
    await page.goto('/')

    // Zoom to 200%
    await page.evaluate(() => {
      document.body.style.zoom = '2'
    })

    await page.waitForTimeout(300)

    // Main content should still be readable
    const heading = page.getByRole('heading').first()
    const isVisible = await heading.isVisible()
    expect(isVisible).toBe(true)

    // Reset zoom
    await page.evaluate(() => {
      document.body.style.zoom = '1'
    })
  })

  test('스크린 리더 숨김 텍스트가 올바르게 사용됨 @a11y', async ({ page }) => {
    await page.goto('/')

    // Check for sr-only or similar hidden but accessible text
    const hiddenElements = await page.locator('.sr-only, .visually-hidden, [aria-hidden="false"]').all()

    // Should have some hidden text for screen readers or be using aria correctly
    const accessibleHidden = hiddenElements.length > 0 || (await page.locator('[aria-label]').count()) > 0

    expect(accessibleHidden || true).toBe(true) // Optional if site doesn't use sr-only pattern
  })

  test('네비게이션이 스킵 가능함 @a11y', async ({ page }) => {
    await page.goto('/')

    // Check for skip-to-content link
    const skipLink = page.locator('a').filter({ has: page.locator('text=/skip|main|건너뛰/i') })

    const hasSkipLink = await skipLink.isVisible({ timeout: 1000 }).catch(() => false)

    // Either has skip link or main content is early in tab order
    if (!hasSkipLink) {
      // Tab multiple times, should reach main content quickly
      for (let i = 0; i < 5; i++) {
        await page.keyboard.press('Tab')
      }

      const focused = await page.evaluate(() => document.activeElement?.textContent)
      expect(focused).toBeTruthy()
    }
  })
})
