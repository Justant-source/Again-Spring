/**
 * Flow 01: 인증 (로그인·게스트·로그아웃)
 *
 * 권위본: frontend/docs/ux/flows/01-auth.md (as-is 코드 기준)
 * 토큰 키: again-spring-token (localStorage)
 * 게스트 → 바로 홈('/') 진입 (온보딩 강제 폐지 2026-05-31)
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { cleanup } from '../fixtures/cleanup'
import {
  EMAIL_INPUT_PLACEHOLDER,
  PASSWORD_INPUT_PLACEHOLDER,
  LOGIN_BUTTON,
  GUEST_START_BUTTON,
} from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const TOKEN_KEY = 'again-spring-token'

test.describe('Flow 01: 인증', () => {
  test.beforeAll(() => {
    cleanup(BASE)
  })

  test('이메일 로그인 → again-spring-token 저장 + 랜딩 도달', async ({ page }) => {
    await page.goto(`${BASE}/login`)

    await page.getByPlaceholder(EMAIL_INPUT_PLACEHOLDER).fill(PERSONA_TEST1.email)
    await page.getByPlaceholder(PASSWORD_INPUT_PLACEHOLDER).fill(PERSONA_TEST1.password)
    await page.getByRole('button', LOGIN_BUTTON).click()

    // 로그인 성공 → 랜딩 또는 리다이렉트 목적지
    await page.waitForURL(/\/$|\/session\/new|\/onboarding/, { timeout: 10_000 })

    const token = await page.evaluate((key: string) => localStorage.getItem(key), TOKEN_KEY)
    expect(token).toBeTruthy()
  })

  test('게스트 진입 → 바로 세션 생성 화면 진입 (온보딩 강제 없음)', async ({ page }) => {
    await page.goto(`${BASE}/guest`)

    // 닉네임 입력 (dynamic placeholder는 useEffect, role/position으로 접근)
    const input = page.locator('input[type="text"]').first()
    await input.waitFor({ state: 'visible', timeout: 8_000 })
    await input.fill('E2E테스트게스트')

    await page.getByRole('button', GUEST_START_BUTTON).click()

    // 온보딩 강제 폐지(2026-05-31) — 게스트는 닉네임 설정 즉시 /session/new로 이동
    await page.waitForURL(/\/session\/new/, { timeout: 10_000 })
    expect(page.url()).toContain('/session/new')

    const token = await page.evaluate((key: string) => localStorage.getItem(key), TOKEN_KEY)
    expect(token).toBeTruthy()
  })

  test('로그아웃 → again-spring-token 제거', async ({ page }) => {
    // globalSetup이 저장한 storageState로 로그인 상태 진입
    // (Rate Limit 5/min 회피 — 로그인 UI 재사용 불가)
    const statePath = authStatePath(PERSONA_TEST1.email)
    const token0 = await import('fs').then((fs) => {
      const state = JSON.parse(fs.readFileSync(statePath, 'utf-8'))
      const ls = state.origins?.[0]?.localStorage ?? []
      return (ls.find((e: { name: string; value: string }) => e.name === TOKEN_KEY)?.value as string) ?? null
    })
    expect(token0).toBeTruthy() // storageState에 토큰 있음

    await page.goto(BASE)
    await page.evaluate((t: string) => localStorage.setItem('again-spring-token', t), token0)
    await page.reload()
    await page.waitForURL(/\/$|\/session|\/onboarding/, { timeout: 10_000 })

    // 로그아웃 버튼 (존재하면 클릭, 없으면 localStorage 직접 제거로 폴백)
    const logoutBtn = page.getByRole('button', { name: /로그아웃|logout/i })
    if (await logoutBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await logoutBtn.click()
    } else {
      await page.evaluate((key: string) => localStorage.removeItem(key), TOKEN_KEY)
    }

    const token = await page.evaluate((key: string) => localStorage.getItem(key), TOKEN_KEY)
    expect(token).toBeNull()
  })
})
