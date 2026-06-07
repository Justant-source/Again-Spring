/**
 * LLM 가드레일 픽스처.
 *
 * 모든 journey spec은 @playwright/test 대신 이 파일을 import한다.
 * page.on('request') 리스너가 LLM을 트리거하는 브라우저 요청을 감시해
 * 발견 즉시 테스트를 실패시킨다.
 *
 * 배경:
 *   - 러닝 중인 BE에는 LLM 비활성화 스위치가 없다
 *     (application-test.yml의 llm.provider:mock은 무효 — RemoteLlmProvider가 @Primary 무조건).
 *   - jurorCount=0 하드코딩과 이 가드레일 두 겹으로 LLM 미호출을 보장한다.
 *   - abort 대신 관찰-후-실패 방식: FE가 jurorCount>0를 보내기 시작하는 회귀를 잡기 위해.
 *
 * 커버 범위:
 *   ① page.on('request') → 브라우저가 보내는 모든 HTTP 요청
 *   ② support/api.ts의 assertNoLlmRequest → APIRequestContext 경유 요청
 *      (page.on은 request fixture 트래픽을 보지 못하므로 api.ts로 보완)
 */
import { test as base, expect } from '@playwright/test'

/** LLM을 트리거하는 경로 패턴 */
const LLM_PATH_PATTERNS = [
  /\/api\/community\/posts\/[^?/]+\/jury\/retry/,
  /\/api\/admin\/content\/corrections\/analyze/,
  /\/api\/admin\/ai-rules\/history\/[^?/]+\/analyze/,
  /\/api\/admin\/ai-rules\/history\/analyze-batch/,
  /\/api\/admin\/marketing\/[^?/]+\/(generate|simulation|story)/,
]

type NoLlmFixtures = {
  /** violations 배열 — afterEach에서 비어있어야 통과 */
  _llmViolations: string[]
}

export const test = base.extend<NoLlmFixtures>({
  _llmViolations: [
    async ({}, use) => {
      const violations: string[] = []
      await use(violations)
    },
    { auto: true },
  ],

  page: async ({ page, _llmViolations }, use) => {
    page.on('request', (req) => {
      const url = req.url()
      const method = req.method()

      // 1) LLM-tripping 경로 패턴 매칭
      for (const pattern of LLM_PATH_PATTERNS) {
        if (pattern.test(url)) {
          const msg = `[no-llm-guardrail] LLM 트리거 엔드포인트 호출 감지: ${method} ${url}`
          _llmViolations.push(msg)
          return
        }
      }

      // 2) POST /api/community/posts — jurorCount > 0 감지
      if (method === 'POST' && /\/api\/community\/posts$/.test(url)) {
        const raw = req.postData()
        if (raw) {
          try {
            const body = JSON.parse(raw)
            if (body.jurorCount && body.jurorCount > 0) {
              const msg = `[no-llm-guardrail] POST /api/community/posts에 jurorCount=${body.jurorCount} 감지 — LLM이 호출됩니다`
              _llmViolations.push(msg)
            }
          } catch { /* JSON이 아닌 body 무시 */ }
        }
      }

      // 3) POST /api/s/{token}/answer — 이 함수를 쓰기 전에 반드시 jurorCount=0 포스트만 사용
      //    런타임에 부모 post의 jurorCount를 알 수 없으므로 경고만 (api.ts가 보완)
      if (method === 'POST' && /\/api\/s\/[^/]+\/answer$/.test(url)) {
        // api.ts의 submitPartnerAnswer는 createPost가 jurorCount=0임을 보장
        // 여기서는 브라우저 직접 제출(UI 경유) 시 경고를 위해 놔둠 — 위반은 아님
      }
    })

    await use(page)

    // 테스트 종료 후 위반 검사
    expect(
      _llmViolations,
      `LLM 가드레일 위반 감지됨:\n${_llmViolations.join('\n')}`,
    ).toEqual([])
  },
})

export { expect }
