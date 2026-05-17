import type { APIRequestContext } from '@playwright/test'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

/** 인증 헬퍼 */
export async function login(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const resp = await request.post(`${BASE}/api/auth/login`, {
    data: { email, password },
  })
  if (!resp.ok()) throw new Error(`Login failed: ${resp.status()} — ${await resp.text()}`)
  const { token } = await resp.json()
  return token.accessToken as string
}

export async function guestLogin(request: APIRequestContext, nickname?: string): Promise<string> {
  const resp = await request.post(`${BASE}/api/auth/guest`, {
    data: { nickname: nickname ?? 'E2E게스트' },
  })
  if (!resp.ok()) throw new Error(`Guest login failed: ${resp.status()}`)
  const { token } = await resp.json()
  return token.accessToken as string
}

/** 세션 생성 (Solo) */
export async function createSession(
  request: APIRequestContext,
  token: string,
  opts?: { relationType?: string; description?: string },
): Promise<{ id: string; inviteToken?: string }> {
  const resp = await request.post(`${BASE}/api/sessions`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      relationType: opts?.relationType ?? 'friend',
      category: { majorId: 'friend', middleId: 'friend_communication', minorId: 'contact_lack' },
      description: opts?.description,
      soloMode: true,
    },
  })
  if (!resp.ok()) throw new Error(`Create session failed: ${resp.status()} — ${await resp.text()}`)
  return resp.json()
}

/** 메시지 전송 — 409(crisis) 또는 200 반환 */
export async function sendMessage(
  request: APIRequestContext,
  token: string,
  sessionId: string,
  content: string,
): Promise<{ status: number; body: Record<string, unknown> }> {
  const resp = await request.post(`${BASE}/api/sessions/${sessionId}/messages`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { content },
  })
  const body = await resp.json().catch(() => ({}))
  return { status: resp.status(), body }
}

/** 메시지 목록 조회 */
export async function getMessages(
  request: APIRequestContext,
  token: string,
  sessionId: string,
  since = 0,
): Promise<unknown[]> {
  const resp = await request.get(`${BASE}/api/sessions/${sessionId}/messages?since=${since}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) return []
  return resp.json()
}

/** Finalize 요청 */
export async function finalizeSession(
  request: APIRequestContext,
  token: string,
  sessionId: string,
): Promise<number> {
  const resp = await request.post(`${BASE}/api/sessions/${sessionId}/finalize`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return resp.status()
}

/** 리포트 폴링 (생성될 때까지 최대 timeoutMs) */
export async function pollReport(
  request: APIRequestContext,
  token: string,
  sessionId: string,
  timeoutMs = 90_000,
  intervalMs = 3_000,
): Promise<Record<string, unknown> | null> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const resp = await request.get(`${BASE}/api/sessions/${sessionId}/report`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok()) return resp.json()
    await new Promise((r) => setTimeout(r, intervalMs))
  }
  return null
}

/** Invite 토큰 생성 */
export async function invitePartner(
  request: APIRequestContext,
  token: string,
  sessionId: string,
): Promise<string> {
  const resp = await request.post(`${BASE}/api/sessions/${sessionId}/invite`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) throw new Error(`Invite failed: ${resp.status()} — ${await resp.text()}`)
  const body = await resp.json()
  return body.inviteToken as string
}

/** 파트너 참여
 * @param token B 사용자의 JWT — DUO_MODE_DISABLED 게이트 통과에 필요 (TESTER 역할 확인)
 */
export async function joinSession(
  request: APIRequestContext,
  inviteToken: string,
  nickname: string,
  token: string,
): Promise<{ id: string }> {
  const resp = await request.post(`${BASE}/api/sessions/join/${inviteToken}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { nickname, asGuest: false },
  })
  if (!resp.ok()) throw new Error(`Join failed: ${resp.status()} — ${await resp.text()}`)
  return resp.json()
}

/** 파트너 메시지 메타데이터 조회 */
export async function getPartnerMessages(
  request: APIRequestContext,
  token: string,
  sessionId: string,
): Promise<Record<string, unknown>[]> {
  const resp = await request.get(`${BASE}/api/sessions/${sessionId}/partner-messages`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) return []
  return resp.json()
}

/** Admin: 사용자 ID 조회 */
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

/** Admin: 사용자 roles 변경 */
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
  if (!resp.ok()) throw new Error(`PATCH roles failed: ${resp.status()} — ${await resp.text()}`)
}
