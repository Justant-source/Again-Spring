/**
 * globalTeardown — 전체 e2e 실행 완료 후 DB 정리.
 *
 * global-setup에서 "실행 전 정리"를 하고, 여기서 "실행 후 정리"를 한다.
 * 두 겹 정리로 테스트 DB가 매 실행마다 누적되는 문제를 방지.
 * (1차 삭제는 no-llm-fixture의 createPost afterEach — 여기 cleanup은 안전망.)
 *
 * 미공개: localhost:8091 + againspring-mariadb-prod. 공개 URL cleanup은 거부.
 */
import { cleanup } from '../fixtures/cleanup'
import { sql } from './db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

export default async function globalTeardown(): Promise<void> {
  try {
    cleanup(BASE)
    console.log('[global-teardown] DB cleanup 완료')
  } catch (e) {
    // teardown 실패가 테스트 결과를 바꾸지 않도록 경고만
    console.warn('[global-teardown] cleanup 건너뜀:', (e as Error).message)
  }

  // 안전망 검증 — E2E 제목 사연이 남았으면 경고 (광장 오염 조기 발견)
  try {
    const leftover = sql(
      `SELECT CONCAT(id, ':', IFNULL(title,'')) FROM posts
       WHERE id <> 'mock_001'
         AND (title LIKE '%E2E%' OR title LIKE '%e2e%'
              OR user_title LIKE '%E2E%' OR user_title LIKE '%e2e%')`,
    )
    if (leftover) {
      console.warn('[global-teardown] E2E 사연이 아직 남아 있음:\n' + leftover)
    } else {
      console.log('[global-teardown] E2E 사연 잔존 없음 확인')
    }
  } catch (e) {
    console.warn('[global-teardown] 잔존 검증 건너뜀:', (e as Error).message)
  }
}
