import type { BrowserContext } from '@playwright/test'
import path from 'path'
import fs from 'fs'
import type { Persona } from './personas'

export const AUTH_STATE_DIR = path.resolve('.auth')

export function authStatePath(email: string): string {
  return path.join(AUTH_STATE_DIR, `${email.replace('@', '_at_')}.json`)
}

/**
 * 페르소나 로그인 후 storageState를 .auth/<email>.json으로 저장.
 * globalSetup에서만 호출. 이후 spec은 test.use({ storageState })로 즉시 로그인 상태 진입.
 * Rate Limit(5/min) 회피를 위해 매 spec마다 호출하지 않는다.
 */
export async function saveAuthState(
  context: BrowserContext,
  persona: Persona,
  baseURL: string,
): Promise<void> {
  const resp = await context.request.post(`${baseURL}/api/auth/login`, {
    data: { email: persona.email, password: persona.password },
  })
  if (!resp.ok()) {
    throw new Error(`Login failed for ${persona.email}: ${resp.status()} ${await resp.text()}`)
  }
  const loginData = await resp.json()
  const accessToken: string = loginData.token?.accessToken
  const user = loginData.user ?? null

  const page = await context.newPage()
  await page.goto(baseURL)
  await page.evaluate(
    ({ t, u }: { t: string; u: unknown }) => {
      localStorage.setItem('again-spring-token', t)
      // Zustand persist 스토어에 user 데이터 주입 — user=null이면 프로필 페이지가 login으로 redirect됨
      if (u) {
        localStorage.setItem(
          'again-spring-user',
          JSON.stringify({ state: { user: u, _hasHydrated: true }, version: 0 }),
        )
      }
    },
    { t: accessToken, u: user },
  )
  await page.close()

  if (!fs.existsSync(AUTH_STATE_DIR)) fs.mkdirSync(AUTH_STATE_DIR, { recursive: true })
  await context.storageState({ path: authStatePath(persona.email) })
}
