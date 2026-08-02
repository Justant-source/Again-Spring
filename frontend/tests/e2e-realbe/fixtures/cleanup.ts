import { execSync } from 'child_process'
import path from 'path'

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
