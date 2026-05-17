import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { RATIO_LEGAL_NOTICE, BLURRED_BUBBLE } from './selectors'

/**
 * ContributionRatio 법적 안내 박스가 표시되고 숨겨지지 않음을 단언.
 * 3회 재확인으로 동적으로 제거되지 않는다는 것까지 검증.
 */
export async function assertLegalNoticeAlwaysVisible(page: Page): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await expect(page.locator(RATIO_LEGAL_NOTICE)).toBeVisible()
    if (i < 2) await page.waitForTimeout(500)
  }
}

/**
 * BlurredBubble이 DOM에 존재하고 A 원문이 포함되지 않음을 단언.
 */
export async function assertBlurredBubbleNoRawText(
  page: Page,
  aMessageContent: string,
): Promise<void> {
  const bubbles = page.locator(BLURRED_BUBBLE)
  await expect(bubbles.first()).toBeVisible()
  const bodyText = await page.locator('body').textContent()
  expect(bodyText).not.toContain(aMessageContent)
}

/**
 * partner-messages 응답에 content 필드가 없음을 단언.
 */
export function assertNoContentField(partnerMessages: Record<string, unknown>[]): void {
  for (const msg of partnerMessages) {
    expect(msg).not.toHaveProperty('content')
    expect(msg).toHaveProperty('charCount')
    expect(msg).toHaveProperty('createdAt')
  }
}
