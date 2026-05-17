import type { APIRequestContext, Page } from '@playwright/test'
import { getMessages } from '../fixtures/api-helpers'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

/**
 * LLM 응답(mediator 메시지) 도착까지 폴링.
 * BE DEFAULT_WAIT = 최대 25s → timeoutMs 기본 30_000.
 */
export async function waitForMediatorMessage(
  request: APIRequestContext,
  token: string,
  sessionId: string,
  afterTimestamp = 0,
  timeoutMs = 30_000,
  intervalMs = 1_500,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const msgs = await getMessages(request, token, sessionId, afterTimestamp)
    if ((msgs as Record<string, unknown>[]).some((m) => String(m.sender).startsWith('MEDIATOR'))) {
      return true
    }
    await new Promise((r) => setTimeout(r, intervalMs))
  }
  return false
}

/**
 * 특정 URL 패턴이 될 때까지 대기.
 */
export async function waitForUrlPattern(
  page: Page,
  pattern: RegExp | string,
  timeoutMs = 15_000,
): Promise<void> {
  await page.waitForURL(pattern, { timeout: timeoutMs })
}

/**
 * element가 보이거나 timeoutMs 초과시 false 반환.
 */
export async function waitForVisible(
  page: Page,
  selector: string,
  timeoutMs = 5_000,
): Promise<boolean> {
  return page
    .locator(selector)
    .waitFor({ state: 'visible', timeout: timeoutMs })
    .then(() => true)
    .catch(() => false)
}

/**
 * API 응답 폴링 (리포트 생성 등).
 */
export async function pollUntilOk(
  request: APIRequestContext,
  url: string,
  headers: Record<string, string>,
  timeoutMs = 90_000,
  intervalMs = 3_000,
): Promise<Record<string, unknown> | null> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const resp = await request.get(url, { headers })
    if (resp.ok()) return resp.json()
    await new Promise((r) => setTimeout(r, intervalMs))
  }
  return null
}
