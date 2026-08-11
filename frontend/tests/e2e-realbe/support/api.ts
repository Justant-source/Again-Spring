/**
 * BE 셋업 호출 단일 출처.
 *
 * - createPost로 만든 글은 레지스트리에 등록 → no-llm-fixture afterEach에서 삭제
 * - 모든 spec은 인라인 fetch 대신 이 모듈을 사용한다.
 * - 기존 fixtures/api-helpers.ts를 통합; api-helpers.ts는 여기로 포인터만 남김.
 */
import type { APIRequestContext } from '@playwright/test'
import fs from 'fs'
import path from 'path'
import { E2E_DEFAULT_BASE_URL } from './env'
import { forceDeletePostById } from './db'

/** 호출 시점의 E2E_BASE_URL — 모듈 로드 시점 캡처 금지 */
function e2eBase(): string {
  return process.env.E2E_BASE_URL ?? E2E_DEFAULT_BASE_URL
}

/** request 객체에 붙이는 추적 키 — 모듈 싱글톤 불일치 방지 */
export const E2E_CREATED_POSTS = Symbol.for('againspring.e2e.createdPosts')

/** 테스트 중 createPost로 생성된 사연 — fixture afterEach가 비운다 */
export type TrackedPost = { id: string; token: string }

function trackCreatedPost(
  request: APIRequestContext,
  id: string,
  token: string,
): void {
  const bag = (request as unknown as Record<symbol, TrackedPost[]>)[E2E_CREATED_POSTS]
  if (bag) bag.push({ id, token })
}

// ── LLM 안전 검사 ─────────────────────────────────────────────────

/** LLM을 트리거하는 것으로 알려진 엔드포인트 패턴 */
const LLM_DANGEROUS_PATHS = [
  /\/api\/admin\/content\/corrections\/analyze/,
  /\/api\/admin\/ai-rules\/history\/[^/]+\/analyze/,
  /\/api\/admin\/ai-rules\/history\/analyze-batch/,
  /\/api\/admin\/marketing\/[^/]+\/(generate|simulation|story)/,
]

export function assertNoLlmRequest(url: string, method: string, _body?: string): void {
  for (const pattern of LLM_DANGEROUS_PATHS) {
    if (pattern.test(url)) {
      throw new Error(`[no-llm-guardrail] LLM을 트리거하는 엔드포인트 호출 감지: ${method} ${url}`)
    }
  }
}

// ── 인증 ──────────────────────────────────────────────────────────

export async function login(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const resp = await request.post(`${e2eBase()}/api/auth/login`, {
    data: { email, password },
  })
  if (!resp.ok()) throw new Error(`Login 실패: ${resp.status()} — ${await resp.text()}`)
  const { token } = await resp.json()
  return token.accessToken as string
}

