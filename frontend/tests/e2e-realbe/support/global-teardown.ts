/**
 * globalTeardown — 전체 e2e 실행 완료 후 DB 정리.
 *
 * global-setup에서 "실행 전 정리"를 하고, 여기서 "실행 후 정리"를 한다.
 * 두 겹 정리로 테스트 DB가 매 실행마다 누적되는 문제를 방지.
 *
 * 미공개: localhost:8091 + againspring-mariadb-prod. 공개 URL cleanup은 거부.
 */
import { cleanup } from '../fixtures/cleanup'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

export default async function globalTeardown(): Promise<void> {
  try {
    cleanup(BASE)
    console.log('[global-teardown] DB cleanup 완료')
  } catch (e) {
    // teardown 실패가 테스트 결과를 바꾸지 않도록 경고만
    console.warn('[global-teardown] cleanup 건너뜀:', (e as Error).message)
  }
}
