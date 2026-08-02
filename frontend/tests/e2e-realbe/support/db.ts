/**
 * DB 직접 접근 헬퍼.
 *
 * 대상 DB는 E2E_BASE_URL → resolveE2ETarget (dev만, E3).
 */
import { spawnSync } from 'child_process'
import path from 'path'
import fs from 'fs'
import { resolveE2ETarget } from './env'

const ALLOWED_CONTAINERS = new Set([
  'againspring-mariadb-dev',
  'againspring-mariadb',
])
const PYTHON_HELPER = path.resolve(
  process.cwd(),
  '../backend/scripts/test-automation/dev_db_sql.py',
)

function currentTarget() {
  return resolveE2ETarget(process.env.E2E_BASE_URL)
}

export function readEnvVar(key: string): string {
  const envFile = process.env.E2E_ENV_FILE || currentTarget().envFile
  if (fs.existsSync(envFile)) {
    const line = fs
      .readFileSync(envFile, 'utf-8')
      .split('\n')
      .find((l) => l.trimStart().startsWith(`${key}=`))
    if (line) {
      const val = line.slice(line.indexOf('=') + 1).replace(/^['"]|['"]$/g, '').trim()
      return val
    }
  }
  return ''
}

function dbCredentials(): {
  container: string
  pass: string
  db: string
  user: string
  envFile: string
  label: string
} {
  const target = currentTarget()
  const container = process.env.DB_CONTAINER || target.dbContainer
  if (/prod/i.test(container)) {
    throw new Error(`DB 접근 거부: prod DB 금지 (${container})`)
  }
  if (!ALLOWED_CONTAINERS.has(container)) {
    throw new Error(`DB 접근 거부: 허용되지 않은 컨테이너 (${container})`)
  }
  const envFile = process.env.E2E_ENV_FILE || target.envFile
  const pass = readEnvVar('MARIADB_PASSWORD')
  const db = readEnvVar('MARIADB_DATABASE') || 'againspring'
  const user = readEnvVar('MARIADB_USER') || 'againspring'
  if (!pass) {
    throw new Error(`[db.ts] MARIADB_PASSWORD를 찾을 수 없습니다. ${envFile} 확인.`)
  }
  return { container, pass, db, user, envFile, label: target.label }
}

/**
 * SQL 쿼리를 E2E 대상 MariaDB 컨테이너에서 실행하고 stdout 문자열을 반환.
 */
export function sql(query: string): string {
  const { container, pass, db, user, envFile, label } = dbCredentials()

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
    [PYTHON_HELPER, '--env-file', envFile, '--raw', '--query', query],
    { encoding: 'utf-8' },
  )
  if (fallback.status !== 0) {
    throw new Error(`SQL 실행 실패 (${label}): ${result.stderr || fallback.stderr}`)
  }
  return fallback.stdout.trim()
}

export function runSqlScript(sqlText: string): void {
  const { container, pass, db, user, envFile, label } = dbCredentials()

  const viaDocker = spawnSync(
    'docker',
    ['exec', '-i', container, 'mariadb', '-u', user, `-p${pass}`, db],
    { encoding: 'utf-8', input: sqlText },
  )
  if (viaDocker.status === 0) {
    return
  }

  const fallback = spawnSync('python3', [PYTHON_HELPER, '--env-file', envFile], {
    encoding: 'utf-8',
    input: sqlText,
  })
  if (fallback.status !== 0) {
    throw new Error(
      `SQL 스크립트 실행 실패 (${label}): ${viaDocker.stderr || fallback.stderr}`,
    )
  }
}

/**
 * 단일 포스트를 자식 행과 함께 강제 삭제 (API 삭제 실패 시 폴백).
 * postId 형식만 허용 — SQL injection 방지.
 */
export function forceDeletePostById(postId: string): void {
  if (!/^post_[a-zA-Z0-9]+$/.test(postId)) {
    throw new Error(`forceDeletePostById: invalid postId ${postId}`)
  }
  const { db } = dbCredentials()
  runSqlScript(`
SET SESSION foreign_key_checks = 0;
DELETE FROM \`${db}\`.marketing_job WHERE post_id='${postId}';
DELETE FROM \`${db}\`.notifications WHERE ref_post_id='${postId}';
DELETE FROM \`${db}\`.votes WHERE post_id='${postId}';
DELETE FROM \`${db}\`.post_views WHERE post_id='${postId}';
DELETE FROM \`${db}\`.post_comments WHERE post_id='${postId}';
DELETE FROM \`${db}\`.jurors WHERE post_id='${postId}';
DELETE FROM \`${db}\`.vote_options WHERE post_id='${postId}';
DELETE FROM \`${db}\`.posts WHERE id='${postId}';
SET SESSION foreign_key_checks = 1;
`)
}

export function latestVerificationCode(email: string): string {
  return sql(
    `SELECT code FROM email_verifications WHERE email='${email}' AND used=0 ORDER BY created_at DESC LIMIT 1`,
  )
}

export function softDeleteUser(userId: string): void {
  sql(`UPDATE users SET deleted_at = NOW() WHERE id = '${userId}'`)
}

export function isUserActive(userId: string): boolean {
  return sql(`SELECT deleted_at IS NULL FROM users WHERE id='${userId}'`) === '1'
}
