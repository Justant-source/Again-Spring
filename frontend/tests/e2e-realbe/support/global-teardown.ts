/**
 * globalTeardown — 전체 e2e 실행 완료 후 DB 정리.
 *
 * global-setup에서 "실행 전 정리"를 하고, 여기서 "실행 후 정리"를 한다.
 * 두 겹 정리로 dev DB가 매 실행마다 누적되는 문제를 방지.
 *
 * ⚠️  prod-like URL/컨테이너 이름 감지 시 즉시 abort (cleanup.ts와 동일 가드).
 */
import { cleanup } from '../fixtures/cleanup'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

export default async function globalTeardown(): Promise<void> {
  try {
    cleanup(BASE)
    console.log('[global-teardown] DB cleanup 완료')
  } catch (e) {
    // teardown 실패가 테스트 결과를 바꾸지 않도록 경고만
    console.warn('[global-teardown] cleanup 건너뜀:', (e as Error).message)
  }
}
