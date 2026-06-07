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

  test('send-verification → DB 코드 읽기 → /signup UI → 가입 성공', async ({ page }) => {
    // 1. /signup 페이지 접근
    await page.goto(`${BASE}/signup`)
    await page.waitForURL(/\/signup/, { timeout: 10_000 })

    // 2. 닉네임 입력
    const nicknameInput = page.getByPlaceholder('닉네임')
    await expect(nicknameInput).toBeVisible({ timeout: 8_000 })
    await nicknameInput.fill(SIGNUP_NICKNAME)

    // 3. 이메일 입력
    await page.getByPlaceholder('이메일').fill(SIGNUP_EMAIL)

    // 4. "인증코드 전송" 버튼 클릭 → API 호출 + codeSent=true → 코드 입력란 표시
    const sendBtn = page.getByRole('button', { name: /인증코드 전송|재전송/ })
    await expect(sendBtn).toBeVisible({ timeout: 5_000 })
    await sendBtn.click()

    // 5. 코드 입력란이 나타날 때까지 대기 (codeSent=true 시 표시)
    const codeInput = page.getByPlaceholder('인증코드 4자리')
    await expect(codeInput).toBeVisible({ timeout: 10_000 })

    // 6. DB에서 코드 읽기 (email_verifications 테이블에 평문 저장)
    let code: string | null = null
    for (let i = 0; i < 10; i++) {
      code = latestVerificationCode(SIGNUP_EMAIL)
      if (code && code.length === 4) break
      await new Promise(r => setTimeout(r, 500))
    }
    expect(code, 'DB에서 인증 코드를 읽어야 합니다. email_verifications 테이블 확인 필요').toBeTruthy()
    expect(code!.length).toBe(4)

    // 7. 코드 입력
    await codeInput.fill(code!)

    // 8. 비밀번호 입력 (strict mode: '비밀번호'는 2개 매칭 → .first() 사용)
    await page.getByPlaceholder('비밀번호').first().fill(SIGNUP_PASSWORD)
    await page.getByPlaceholder('비밀번호 확인').fill(SIGNUP_PASSWORD)

    // 9. 가입하기 클릭 → 성공 후 /signup을 벗어나면 성공 (/, /community, /session 등)
    await page.locator('button[type="submit"]').click()
    await page.waitForURL(url => !url.toString().includes('/signup'), { timeout: 20_000 })

    const token = await page.evaluate(() => localStorage.getItem('again-spring-token'))
    expect(token).toBeTruthy()
  })
})
