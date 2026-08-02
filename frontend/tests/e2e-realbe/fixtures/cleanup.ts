import { execSync } from 'child_process'
import path from 'path'
import { resolveE2ETarget } from '../support/env'

const SCRIPT = path.resolve(__dirname, '../../../../backend/scripts/test-automation/cleanup-test-db.sh')

/**
 * 삭제 대상:
 *   - test%@again.com 페르소나가 생성한 모든 커뮤니티 데이터
 *   - is_guest=1 게스트 유저 행 + 그 산출물
 *   - e2e-signup%@example.com 일회용 가입 유저
 *   - 제목/user_title 패턴 E2E/REPRO/[e2e] 포스트 안전망
 *   - marketing_job / notifications / community_reports / password_reset_tokens
 * 보존: mock_001, test%@again.com users 행
 *
 * 1차 삭제는 no-llm-fixture afterEach(createPost 추적). 이 스크립트는 setup/teardown 안전망.
 *
 * 중요: DB 대상은 E2E_BASE_URL에서 파생한다 (resolveE2ETarget).
 * ambient DB_CONTAINER / MARIADB_* 가 BE와 다른 스택을 가리키면 광장에 E2E 글이 남는다.
 * 공개 URL(againspring.net 등) cleanup은 거부.
 */
export function cleanup(baseURL?: string): void {
  const target = resolveE2ETarget(baseURL)
  const isLocalhost =
    target.baseURL.includes('localhost') || target.baseURL.includes('127.0.0.1')
  if (!isLocalhost) {
    throw new Error(`Cleanup refused: non-localhost URL detected: ${target.baseURL}`)
  }

  // ambient MARIADB_* 제거 → 스크립트가 E2E_ENV_FILE만 읽게 강제 (스택 불일치 방지)
  const env: NodeJS.ProcessEnv = { ...process.env }
  delete env.MARIADB_DATABASE
  delete env.MARIADB_PASSWORD
  delete env.MARIADB_USER
  delete env.DB_CONTAINER

  console.log(
    `[cleanup] E2E target=${target.label} url=${target.baseURL} db=${target.dbContainer} env=${path.basename(target.envFile)}`,
  )

  execSync(`bash "${SCRIPT}"`, {
    stdio: 'inherit',
    env: {
      ...env,
      DB_CONTAINER: target.dbContainer,
      E2E_ENV_FILE: target.envFile,
    },
  })
}
