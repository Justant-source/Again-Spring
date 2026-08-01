import { execSync } from 'child_process'
import path from 'path'

const SCRIPT = path.resolve(__dirname, '../../../../backend/scripts/test-automation/cleanup-test-db.sh')

/**
 * test%@again.com 페르소나의 세션/메시지/turn/리포트 삭제. users 행 보존.
 *
 * 미공개(prelaunch): localhost:8091 + againspring-mariadb-prod 허용.
 * 공개 URL(againspring.net 등) cleanup은 계속 거부. 정식 공개 후 재검토.
 */
export function cleanup(baseURL?: string): void {
  const url = baseURL ?? process.env.E2E_BASE_URL ?? 'http://localhost:8091'
  const isLocalhost = url.includes('localhost') || url.includes('127.0.0.1')
  if (!isLocalhost) {
    throw new Error(`Cleanup refused: non-localhost URL detected: ${url}`)
  }

  const container = process.env.DB_CONTAINER ?? 'againspring-mariadb-prod'
  // Refuse accidental remote-looking container names that aren't our local prod/dev containers
  if (/prod/i.test(container) && container !== 'againspring-mariadb-prod') {
    throw new Error(`Cleanup refused: unexpected prod-like DB container: ${container}`)
  }

  execSync(`bash "${SCRIPT}"`, {
    stdio: 'inherit',
    env: {
      ...process.env,
      DB_CONTAINER: container,
      E2E_ENV_FILE:
        process.env.E2E_ENV_FILE ??
        path.resolve(__dirname, '../../../../env/.env.prod'),
    },
  })
}
