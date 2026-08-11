/**
 * LLM 가드레일 + createPost 자동 삭제 픽스처.
 *
 * 모든 journey spec은 @playwright/test 대신 이 파일을 import한다.
 * createPost로 만든 사연은 테스트 종료 시 API DELETE → DB 잔존 시 SQL 강제 삭제.
 * global-teardown cleanup-test-db.sh는 동일 E2E_BASE_URL 스택 안전망.
 */
import { test as base, expect } from '@playwright/test'
import {
  deleteTrackedPosts,
  E2E_CREATED_POSTS,
  type TrackedPost,
} from './api'

/** LLM을 트리거하는 경로 패턴 */
const LLM_PATH_PATTERNS = [
  /\/api\/admin\/content\/corrections\/analyze/,
  /\/api\/admin\/ai-rules\/history\/[^?/]+\/analyze/,
  /\/api\/admin\/ai-rules\/history\/analyze-batch/,
  /\/api\/admin\/marketing\/[^/]+\/(generate|simulation|story)/,
]

type NoLlmFixtures = {
  _llmViolations: string[]
  _createdPostsCleanup: TrackedPost[]
}

export const test = base.extend<NoLlmFixtures>({
  _llmViolations: [
    async ({}, use) => {
      const violations: string[] = []
      await use(violations)
    },
    { auto: true },
  ],

  _createdPostsCleanup: [
    async ({ request }, use) => {
      const created: TrackedPost[] = []
      ;(request as unknown as Record<symbol, TrackedPost[]>)[E2E_CREATED_POSTS] = created
      await use(created)
      if (created.length > 0) {
        console.log(`[e2e-cleanup] createPost ${created.length}건 삭제 시작`)
        await deleteTrackedPosts(request, created)
        console.log('[e2e-cleanup] createPost 삭제 완료')
      }
      // 추적 누락·지연 커밋 대비: 매 테스트 후 전체 E2E cleanup 스크립트
      try {
        const { cleanup } = await import('../fixtures/cleanup')
        cleanup(process.env.E2E_BASE_URL)
      } catch (e) {
        console.warn('[e2e-cleanup] per-test cleanup 스크립트 실패:', (e as Error).message)
      }
    },
    { auto: true },
  ],

  page: async ({ page, _llmViolations }, use) => {
    page.on('request', (req) => {
      const url = req.url()
      const method = req.method()

      for (const pattern of LLM_PATH_PATTERNS) {
        if (pattern.test(url)) {
          _llmViolations.push(
            `[no-llm-guardrail] LLM 트리거 엔드포인트 호출 감지: ${method} ${url}`,
          )
          return
        }
      }
    })

    await use(page)

    expect(
      _llmViolations,
      `LLM 가드레일 위반 감지됨:\n${_llmViolations.join('\n')}`,
    ).toEqual([])
  },
})

export { expect }
