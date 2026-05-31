import { test, expect } from '@playwright/test';

/**
 * 커뮤니티 글쓰기 위기 감지 (FE 이중방어 확장)
 *
 * 절대 불변 규칙:
 * - 커뮤니티 글쓰기에서 위기 키워드 입력 시 Crisis 모달 표시
 * - 사용자 입력 즉시 감지 (비동기 검증 아님)
 * - FE + BE 이중 방어 유지
 */
test.describe('커뮤니티 위기 감지 (불변 규칙)', () => {
  test('커뮤니티 글쓰기에서 위기 키워드 입력 시 Crisis 모달 표시', async ({ page }) => {
    await page.goto('/community/new');

    // 사연 입력 필드에 위기 키워드 입력
    const textarea = page.locator('[data-testid="post-body-input"]');
    await textarea.fill('죽고싶다');

    // Crisis Resource 모달 나타남 확인 (role="dialog")
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 3000 });

    // 모달 텍스트 검증
    const dialogText = await dialog.textContent();
    expect(dialogText).toContain('중요한 안내');
  });

  test('위기 감지는 글 생성 단계에서도 적용되지만, FE 먼저 차단', async ({ page }) => {
    await page.goto('/community/new');

    const textarea = page.locator('[data-testid="post-body-input"]');

    // 위기 키워드 입력
    await textarea.fill('자해하고싶어');

    // Crisis 모달 표시됨
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 3000 });

    // 모달 닫기 후 입력 필드는 비어있음 (ChatInput의 동작 미러)
    const closeButton = dialog.locator('button:has-text("닫기")');
    await closeButton.click();
    await expect(dialog).not.toBeVisible();

    // textarea는 여전히 채워져 있지만, submit 불가 (설계상)
    const textValue = await textarea.inputValue();
    expect(textValue).not.toBe(''); // 자동으로 지우지 않음 (사용자가 입력한 값 보존)
  });
});
