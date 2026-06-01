/**
 * 절대 불변 규칙: CrisisModal/CrisisResourceModal은 ESC·바깥 클릭으로 닫히지 않는다.
 *
 * 권위본:
 *   frontend/docs/ux/principles.md §2.3
 *   frontend/docs/ux/flows/08-crisis.md
 *   frontend/README.md — 절대 불변 규칙 #1
 *   shared/docs/policies/crisis-detection.md
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { login, createSession, sendMessage } from '../fixtures/api-helpers'
import { cleanup } from '../fixtures/cleanup'
import { PERSONA_TEST1 } from '../fixtures/personas'
import {
  CRISIS_MODAL,
  CRISIS_MODAL_CLOSE,
  CRISIS_RESOURCE_DIALOG,
  SOS_BUTTON_ROLE,
  CHAT_INPUT_PLACEHOLDER,
  SEND_BUTTON,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('절대 불변: Crisis 모달 dismiss 마찰', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  let sessionId: string

  test.beforeAll(async ({ request }) => {
    cleanup(BASE)
    const token = await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const session = await createSession(request, token)
    sessionId = session.id
  })

  test('CrisisModal은 ESC로 닫히지 않는다', async ({ page }) => {
    await page.goto(`${BASE}/session/chat/${sessionId}`)

    const input = page.getByPlaceholder(CHAT_INPUT_PLACEHOLDER)
    await expect(input).toBeVisible({ timeout: 10_000 })

    await input.fill('자해')
    await page.getByRole('button', SEND_BUTTON).click()

    const modal = page.locator(CRISIS_MODAL)
    await expect(modal).toBeVisible({ timeout: 8_000 })

    // ESC → 닫히지 않음
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
    await expect(modal).toBeVisible()

    // 명시적 닫기 버튼으로만 닫힘
    await page.locator(CRISIS_MODAL_CLOSE).click()
    await expect(modal).not.toBeVisible({ timeout: 3_000 })
  })

  test('CrisisModal은 바깥 영역 클릭으로 닫히지 않는다', async ({ page }) => {
    await page.goto(`${BASE}/session/chat/${sessionId}`)

    const input = page.getByPlaceholder(CHAT_INPUT_PLACEHOLDER)
    await expect(input).toBeVisible({ timeout: 10_000 })

    await input.fill('자살')
    await page.getByRole('button', SEND_BUTTON).click()

    const modal = page.locator(CRISIS_MODAL)
    await expect(modal).toBeVisible({ timeout: 8_000 })

    // backdrop 좌표 클릭 (좌상단 외곽)
    await page.mouse.click(10, 10)
    await page.waitForTimeout(400)
    await expect(modal).toBeVisible()

    // 명시적 닫기
    await page.locator(CRISIS_MODAL_CLOSE).click()
  })

  test.skip('헤더 SOS 버튼 → CrisisResourceModal 표시, ESC·backdrop 무효', async ({ page }) => {
    // V17에서 SOS 버튼을 의도적으로 제거함 (이모지 금지 정책 / 디자인).
    // CrisisResourceModal은 이제 자동 위기감지(ChatInput KeywordGuard)를 통해서만 트리거됨.
    // 모달 자체의 dismiss 마찰(ESC·backdrop 차단)은 아래 두 테스트(CrisisModal)에서 동일하게 검증.
    // 이 케이스는 트리거 방법이 없어진 것이므로 skip 처리.
    void page, sessionId
  })
})
