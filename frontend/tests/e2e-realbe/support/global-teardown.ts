/**
 * globalTeardown — 전체 e2e 실행 완료 후 DB 정리.
 *
 * 잔존 검증은 SQL + 공개 피드 API 둘 다 사용 (한쪽만 보면 놓침).
 */
import { request as playwrightRequest } from '@playwright/test'
import { cleanup } from '../fixtures/cleanup'
import { sql } from './db'
import { resolveE2ETarget } from './env'

function listE2ELeftoversSql(): string {
  return sql(
    `SELECT CONCAT(id, ':', IFNULL(title,'')) FROM posts
     WHERE id <> 'mock_001'
       AND (title LIKE '%E2E%' OR title LIKE '%e2e%'
            OR user_title LIKE '%E2E%' OR user_title LIKE '%e2e%'
            OR title LIKE 'REPRO%' OR title LIKE '[e2e]%')`,
  )
}

async function listE2ELeftoversFeed(baseURL: string): Promise<string[]> {
  const ctx = await playwrightRequest.newContext()
  try {
    const resp = await ctx.get(`${baseURL}/api/community/posts?page=0&size=50&sort=latest`)
    if (!resp.ok()) return [`feed HTTP ${resp.status()}`]
    const data = await resp.json()
    const items = Array.isArray(data) ? data : (data.content ?? [])
    return items
      .filter((p: { title?: string }) => {
        const t = p.title ?? ''
        return /E2E|e2e|REPRO|\[e2e\]/.test(t)
      })
      .map((p: { id?: string; title?: string }) => `${p.id}:${p.title}`)
  } finally {
    await ctx.dispose()
  }
}

export default async function globalTeardown(): Promise<void> {
  const target = resolveE2ETarget(process.env.E2E_BASE_URL)
  console.log(
    `[global-teardown] target=${target.label} url=${target.baseURL} db=${target.dbContainer}`,
  )

  cleanup(target.baseURL)
  console.log('[global-teardown] DB cleanup 완료')

  // PromoTitle/AnswerProcessing async가 DELETE 직후 save()/merge로 행을 되살리던
  // 사고 대비: 짧게 대기 후 한 번 더 정리·검증
  await new Promise((r) => setTimeout(r, 15_000))
  cleanup(target.baseURL)
  console.log('[global-teardown] delayed cleanup 완료')

  let sqlLeft = listE2ELeftoversSql()
  if (sqlLeft) {
    console.warn('[global-teardown] SQL 잔존 — cleanup 재시도:\n' + sqlLeft)
    cleanup(target.baseURL)
    sqlLeft = listE2ELeftoversSql()
  }

  const feedLeft = await listE2ELeftoversFeed(target.baseURL)

  if (sqlLeft || feedLeft.length > 0) {
    throw new Error(
      `[global-teardown] E2E 사연이 ${target.label}에 남아 있음\n` +
        `SQL:\n${sqlLeft || '(없음)'}\n` +
        `FEED:\n${feedLeft.join('\n') || '(없음)'}`,
    )
  }
  console.log(`[global-teardown] E2E 사연 잔존 없음 확인 (${target.label})`)
}
