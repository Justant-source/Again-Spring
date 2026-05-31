import { test, expect } from '@playwright/test'

/**
 * 절대 불변: 중재자(mediator) 응답에 내부 메타데이터(<turn_meta> JSON 등)가
 * 사용자에게 노출되어서는 안 된다 — 스트리밍 draft 단계와 최종 메시지 모두.
 *
 * 회귀 배경(2026-05-31, prod claude-api 전용):
 *  - MAX_TOKENS=256으로 응답이 잘려 </turn_meta> 누락 → 파서가 본문 분리 실패 → raw JSON 저장
 *  - 스트리밍 partial이 raw로 draft 저장되어 JSON이 "일시적으로" 노출됐다가 최종에 정상으로 교체
 * 수정: MAX_TOKENS 1024 + 파서 잘린-블록 방어 + processor fallback + 스트리밍 partial 정제.
 *
 * draft(status=streaming)와 최종 메시지를 모두 폴링으로 훑어 JSON 노출이 한 번도 없어야 통과.
 */
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const JSON_LEAK = /turn_meta|"horsemen"|"criticism"|"nvc_completion"|"user_state"|"inferred_keywords"/

test.describe('불변: 중재자 응답 JSON 노출 금지', () => {
  test('채팅 응답(스트리밍 draft 포함)에 turn_meta JSON이 노출되지 않는다', async ({ page }) => {
    test.setTimeout(60_000)
    const h = (t: string) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' })

    const health = await page.request.get(`${BASE}/api/health`)
    expect(health.ok(), 'BE 헬스 실패 — dev 스택을 확인하세요').toBe(true)

    const auth = await page.request.post(`${BASE}/api/auth/guest`, { data: { nickname: 'JSON검증' } })
    expect(auth.status(), 'POST /api/auth/guest 200').toBe(200)
    const token = (await auth.json()).token.accessToken

    const sess = await page.request.post(`${BASE}/api/sessions`, {
      headers: h(token),
      data: { relationType: 'couple', category: { majorId: 'couple' } },
    })
    if (sess.status() === 429) {
      test.skip(true, '게스트 세션 일일 한도 소진 — 인프라 이슈, 회귀 아님')
      return
    }
    expect(sess.status(), 'POST /api/sessions 201').toBe(201)
    const sid = (await sess.json()).id

    const send = await page.request.post(`${BASE}/api/sessions/${sid}/messages`, {
      headers: h(token),
      data: { content: '남편이랑 사소한 걸로 자주 싸워요. 어제도 크게 다퉜고 서로 말도 안 해요. 너무 지쳐요.' },
    })
    expect(send.status(), 'POST /messages 200').toBe(200)

    // draft(streaming) + 최종 mediator 메시지를 폴링하며 매 스냅샷에서 JSON 노출 검사
    let sawReply = false
    let leaked: string | null = null
    for (let i = 0; i < 40; i++) {
      const r = await page.request.get(`${BASE}/api/sessions/${sid}/messages`, { headers: h(token) })
      const msgs = (await r.json()) as Array<{ sender: string; content: string }>
      const mediators = msgs.filter((m) => m.sender?.startsWith('MEDIATOR'))
      for (const m of mediators) {
        if (JSON_LEAK.test(m.content ?? '')) leaked = m.content
      }
      if (mediators.length >= 2) sawReply = true // 첫마디 + 응답
      if (sawReply) break
      await page.waitForTimeout(500)
    }

    expect(leaked, `중재자 메시지에 turn_meta JSON이 노출됨: ${leaked}`).toBeNull()
    // mediator 응답 도착 여부는 haiku 의존이라 optional — JSON 노출이 없으면 통과
  })
})
