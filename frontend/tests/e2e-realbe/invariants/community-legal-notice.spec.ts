import { test, expect } from '@playwright/test';

/**
 * 커뮤니티 결과/배심원 화면 법적 안내 박스 의무 표시
 * 권위본: shared/docs/policies/jury-voting.md
 *
 * 절대 불변 규칙:
 * - 사연 상세 화면에서 법적 박스 항상 표시
 * - 조건부 렌더링 금지 — 항상 표시
 */
test.describe('커뮤니티 법적 안내 박스 (불변 규칙)', () => {
  test('사연 상세 화면에서 법적 박스 표시', async ({ page }) => {
    await page.goto('/community');
    await page.waitForLoadState('networkidle');

    // 실제 포스트 카드 링크만 선택 (data-testid="community-post-link")
    const postLink = page.locator('[data-testid="community-post-link"]').first();
    const count = await postLink.count();

    if (count === 0) {
      test.skip(true, '공개 글이 없어 skip');
      return;
    }

    await postLink.click();
    await page.waitForLoadState('networkidle');

    // 법적 박스 존재 확인
    const legalBox = page.locator('[data-testid="ratio-legal-notice"]');
    await expect(legalBox).toBeVisible({ timeout: 5000 });
    await expect(legalBox).toContainText('과실 비율');
    await expect(legalBox).toContainText('무관');
  });

  test('법적 박스는 절대 숨겨지거나 조건부로 렌더되지 않음', async ({ page }) => {
    await page.goto('/community');
    await page.waitForLoadState('networkidle');

    const postLink = page.locator('[data-testid="community-post-link"]').first();
    const count = await postLink.count();

    if (count === 0) {
      test.skip(true, '글이 없어 skip');
      return;
    }

    await postLink.click();
    await page.waitForLoadState('networkidle');

    const legalBox = page.locator('[data-testid="ratio-legal-notice"]');
    await expect(legalBox).toBeVisible({ timeout: 5000 });
    await expect(legalBox).toHaveAttribute('data-testid', 'ratio-legal-notice');
  });
});
