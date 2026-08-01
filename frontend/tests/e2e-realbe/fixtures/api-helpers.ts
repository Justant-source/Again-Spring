import type { APIRequestContext } from '@playwright/test'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

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
