/**
 * Journey 08: 이메일 인증 + 가입 완주
 *
 * - send-verification 200 스모크 (기존 flow 03)
 * - 실제 가입 1회 완주: send-verification → DB에서 코드 읽기 → /signup 폼 입력 → 가입 성공
 *
 * 이메일 인증 코드는 DB email_verifications 테이블에 평문 저장됨.
 * support/db.ts의 latestVerificationCode()로 읽어 폼에 입력한다.
 *
 * dev 환경: SMTP 실패 시에도 200 반환 (EmailVerificationService dev 폴백).
 * 가입 유저는 teardown(cleanup-test-db.sh §3)에서 자동 삭제.
 */
import { test, expect } from '../support/no-llm-fixture'
import { latestVerificationCode } from '../support/db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
// 일회용 가입 이메일 — teardown이 'e2e-signup%@example.com' 패턴으로 정리
const SIGNUP_EMAIL = `e2e-signup-${Date.now()}@example.com`
const SIGNUP_PASSWORD = 'E2Etest123!'
const SIGNUP_NICKNAME = `E2E${Date.now()}`.slice(0, 10)

// ── A. send-verification 스모크 ───────────────────────────────────
test.describe('Journey 08-A: send-verification 200 스모크', () => {

  test('POST /api/auth/send-verification → 200', async ({ request }) => {
    const resp = await request.post(`${BASE}/api/auth/send-verification`, {
      data: { email: `e2e-smoke-${Date.now()}@example.com` },
    })
    expect(
      resp.status(),
      `send-verification이 200이어야 합니다. 실제: ${resp.status()} — BE 메일 설정/로그 확인`,
    ).toBe(200)
  })
})

// ── B. 실제 가입 완주 ─────────────────────────────────────────────
test.describe('Journey 08-B: 이메일 인증 → 가입 완주', () => {

  test('send-verification → DB 코드 읽기 → /signup 폼 → 가입 성공', async ({ page, request }) => {
    // 1. 인증코드 발송
    const sendResp = await request.post(`${BASE}/api/auth/send-verification`, {
      data: { email: SIGNUP_EMAIL },
    })
    expect(sendResp.status()).toBe(200)

    // 2. DB에서 코드 읽기 (email_verifications 테이블)
    //    dev 환경: SMTP 실패 시 코드를 로그에 출력하고 DB에는 저장됨
    let code: string | null = null
    // 코드 저장까지 최대 3초 대기
    for (let i = 0; i < 6; i++) {
      code = latestVerificationCode(SIGNUP_EMAIL)
      if (code && code.length === 4) break
      await new Promise(r => setTimeout(r, 500))
    }
    expect(code, 'DB에서 인증 코드를 읽어야 합니다. email_verifications 테이블 확인 필요').toBeTruthy()
    expect(code!.length).toBe(4)

    // 3. /signup 폼 접근 → 이메일·코드·닉네임·비밀번호 입력
    await page.goto(`${BASE}/signup`)
    await page.waitForURL(/\/signup/, { timeout: 10_000 })

    // 닉네임 입력
    const nicknameInput = page.getByPlaceholder('닉네임')
    await expect(nicknameInput).toBeVisible({ timeout: 8_000 })
    await nicknameInput.fill(SIGNUP_NICKNAME)

    // 이메일 입력
    await page.getByPlaceholder('이메일').fill(SIGNUP_EMAIL)

    // 인증코드 입력
    const codeInput = page.getByPlaceholder(/인증코드/)
    await expect(codeInput).toBeVisible({ timeout: 5_000 })
    await codeInput.fill(code!)

    // 비밀번호 입력
    const pwInputs = page.locator('input[type="password"]')
    await pwInputs.first().fill(SIGNUP_PASSWORD)
    await pwInputs.nth(1).fill(SIGNUP_PASSWORD)

    // 가입하기 버튼 클릭
    await page.getByRole('button', { name: '가입하기' }).click()

    // 4. 가입 성공 → 로그인 상태로 리다이렉트 (커뮤니티 또는 홈)
    await page.waitForURL(/\/community|\/\?|^\/?$|\/session/, { timeout: 15_000 })

    const token = await page.evaluate(() => localStorage.getItem('again-spring-token'))
    expect(token).toBeTruthy()
  })
})