export async function guestLogin(request: APIRequestContext, nickname?: string): Promise<string> {
  const resp = await request.post(`${e2eBase()}/api/auth/guest`, {
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
 * 사연 생성.
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
  const url = `${e2eBase()}/api/community/posts`
  const data = {
    bodyRaw: body,
    category,
    visibility: 'PUBLIC',
    userTitle: title,
  }
  // API 경로 LLM 안전 검사
  assertNoLlmRequest(url, 'POST', JSON.stringify(data))

  const resp = await request.post(url, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  })
  if (!resp.ok()) throw new Error(`포스트 생성 실패: ${resp.status()} — ${await resp.text()}`)
  const id = (await resp.json()).id as string
  trackCreatedPost(request, id, token)
  return id
}

/**
 * 작성자 JWT로 사연 삭제 (DELETE /api/community/posts/{id}).
 * createPost 추적 정리·명시적 teardown에서 사용. 이미 없으면(404) 성공으로 본다.
 */
export async function deletePost(
  request: APIRequestContext,
  token: string,
  postId: string,
): Promise<void> {
  const resp = await request.delete(`${e2eBase()}/api/community/posts/${postId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (resp.ok() || resp.status() === 204 || resp.status() === 404) return
  throw new Error(`포스트 삭제 실패: ${resp.status()} — ${await resp.text()}`)
}

/**
 * 추적된 사연을 반드시 삭제한다.
 *
 * fixture teardown 중의 `request`는 불안정할 수 있어 **새 APIRequestContext**로
 * DELETE/GET 검증한다. (이전: 삭제 확인 OK인데 prod에 동일 ID가 남는 사고)
 */
export async function deleteTrackedPosts(
  _request: APIRequestContext,
  posts: TrackedPost[],
): Promise<void> {
  const { request: freshRequest } = await import('@playwright/test')
  const ctx = await freshRequest.newContext({ baseURL: e2eBase() })
  const errors: string[] = []
  try {
    for (const { id, token } of [...posts].reverse()) {
      try {
        await deletePost(ctx, token, id)
      } catch (e) {
        console.warn(`[e2e-cleanup] API 삭제 실패 ${id}:`, (e as Error).message)
      }

      try {
        forceDeletePostById(id)
      } catch (e) {
        console.warn(`[e2e-cleanup] SQL 강제 삭제 실패 ${id}:`, (e as Error).message)
      }

      if (await postStillExists(ctx, id)) {
        try {
          await deletePost(ctx, token, id)
        } catch { /* ignore */ }
        try {
          forceDeletePostById(id)
        } catch { /* ignore */ }
        if (await postStillExists(ctx, id)) {
          errors.push(`삭제 후에도 API로 조회됨: ${id}`)
          continue
        }
        console.log(`[e2e-cleanup] 삭제 확인 OK (2차): ${id}`)
      } else {
        console.log(`[e2e-cleanup] 삭제 확인 OK: ${id}`)
      }
    }
  } finally {
    await ctx.dispose()
  }
  posts.length = 0
  if (errors.length > 0) {
    throw new Error(`[e2e-cleanup] 사연 삭제 실패:\n${errors.join('\n')}`)
  }
}

/** create와 동일 채널로 잔존 여부 확인. 404/410 = 없음. */
async function postStillExists(
  request: APIRequestContext,
  postId: string,
): Promise<boolean> {
  const resp = await request.get(`${e2eBase()}/api/community/posts/${postId}`)
  if (resp.status() === 404 || resp.status() === 410) return false
  if (!resp.ok()) {
    // 5xx 등은 잔존으로 간주해 재삭제 유도
    console.warn(`[e2e-cleanup] GET ${postId} status=${resp.status()}`)
    return true
  }
  return true
}

// ── 초대 ──────────────────────────────────────────────────────────

export async function createInviteToken(
  request: APIRequestContext,
  token: string,
  postId: string,
): Promise<string> {
  const resp = await request.post(`${e2eBase()}/api/community/posts/${postId}/invite`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) throw new Error(`초대 토큰 생성 실패: ${resp.status()} — ${await resp.text()}`)
  return (await resp.json()).inviteToken as string
}

/**
 * 상대방 답변 제출.
 */
export async function submitPartnerAnswer(
  request: APIRequestContext,
  inviteToken: string,
  body = '상대방 답변입니다. e2e 테스트에 의해 자동 생성됩니다.',
  title = '상대방 입장 제목',
): Promise<void> {
  const resp = await request.post(`${e2eBase()}/api/s/${inviteToken}/answer`, {
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
    const resp = await request.get(`${e2eBase()}/api/community/posts/${postId}`)
    if (resp.ok()) {
      const data = await resp.json()
      if (data.paired || data.partnerBodyPublished) return true
    }
    await new Promise(r => setTimeout(r, 500))
  }
  return false
}

/** PATCH publish-mode (WAIT_FOR_PARTNER | PUBLISH_NOW). WAIT ≡ PUBLISH_NOW(즉시 PUBLIC). LLM 미호출. */
export async function setPublishMode(
  request: APIRequestContext,
  token: string,
  postId: string,
  mode: 'PUBLISH_NOW' | 'WAIT_FOR_PARTNER',
  voteDurationHours = 72,
): Promise<void> {
  const resp = await request.patch(`${e2eBase()}/api/community/posts/${postId}/publish-mode`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { mode, voteDurationHours },
  })
  if (!resp.ok()) throw new Error(`publish-mode 실패: ${resp.status()} — ${await resp.text()}`)
}

/** POST publish-now — visibility=PUBLIC + voteCloseAt. 이미 PUBLIC이면 보정용(파트너 대기 해제 아님). LLM 미호출. */
export async function publishNow(
  request: APIRequestContext,
  token: string,
  postId: string,
): Promise<void> {
  const resp = await request.post(`${e2eBase()}/api/community/posts/${postId}/publish-now`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok()) throw new Error(`publish-now 실패: ${resp.status()} — ${await resp.text()}`)
}

// ── 관리자 ────────────────────────────────────────────────────────

export async function findUserId(
  request: APIRequestContext,
  adminToken: string,
  email: string,
): Promise<string | null> {
  const resp = await request.get(
    `${e2eBase()}/api/admin/users/search?q=${encodeURIComponent(email)}`,
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
  const resp = await request.patch(`${e2eBase()}/api/admin/users/${userId}/roles`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { roles },
  })
  if (!resp.ok()) throw new Error(`PATCH roles 실패: ${resp.status()} — ${await resp.text()}`)
}
