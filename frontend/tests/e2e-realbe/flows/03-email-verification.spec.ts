import { test, expect } from '@playwright/test'

/**
 * Flow 03: 이메일 인증 코드 발송 (send-verification 회귀).
 *
 * dev 프로파일은 SMTP 실패 시에도 200(코드를 로그로 폴백)이므로, 여기선
 * "발송 엔드포인트가 정상 응답하는지"를 회귀 검증한다. 실제 SMTP 전달은
 * dev에서 검증 불가 — prod 로깅(EmailVerificationService INFO)으로 확인한다.
 *
 * 배경(2026-05-31): prod 메일 발송 안정성(SMTP connection/read/write timeout) +
 * prod 로깅 가시성(발송 성공/실패가 WARN 레벨에 묻혀 진단 불가했던 문제) 개선.
 */
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Flow 03: 이메일 인증 발송', () => {
  test('POST /api/auth/send-verification → 200 (발송 엔드포인트 정상)', async ({ page }) => {
    test.setTimeout(30_000) // SMTP timeout(최대 10s) 여유
    const resp = await page.request.post(`${BASE}/api/auth/send-verification`, {
      data: { email: `e2e-${Date.now()}@example.com` },
    })
    expect(
      resp.status(),
      `send-verification이 200이어야 합니다. 실제: ${resp.status()} — BE 메일 설정/로그 확인`,
    ).toBe(200)
  })
})
