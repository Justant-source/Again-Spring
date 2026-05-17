import { execSync } from 'child_process'
import path from 'path'

const SCRIPT = path.resolve(__dirname, '../../../../backend/scripts/test-automation/cleanup-test-db.sh')
const PROD_PATTERNS = /prod/i

/**
 * test%@again.com 페르소나의 세션/메시지/turn/리포트 삭제. users 행 보존.
 * prod-like URL 또는 컨테이너 이름 감지 시 즉시 throw.
 */
export function cleanup(baseURL?: string): void {
  const url = baseURL ?? process.env.E2E_BASE_URL ?? 'http://localhost:8090'
  if (PROD_PATTERNS.test(url) && !url.includes('localhost') && !url.includes('8090')) {
    throw new Error(`Cleanup refused: prod-like URL detected: ${url}`)
  }

  const container = process.env.DB_CONTAINER ?? 'againspring-mariadb-dev'
  if (PROD_PATTERNS.test(container)) {
    throw new Error(`Cleanup refused: prod-like DB container detected: ${container}`)
  }

  execSync(`bash "${SCRIPT}"`, { stdio: 'inherit' })
}
