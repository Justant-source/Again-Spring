/**
 * e2e-realbe 대상 결정.
 *
 * 기본 = **dev :8090**. prod(:8091 / againspring.net) e2e는 E3로 **하드 거부**.
 */
import path from 'path'

export type E2ETarget = {
  baseURL: string
  dbContainer: 'againspring-mariadb-dev' | 'againspring-mariadb'
  envFile: string
  label: 'dev' | 'base'
}

const ENV_DIR = path.resolve(__dirname, '../../../../env')

export const E2E_DEFAULT_BASE_URL = 'http://localhost:8090'
export const E2E_DEFAULT_DB_CONTAINER = 'againspring-mariadb-dev' as const
export const E2E_DEFAULT_ENV_FILE = path.join(ENV_DIR, '.env.dev')

function normalizeBaseURL(url: string): string {
  return url.replace(/^(https?:\/\/)127\.0\.0\.1(?=[:/]|$)/i, '$1localhost')
}

/** prod URL이면 throw — e2e는 dev만 (E3). */
export function assertE2EDevOnly(baseURL?: string): string {
  const url = normalizeBaseURL(baseURL ?? process.env.E2E_BASE_URL ?? E2E_DEFAULT_BASE_URL)
  if (
    /:8091\b/i.test(url) ||
    /againspring\.net/i.test(url) ||
    /mariadb-prod/i.test(process.env.DB_CONTAINER ?? '')
  ) {
    throw new Error(
      `E2E refused: prod 대상 금지 (url=${url}, DB_CONTAINER=${process.env.DB_CONTAINER ?? ''}). ` +
        `E2E_BASE_URL=http://localhost:8090 만 허용.`,
    )
  }
  return url
}

/** E2E_BASE_URL → DB 컨테이너/.env. prod는 거부. */
export function resolveE2ETarget(baseURL?: string): E2ETarget {
  const url = assertE2EDevOnly(baseURL)
  return {
    baseURL: url,
    dbContainer: 'againspring-mariadb-dev',
    envFile: path.join(ENV_DIR, '.env.dev'),
    label: 'dev',
  }
}
