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

  test('헤더 SOS 버튼 → CrisisResourceModal 표시, ESC·backdrop 무효', async ({ page }) => {
    await page.goto(`${BASE}/session/chat/${sessionId}`)
    await expect(page.getByPlaceholder(CHAT_INPUT_PLACEHOLDER)).toBeVisible({ timeout: 10_000 })

    // SOS 버튼 클릭 (aria-label)
    await page.getByRole('button', SOS_BUTTON_ROLE).click()

    const dialog = page.locator(CRISIS_RESOURCE_DIALOG)
    await expect(dialog).toBeVisible({ timeout: 5_000 })

    // ESC → 닫히지 않음
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
    await expect(dialog).toBeVisible()

    // backdrop 클릭 → 닫히지 않음
    await page.mouse.click(10, 10)
    await page.waitForTimeout(400)
    await expect(dialog).toBeVisible()

    // 명시적 닫기 버튼
    await page.getByRole('button', { name: '닫기' }).click()
    await expect(dialog).not.toBeVisible({ timeout: 3_000 })
  })
})
