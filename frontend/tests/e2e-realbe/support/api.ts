/**
 * BE 셋업 호출 단일 출처.
 *
 * - createPost: 항상 jurorCount=0 강제 → LLM 미호출 보장
 * - 모든 spec은 인라인 fetch 대신 이 모듈을 사용한다.
 * - 기존 fixtures/api-helpers.ts를 통합; api-helpers.ts는 여기로 포인터만 남김.
 */
import type { APIRequestContext } from '@playwright/test'
import fs from 'fs'
import path from 'path'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── LLM 안전 검사 ─────────────────────────────────────────────────

/** LLM을 트리거하는 것으로 알려진 엔드포인트 패턴 */
const LLM_DANGEROUS_PATHS = [
  /\/api\/community\/posts\/[^/]+\/jury\/retry/,
  /\/api\/admin\/content\/corrections\/analyze/,
  /\/api\/admin\/ai-rules\/history\/[^/]+\/analyze/,
  /\/api\/admin\/ai-rules\/history\/analyze-batch/,
]

export function assertNoLlmRequest(url: string, method: string, body?: string): void {
  for (const pattern of LLM_DANGEROUS_PATHS) {
    if (pattern.test(url)) {
      throw new Error(`[no-llm-guardrail] LLM을 트리거하는 엔드포인트 호출 감지: ${method} ${url}`)
    }
  }
  // POST /api/community/posts — jurorCount > 0 확인
  if (method === 'POST' && /\/api\/community\/posts$/.test(url) && body) {
    try {
      const parsed = JSON.parse(body)
      if (parsed.jurorCount && parsed.jurorCount > 0) {
        throw new Error(
          `[no-llm-guardrail] POST /api/community/posts에 jurorCount=${parsed.jurorCount} 감지. LLM 호출을 방지하려면 jurorCount=0을 사용하세요.`,
        )
      }
    } catch (e) {
      if ((e as Error).message.includes('no-llm-guardrail')) throw e
    }
  }
  // POST /api/s/{token}/answer — 부모 post의 jurorCount는 런타임에 확인할 수 없으므로
  // createPost가 항상 jurorCount=0을 강제하는 것으로 보장
}

// ── 인증 ──────────────────────────────────────────────────────────

export async function login(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const resp = await request.post(`${BASE}/api/auth/login`, {
    data: { email, password },
  })
  if (!resp.ok()) throw new Error(`Login 실패: ${resp.status()} — ${await resp.text()}`)
  const { token } = await resp.json()
  return token.accessToken as string
}

export async function guestLogin(request: APIRequestContext, nickname?: string): Promise<string> {
  const resp = await request.post(`${BASE}/api/auth/guest`, {
    data: { nickname: nickname ?? 'E2E게스트' },
  })
  if (!resp.ok()) throw new Error(`Guest login 실패: ${resp.status()}`)
  const { token } = await resp.json()
  return token.accessToken as string
}

/**
 * storageState 파일에서 JWT 토큰을 읽어 반환.
 * 매 테스트마다 login() API를 호출하는 대신 이 함수를 사용해 중복 로그인 제거.
 */
export function tokenFromStorageState(email: string): string {
  const AUTH_STATE_DIR = path.resolve('.auth')
  const filePath = path.join(AUTH_STATE_DIR, `${email.replace('@', '_at_')}.json`)
  try {
    const raw = fs.readFileSync(filePath, 'utf-8')
    const state = JSON.parse(raw)
    const ls = state?.origins?.[0]?.localStorage ?? []
    const item = ls.find((i: { name: string; value: string }) => i.name === 'again-spring-token')
    if (item?.value) return item.value as string
  } catch { /* storageState 미존재 시 무시 */ }
  return ''
}

// ── 커뮤니티 게시글 ────────────────────────────────────────────────

/**
 * 사연 생성. 항상 jurorCount=0을 강제해 LLM 미호출을 보장.
 * 어떤 spec도 jurorCount를 인라인으로 설정하지 말 것.
 */
export async function createPost(
  request: APIRequestContext,
  opts: {
    token: string
    title?: string
    body?: string
    category?: string
  },
): Promise<string> {
  const { token, title = 'E2E 테스트 사연', body = 'e2e 테스트용 사연 본문입니다. 충분한 길이를 확보합니다.', category = 'OTHER' } = opts
  const url = `${BASE}/api/community/posts`
  const data = {
    bodyRaw: body,
    category,
    visibility: 'PUBLIC',
    jurorCount: 0, // 항상 0 — 절대 변경 금지
    userTitle: title,
  }
  // API 경로 LLM 안전 검사
  assertNoLlmRequest(url, 'POST', JSON.stringify(data))

  const resp = await request.post(url, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  })
  if (!resp.ok()) throw new Error(`포스트 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).id as string
}

// ── 초대 ──────────────────────────────────────────────────────────

export async function createInviteToken(
  request: APIRequestContext,
  token: string,
  postId: string,
): Promise<string> {
  const resp = await request.post(`${BASE}/api/community/posts/${postId}/invite`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) throw new Error(`초대 토큰 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).inviteToken as string
}

/**
 * 상대방 답변 제출.
 * 이 함수를 호출하기 전에 반드시 createPost로 jurorCount=0인 포스트를 생성해야 함.
 * jurorCount>0인 포스트에 이 함수를 쓰면 LLM이 호출된다 (AnswerProcessingService).
 */
export async function submitPartnerAnswer(
  request: APIRequestContext,
  inviteToken: string,
  body = '상대방 답변입니다. e2e 테스트에 의해 자동 생성됩니다.',
  title = '상대방 입장 제목',
): Promise<void> {
  const resp = await request.post(`${BASE}/api/s/${inviteToken}/answer`, {
    data: { bodyRaw: body, userTitle: title },
  })
  if (!resp.ok()) throw new Error(`상대 답변 제출 실패: ${resp.status()} — ${await resp.text()}`)
}

/** 포스트가 paired 상태인지 최대 maxRetries×500ms 간격으로 폴링 */
export async function waitForPaired(
  request: APIRequestContext,
  postId: string,
  maxRetries = 10,
): Promise<boolean> {
  for (let i = 0; i < maxRetries; i++) {
    const resp = await request.get(`${BASE}/api/community/posts/${postId}`)
    if (resp.ok()) {
      const data = await resp.json()
      if (data.paired || data.partnerBodyPublished) return true
    }
    await new Promise(r => setTimeout(r, 500))
  }
  return false
}

// ── 관리자 ────────────────────────────────────────────────────────

export async function findUserId(
  request: APIRequestContext,
  adminToken: string,
  email: string,
): Promise<string | null> {
  const resp = await request.get(
    `${BASE}/api/admin/users/search?q=${encodeURIComponent(email)}`,
    { headers: { Authorization: `Bearer ${adminToken}` } },
  )
  if (!resp.ok()) return null
  const result = await resp.json()
  const users = Array.isArray(result) ? result : (result.content ?? result.users ?? [])
  const found = users.find((u: Record<string, string>) => u.email === email)
  return found?.id ?? null
}

export async function patchUserRoles(
  request: APIRequestContext,
  adminToken: string,
  userId: string,
  roles: string[],
): Promise<void> {
  const resp = await request.patch(`${BASE}/api/admin/users/${userId}/roles`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { roles },
  })
  if (!resp.ok()) throw new Error(`PATCH roles 실패: ${resp.status()} — ${await resp.text()}`)
}
