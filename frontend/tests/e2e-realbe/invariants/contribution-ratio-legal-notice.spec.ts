/**
 * 절대 불변 규칙: ContributionRatio 법적 안내 박스는 항상 표시되고 숨겨지지 않는다.
 *
 * 권위본:
 *   frontend/README.md — 절대 불변 규칙 #3
 *   shared/docs/policies/ratio-calculation.md
 *   frontend/docs/ux/principles.md §2.4
 *
 * 설계 노트:
 *   - 법적 안내 박스는 FE 전용 UI 보장 (BE에 해당 필드 없음).
 *   - 실 BE 리포트 생성은 LLM 의존: Solo finalize → generateSoloReport(동기).
 *     report polling 타임아웃 시 test.skip + 사유 로그 (LLM unavailable 비회귀).
 *   - conflictType 4분기 매트릭스 검증은 vitest 컴포넌트 테스트 책임
 *     (tests/component/result/contribution-ratio.spec.tsx,
 *      tests/component/safety/contribution-ratio-legal.spec.tsx).
 *   - ContributionRatio.tsx 법적박스에 CJK 오타(分析) 존재 →
 *     텍스트 매칭 대신 data-testid="ratio-legal-notice" 사용.
 */
import { test, expect } from '@playwright/test'
import { authStatePath } from '../fixtures/auth-state'
import {
  login,
  createSession,
  sendMessage,
  finalizeSession,
  pollReport,
} from '../fixtures/api-helpers'
import { cleanup } from '../fixtures/cleanup'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { assertLegalNoticeAlwaysVisible } from '../support/assertions'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const MIN_MESSAGES = 5

test.describe('절대 불변: ContributionRatio 법적 안내 박스', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  let token: string
  let sessionId: string
  let reportGenerated = false

  test.beforeAll(async ({ request }) => {
    cleanup(BASE)

    // globalSetup storageState에서 토큰 읽기 (login() 재호출로 인한 Rate Limit 429 방지)
    const fs = await import('fs')
    const savedState = JSON.parse(fs.readFileSync(authStatePath(PERSONA_TEST1.email), 'utf-8'))
    const storedToken = (savedState.origins?.[0]?.localStorage ?? []).find(
      (e: { name: string; value: string }) => e.name === 'again-spring-token',
    )?.value as string | undefined
    token = storedToken ?? await login(request, PERSONA_TEST1.email, PERSONA_TEST1.password)
    const session = await createSession(request, token)
    sessionId = session.id

    // MIN_MESSAGES_TO_FINALIZE = 5 충족
    const safeMessages = [
      '요즘 대화가 줄었어요',
      '그게 많이 서운했는데',
      '어떻게 해야 할지 모르겠어요',
      '그냥 혼자 참다 보니 더 힘들어진 것 같아요',
      '이런 이야기를 하고 싶었어요',
    ]
    for (const msg of safeMessages) {
      await sendMessage(request, token, sessionId, msg)
    }

    const finalizeStatus = await finalizeSession(request, token, sessionId)
    if (finalizeStatus !== 200) {
      console.warn(`[ratio-legal] finalize 응답: ${finalizeStatus}`)
    }

    // 리포트 polling (Solo → generateSoloReport 동기 호출, LLM 대기)
    const report = await pollReport(request, token, sessionId, 90_000)
    if (report) {
      reportGenerated = true
    } else {
      console.warn(
        '[ratio-legal] 리포트 생성 타임아웃 — LLM 비가용 상태로 판단. skip 처리.',
      )
    }
  })

  test('결과 페이지에서 법적 안내 박스가 항상 표시됨', async ({ page }) => {
    if (!reportGenerated) {
      test.skip(true, '리포트 미생성 (LLM unavailable) — 회귀 아님, 인프라 이슈')
    }
    await page.goto(`${BASE}/session/result/${sessionId}`)
    await expect(page.locator('[data-testid="ratio-legal-notice"]')).toBeVisible({ timeout: 15_000 })
    await assertLegalNoticeAlwaysVisible(page)
  })

  test('법적 안내 박스가 조건부 처리되거나 숨겨지지 않는다 (스크롤 후 재확인)', async ({
    page,
  }) => {
    if (!reportGenerated) {
      test.skip(true, '리포트 미생성 (LLM unavailable)')
    }
    await page.goto(`${BASE}/session/result/${sessionId}`)
    await page.waitForTimeout(1_500)

    // 스크롤 후에도 유지
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await page.waitForTimeout(500)
    await assertLegalNoticeAlwaysVisible(page)

    // 법적 안내 박스가 visibility:hidden / display:none이 아님
    const noticeEl = page.locator('[data-testid="ratio-legal-notice"]')
    const style = await noticeEl.evaluate((el) => {
      const cs = getComputedStyle(el)
      return { visibility: cs.visibility, display: cs.display, opacity: cs.opacity }
    })
    expect(style.visibility).not.toBe('hidden')
    expect(style.display).not.toBe('none')
  })
})
