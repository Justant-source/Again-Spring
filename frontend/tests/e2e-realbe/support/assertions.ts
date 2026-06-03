import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { RATIO_LEGAL_NOTICE } from './selectors'

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
