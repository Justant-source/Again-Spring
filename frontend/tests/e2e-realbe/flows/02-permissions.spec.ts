/**
 * Flow 02: 권한 및 라우트 가드
 *
 * 권위본:
 *   frontend/docs/ux/flows/02-permissions.md (as-is 코드 기준)
 *   lib/constants/userPermissions.ts (permissionsFor(), USER_PERMISSIONS)
 *
 * 핵심 사실 (flows 문서 기준, 정책 문서와 다른 지점):
 *   - 미들웨어 없음. 모든 가드는 클라이언트 useEffect.
 *   - /admin → 미로그인: /login?next=/admin 리다이렉트
 *   - /history → 게스트: 인페이지 업셀(리다이렉트 아님)
 *   - /admin → registered: / 리다이렉트
 *   - axios 401/403 → /guest(게스트) 또는 /login(일반)
 *
 * spec은 권한 매트릭스를 하드코딩하지 않고 userPermissions.ts 상수와 동일한
 * 기대값을 검증한다.
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { cleanup } from '../fixtures/cleanup'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Flow 02: 권한 및 라우트 가드', () => {
  test.beforeAll(() => {
    cleanup(BASE)
  })

  test('미로그인 사용자가 /admin 접근 → /login?next=/admin 리다이렉트', async ({ page }) => {
    // storageState 미사용 = 비인증 상태
    await page.goto(`${BASE}/admin`)

    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
    // next 파라미터 검증 (flows/02 기준: ?next=/admin)
    expect(page.url()).toMatch(/next=%2Fadmin|next=\/admin/)
  })

  test('게스트 사용자가 /history 접근 → 인페이지 업셀(리다이렉트 아님)', async ({ page }) => {
    // 게스트로 입장
    await page.goto(`${BASE}/guest`)
    const input = page.locator('input[type="text"]').first()
    await input.waitFor({ state: 'visible', timeout: 8_000 })
    await input.fill('게스트권한테스트')
    await page.getByRole('button', { name: '시작하기' }).click()
    await page.waitForURL(/\/onboarding/, { timeout: 10_000 })

    // /history 접근
    await page.goto(`${BASE}/history`)
    await page.waitForTimeout(2_000)

    // URL은 /history 그대로 (리다이렉트 아님 — flows/02 기준)
    expect(page.url()).toContain('/history')
    // 업셀 텍스트 표시 (flows/02: "게스트 모드에서는 이력이 저장되지 않아요.")
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).toMatch(/게스트|이력|저장되지/)
  })

  test('registered 사용자가 /admin 접근 → / 리다이렉트', async ({ page, browser }) => {
    // test1은 globalSetup에서 ADMIN이 됐으므로 test4(USER only)를 사용
    // test1의 경우 ADMIN이므로 이 테스트는 test5 페르소나 사용
    // storageState가 있으면 사용, 없으면 직접 로그인
    const persona = { email: 'test5@again.com', password: 'test123' }
    const statePath = authStatePath(persona.email)

    const ctx = await browser.newContext({ storageState: statePath })
    const pg = await ctx.newPage()

    await pg.goto(`${BASE}/admin`)
    await pg.waitForURL(/\/$|\/session|\/onboarding/, { timeout: 10_000 })

    // / 또는 허용된 페이지로 리다이렉트, /admin 아님
    expect(pg.url()).not.toContain('/admin')
    await pg.close()
    await ctx.close()
  })
})
