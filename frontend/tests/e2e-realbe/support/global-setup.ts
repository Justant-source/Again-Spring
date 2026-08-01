import { chromium, request as playwrightRequest } from '@playwright/test'
import fs from 'fs'
import {
  PERSONAS,
  PRELOGIN_PERSONAS,
  PERSONA_TEST1,
  PERSONA_TESTER_A,
  PERSONA_TESTER_B,
} from '../fixtures/personas'
import { saveAuthState, AUTH_STATE_DIR } from '../fixtures/auth-state'
import { chromiumLaunchOptions } from './browser'
import { cleanup } from '../fixtures/cleanup'
import { readEnvVar, runSqlScript } from './db'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

/**
 * SQL로 직접 처리:
 *   - test1 password_hash를 test123 해시로 리셋 (test2 해시 복사 — 동일 seed)
 *   - test1 → ADMIN
 *   - test2, test3 → TESTER
 *
 * API 경유(ADMIN 로그인 → PATCH) 방식 대신 SQL 직접 사용:
 *   이전 세션에서 test1 비밀번호가 변경돼 있을 수 있음.
 */
function bootstrapViaSql(): void {
  const pass = readEnvVar('MARIADB_PASSWORD')
  const db = readEnvVar('MARIADB_DATABASE') || 'againspring'
  const user = readEnvVar('MARIADB_USER') || 'againspring'
  if (!pass) {
    console.warn('[global-setup] MARIADB_PASSWORD 미확인 — SQL 부트스트랩 건너뜀')
    console.warn('[global-setup] env/.env.prod 파일을 확인하세요 (db.ts readEnvVar 경유)')
    return
  }

  const runSql = (sql: string) => runSqlScript(sql)

  // BCrypt $2a$12$ 해시: SeedPersonas.build()가 "test123"으로 생성한 값
  const TEST123_HASH = '$2a$12$9EFz.LWcKCU9N/UEPETS7OwIRVslpITrtGseQe1GiqZOMgQ9gCic6'

  // prod에는 SeedDataLoader(@Profile("dev"))가 없어 페르소나가 없을 수 있음.
  // INSERT IGNORE로 최소 행을 보장한 뒤 역할·비밀번호를 맞춘다.
  for (let i = 0; i < PERSONAS.length; i++) {
    const p = PERSONAS[i]
    const id = `e2epersona${String(i + 1).padStart(2, '0')}`.slice(0, 32)
    runSql(`
      INSERT IGNORE INTO users (
        id, email, password_hash, nickname, roles, status,
        is_guest, synthetic, must_change_password,
        onboarding_completed_at, tutorial_completed_at, created_at, updated_at
      ) VALUES (
        '${id}', '${p.email}', '${TEST123_HASH}', '${p.nickname}',
        JSON_ARRAY('USER'), 'ACTIVE',
        0, 0, 0,
        NOW(3), NOW(3), NOW(3), NOW(3)
      );
    `)
  }
  console.log(`[global-setup] e2e 페르소나 보장: ${PERSONAS.length}명`)

  // test1 비밀번호를 test123 해시로 리셋 (test2와 동일 — 동일 seed 비밀번호)
  runSql(
    `UPDATE users SET password_hash='${TEST123_HASH}', roles=JSON_ARRAY('USER','ADMIN') WHERE email='${PERSONA_TEST1.email}';`,
  )
  console.log(`[global-setup] ADMIN + 비밀번호 리셋: ${PERSONA_TEST1.email}`)

  // test2, test3 → TESTER
  for (const email of [PERSONA_TESTER_A.email, PERSONA_TESTER_B.email]) {
    runSql(`UPDATE users SET roles=JSON_ARRAY('USER','TESTER') WHERE email='${email}';`)
    console.log(`[global-setup] TESTER 부여 (SQL): ${email}`)
  }

  // 모든 test 페르소나 튜토리얼 완료 처리 (V13 OnboardingModal 차단 방지)
  // tutorial_completed_at이 NULL이면 채팅 화면에 모달이 오버레이됨
  runSql(`UPDATE users SET tutorial_completed_at = NOW() WHERE email LIKE 'test%@again.com';`)
  console.log('[global-setup] tutorial_completed_at 설정 완료 (test%@again.com)')
}

