/**
 * DB 직접 접근 헬퍼.
 *
 * 모든 SQL 접근을 한 곳에서 관리. 비밀번호는 env에서만 읽음 (평문 인라인 금지).
 * cleanup-test-db.sh와 동일한 readEnvVar 로직을 공유한다.
 *
 * 미공개(prelaunch): 기본 대상 = localhost prod 컨테이너 (`againspring-mariadb-prod` + `.env.prod`).
 * 공개 URL 대상 cleanup/접근은 상위에서 거부. 정식 공개 후 재검토.
 */
import { spawnSync } from 'child_process'
import path from 'path'
import fs from 'fs'

const ENV_FILE =
  process.env.E2E_ENV_FILE ||
  path.resolve(process.cwd(), '../env/.env.prod')
const ALLOWED_CONTAINERS = new Set([
  'againspring-mariadb-prod',
  'againspring-mariadb-dev',
  'againspring-mariadb',
])
const PYTHON_HELPER = path.resolve(
  process.cwd(),
  '../backend/scripts/test-automation/dev_db_sql.py',
)

export function readEnvVar(key: string): string {
  if (process.env[key]) return process.env[key]!
  if (fs.existsSync(ENV_FILE)) {
    const line = fs
      .readFileSync(ENV_FILE, 'utf-8')
      .split('\n')
      .find((l) => l.trimStart().startsWith(`${key}=`))
    if (line) {
      const val = line.slice(line.indexOf('=') + 1).replace(/^['"]|['"]$/g, '').trim()
      return val
    }
  }
  return ''
}

/**
 * SQL 쿼리를 허용된 로컬 MariaDB 컨테이너에서 실행하고 stdout 문자열을 반환.
 */
export function sql(query: string): string {
  const container = process.env.DB_CONTAINER ?? 'againspring-mariadb-prod'
  if (!ALLOWED_CONTAINERS.has(container)) {
    throw new Error(`DB 접근 거부: 허용되지 않은 컨테이너 (${container})`)
  }

  const pass = readEnvVar('MARIADB_PASSWORD')
  const db = readEnvVar('MARIADB_DATABASE') || 'againspring'
  const user = readEnvVar('MARIADB_USER') || 'againspring'

  if (!pass) {
    throw new Error('[db.ts] MARIADB_PASSWORD를 찾을 수 없습니다. env/.env.prod를 확인하세요.')
  }

  const result = spawnSync(
    'docker',
    ['exec', '-i', container, 'mariadb', '-u', user, `-p${pass}`, db, '-N', '-e', query],
    { encoding: 'utf-8' },
  )
  if (result.status === 0) {
    return result.stdout.trim()
  }

  const fallback = spawnSync(
    'python3',
    [PYTHON_HELPER, '--env-file', ENV_FILE, '--raw', '--query', query],
    { encoding: 'utf-8' },
  )
  if (fallback.status !== 0) {
    throw new Error(`SQL 실행 실패: ${result.stderr || fallback.stderr}`)
  }
  return fallback.stdout.trim()
}

export function runSqlScript(sqlText: string): void {
  const container = process.env.DB_CONTAINER ?? 'againspring-mariadb-prod'
  if (!ALLOWED_CONTAINERS.has(container)) {
    throw new Error(`DB 접근 거부: 허용되지 않은 컨테이너 (${container})`)
  }

  const pass = readEnvVar('MARIADB_PASSWORD')
  const db = readEnvVar('MARIADB_DATABASE') || 'againspring'
  const user = readEnvVar('MARIADB_USER') || 'againspring'

  if (!pass) {
    throw new Error('[db.ts] MARIADB_PASSWORD를 찾을 수 없습니다. env/.env.prod를 확인하세요.')
  }

  // Prefer docker exec (prod mariadb has no host port). Python helper is localhost fallback only.
  const viaDocker = spawnSync(
    'docker',
    ['exec', '-i', container, 'mariadb', '-u', user, `-p${pass}`, db],
    { encoding: 'utf-8', input: sqlText },
  )
  if (viaDocker.status === 0) {
    return
  }

  const fallback = spawnSync('python3', [PYTHON_HELPER, '--env-file', ENV_FILE], {
    encoding: 'utf-8',
    input: sqlText,
  })
  if (fallback.status !== 0) {
    throw new Error(
      `SQL 스크립트 실행 실패: ${viaDocker.stderr || fallback.stderr}`,
    )
  }
}

/**
 * 이메일 인증 코드를 DB에서 읽어 반환.
 * EmailVerificationService가 email_verifications 테이블에 4자리 코드를 평문 저장함.
 * e2e 가입 완주(08-email-verification-signup.spec.ts)에서 사용.
 */
export function latestVerificationCode(email: string): string {
  return sql(
    `SELECT code FROM email_verifications WHERE email='${email}' AND used=0 ORDER BY created_at DESC LIMIT 1`,
  )
}

/**
 * 게스트 사용자를 soft-delete 상태로 만든다 (회귀 테스트 재현용).
 * 04-voting.spec.ts에서 사용.
 */
export function softDeleteUser(userId: string): void {
  sql(`UPDATE users SET deleted_at = NOW() WHERE id = '${userId}'`)
}

/**
 * 사용자의 deleted_at이 NULL인지 확인 (복구 검증).
 */
export function isUserActive(userId: string): boolean {
  return sql(`SELECT deleted_at IS NULL FROM users WHERE id='${userId}'`) === '1'
}
