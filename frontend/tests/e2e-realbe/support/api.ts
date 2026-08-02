/**
 * BE 셋업 호출 단일 출처.
 *
 * - createPost: 항상 jurorCount=0 강제 → LLM 미호출 보장
 * - createPost로 만든 글은 레지스트리에 등록 → no-llm-fixture afterEach에서 삭제
 * - 모든 spec은 인라인 fetch 대신 이 모듈을 사용한다.
 * - 기존 fixtures/api-helpers.ts를 통합; api-helpers.ts는 여기로 포인터만 남김.
 */
import type { APIRequestContext } from '@playwright/test'
import fs from 'fs'
import path from 'path'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'

/** 테스트 중 createPost로 생성된 사연 — fixture afterEach가 비운다 */
type TrackedPost = { id: string; token: string }
let postCleanupBucket: TrackedPost[] | null = null

/** no-llm-fixture가 테스트 시작/종료 시 호출. 버킷에 쌓인 글을 afterEach에서 삭제한다. */
export function beginCreatedPostTracking(bucket: TrackedPost[]): void {
  postCleanupBucket = bucket
}

export function endCreatedPostTracking(): void {
  postCleanupBucket = null
}

function trackCreatedPost(id: string, token: string): void {
  if (postCleanupBucket) postCleanupBucket.push({ id, token })
}

// ── LLM 안전 검사 ─────────────────────────────────────────────────

/** LLM을 트리거하는 것으로 알려진 엔드포인트 패턴 */
const LLM_DANGEROUS_PATHS = [
  /\/api\/community\/posts\/[^/]+\/jury\/retry/,
  /\/api\/admin\/content\/corrections\/analyze/,
  /\/api\/admin\/ai-rules\/history\/[^/]+\/analyze/,
  /\/api\/admin\/ai-rules\/history\/analyze-batch/,
  /\/api\/admin\/marketing\/[^/]+\/(generate|simulation|story)/,
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
  const id = (await resp.json()).id as string
  trackCreatedPost(id, token)
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
  const resp = await request.delete(`${BASE}/api/community/posts/${postId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (resp.ok() || resp.status() === 204 || resp.status() === 404) return
  throw new Error(`포스트 삭제 실패: ${resp.status()} — ${await resp.text()}`)
}

/**
 * 추적된(또는 인자로 받은) 사연을 역순 삭제.
 * 개별 실패는 경고만 — 이후 글 정리·global teardown이 이어서 처리.
 */
export async function deleteTrackedPosts(
  request: APIRequestContext,
  posts: TrackedPost[],
): Promise<void> {
  for (const { id, token } of [...posts].reverse()) {
    try {
      await deletePost(request, token, id)
    } catch (e) {
      console.warn(`[e2e-cleanup] post ${id} 삭제 실패:`, (e as Error).message)
    }
  }
  posts.length = 0
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

/** PATCH publish-mode (WAIT_FOR_PARTNER | PUBLISH_NOW). LLM 미호출. */
export async function setPublishMode(
  request: APIRequestContext,
  token: string,
  postId: string,
  mode: 'PUBLISH_NOW' | 'WAIT_FOR_PARTNER',
  voteDurationHours = 72,
): Promise<void> {
  const resp = await request.patch(`${BASE}/api/community/posts/${postId}/publish-mode`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { mode, voteDurationHours },
  })
  if (!resp.ok()) throw new Error(`publish-mode 실패: ${resp.status()} — ${await resp.text()}`)
}

/** POST publish-now — WAIT_FOR_PARTNER 사연을 즉시 공개. LLM 미호출. */
export async function publishNow(
  request: APIRequestContext,
  token: string,
  postId: string,
): Promise<void> {
  const resp = await request.post(`${BASE}/api/community/posts/${postId}/publish-now`, {
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