/** 로그인 사이 짧은 간격 (rate limit 1000/min으로 상향됐지만 안전용 1s 유지) */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export default async function globalSetup(): Promise<void> {
  // 1. BE 헬스 확인
  const apiCtx = await playwrightRequest.newContext()
  try {
    const health = await apiCtx.get(`${BASE}/api/health`)
    if (!health.ok()) {
      throw new Error(
        `[global-setup] BE (${BASE}/api/health)가 응답하지 않습니다. docker compose dev 환경을 확인하세요.`,
      )
    }
    console.log('[global-setup] BE 헬스 OK')
  } finally {
    await apiCtx.dispose()
  }

  // 2. 테스트 데이터 cleanup
  try {
    cleanup(BASE)
    console.log('[global-setup] DB cleanup 완료')
  } catch (e) {
    console.warn('[global-setup] cleanup 건너뜀:', (e as Error).message)
  }

  // 3. SQL 부트스트랩 (test1 비밀번호 리셋 + ADMIN, test2·test3 TESTER)
  bootstrapViaSql()

  // 3-1. mock_001 seed 포스트 보장 — 사연 상세/댓글/read 화면 테스트가 의존
  try {
    const pass = readEnvVar('MARIADB_PASSWORD')
    if (pass) {
      const seedMock = `
        INSERT IGNORE INTO posts (id, author_id, body_published, body_raw, category, created_at, neutralization_passed, status, title, updated_at, visibility, juror_count, publish_mode, user_title, view_count)
        SELECT 'mock_001', id, '저는 직장인인데 주말에도 집안일을 다 도맡아 하고 있어요. 상대방은 이게 당연하다고 생각하는 것 같아요. 저만 쉬는 날이 없는 것 같아서 힘드네요.',
               '저는 직장인인데 주말에도 집안일을 다 도맡아 하고 있어요.', 'WORK', NOW(), 0, 'VOTING', '주말에도 저만 쉬는 날이 없어요', NOW(), 'PUBLIC', 0, 'PUBLISH_NOW', '주말에도 저만 쉬는 날이 없어요', 0
        FROM users WHERE email='test1@again.com';
        -- e2e 반복 시 vote_options 중복 누적 방지 (voteOptions[0]/[1] 계약 깨짐)
        DELETE FROM vote_options WHERE post_id = 'mock_001';
        INSERT INTO vote_options (post_id, label, order_idx) VALUES ('mock_001', '작성자', 0), ('mock_001', '상대방', 1);
      `
      runSqlScript(seedMock)
      console.log('[global-setup] mock_001 seed 포스트 보장 완료')
    }
  } catch (e) {
    console.warn('[global-setup] mock_001 seed 건너뜀:', (e as Error).message)
  }

  // 4. 페르소나 prelogin — storageState 저장 (.auth/<email>.json)
  //    prod auth rate-limit 기본 5/min/IP → 페르소나당 13초 간격 필수.
  //    (dev compose만 SECURITY_RATE_LIMIT_AUTH_PER_MINUTE=1000)
  if (!fs.existsSync(AUTH_STATE_DIR)) fs.mkdirSync(AUTH_STATE_DIR, { recursive: true })

  const authGapMs = Number(process.env.E2E_AUTH_GAP_MS || '13000')
  const browser = await chromium.launch(chromiumLaunchOptions())
  for (let i = 0; i < PRELOGIN_PERSONAS.length; i++) {
    const persona = PRELOGIN_PERSONAS[i]
    if (i > 0) {
      await sleep(authGapMs)
    }
    const context = await browser.newContext()
    try {
      await saveAuthState(context, persona, BASE)
      console.log(`[global-setup] storageState 저장: ${persona.email}`)
    } catch (e) {
      console.warn(`[global-setup] ${persona.email} 로그인 실패:`, (e as Error).message)
    } finally {
      await context.close()
    }
  }
  await browser.close()

  console.log('[global-setup] 완료')
}
