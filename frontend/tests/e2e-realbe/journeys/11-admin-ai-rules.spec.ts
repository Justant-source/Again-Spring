/**
 * Journey 11: 관리자 AI 규칙관리 (비-LLM 경로만)
 *
 * - /admin/ai-rules 페이지 + 페르소나 탭
 * - 전역 금지 규칙 CRUD 1경로 (대표)
 * - /admin/content UI 스모크
 * - 사이드바 링크
 * - prompts slash-key 회귀 (voice/post)
 * - voice 가이드 품질 핵심 회귀 2건
 *
 * 첨삭/history/apply-batch-plan·content API 계약은 BE로 이관.
 * 비관리자 403은 Journey 09-B2.
 * LLM 가드레일: /analyze, /analyze-batch 차단.
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { tokenFromStorageState } from '../support/api'
import { ADMIN_CONTENT } from '../support/selectors'
import fs from 'node:fs'
import path from 'node:path'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const ADMIN_AUTH = authStatePath(PERSONA_TEST1.email)

test.describe('Journey 11-A: /admin/ai-rules 페이지', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 — /admin/ai-rules 페이지 로드 + 페르소나 탭', async ({ page }) => {
    await page.goto(`${BASE}/admin/ai-rules`)
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 10_000 })
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })

    await page.getByRole('tab', { name: '페르소나 주의사항' }).click()
    await expect(page.getByText('페르소나 ID 필터')).toBeVisible({ timeout: 5_000 })
  })
})

test.describe('Journey 11-B: 전역 금지 규칙 CRUD', () => {
  test('전역 규칙 추가 → 조회 → 비활성화 → 삭제', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}` }

    const createResp = await request.post(`${BASE}/api/admin/ai-rules/global`, {
      headers,
      data: { ruleText: '[e2e테스트] 자동화 테스트 전역 규칙 — 삭제 예정', scope: 'ALL' },
    })
    expect(createResp.status()).toBe(201)
    const created = await createResp.json()
    const createdRuleId = created.id
    expect(created.ruleText).toContain('e2e테스트')
    expect(created.active).toBe(true)

    const listResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    expect(listResp.ok()).toBeTruthy()
    const list = await listResp.json()
    const found = list.content.find((r: { id: number }) => r.id === createdRuleId)
    expect(found).toBeTruthy()
    expect(found.scope).toBe('ALL')

    const toggleResp = await request.patch(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, {
      headers,
      data: { active: false },
    })
    expect(toggleResp.ok()).toBeTruthy()
    expect((await toggleResp.json()).active).toBe(false)

    const deleteResp = await request.delete(`${BASE}/api/admin/ai-rules/global/${createdRuleId}`, { headers })
    expect(deleteResp.status()).toBe(204)

    const listAfterResp = await request.get(`${BASE}/api/admin/ai-rules/global`, { headers })
    const listAfter = await listAfterResp.json()
    expect(listAfter.content.find((r: { id: number }) => r.id === createdRuleId)).toBeFalsy()
  })
})

test.describe('Journey 11-C: /admin/content UI', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('대기·완료 탭·액션 컬럼', async ({ page }) => {
    await page.goto(`${BASE}/admin/content`)
    await page.waitForURL(/\/admin\/content/, { timeout: 10_000 })
    await expect(page.getByText('콘텐츠 관리')).toBeVisible({ timeout: 8_000 })
    await expect(page.locator(ADMIN_CONTENT.tabs)).toBeVisible()
    await expect(page.locator(ADMIN_CONTENT.tabHolding)).toBeVisible()
    await expect(page.locator(ADMIN_CONTENT.tabPublished)).toBeVisible()
    await expect(page.getByRole('tab', { name: '대기' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '완료' })).toBeVisible()

    // 기본 탭 = 완료(공개됨)
    await expect(page.locator(ADMIN_CONTENT.publishedPanel)).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '작성 시각 (KST)' })).toBeVisible()
    await expect(page.getByRole('button', { name: '추가', exact: true })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '액션' }).first()).toBeVisible({ timeout: 10_000 })

    await page.locator(ADMIN_CONTENT.tabHolding).click()
    await expect(page.locator(ADMIN_CONTENT.holdingPanel)).toBeVisible({ timeout: 8_000 })
    // 대기 큐는 새벽 배치가 발행을 끝내면 자연스럽게 0건이 된다 — 그때는 테이블 대신
    // emptyMessage가 렌더된다. 두 상태 모두 "정상 렌더"이므로 둘 중 하나만 확인한다.
    const columnHeader = page.getByRole('columnheader', { name: '글 발행 예정 (KST)' })
    const emptyState = page.getByText('대기 중인 글이 없습니다')
    await expect(columnHeader.or(emptyState)).toBeVisible({ timeout: 8_000 })
  })
})

test.describe('Journey 11-E: 사이드바 AI 규칙관리 링크', () => {
  test.use({ storageState: ADMIN_AUTH })

  test('관리자 사이드바 — "AI 규칙관리" 링크 표시 + 이동', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto(`${BASE}/admin`)
    await page.waitForURL(/\/admin/, { timeout: 10_000 })

    const aiRulesLink = page.getByRole('link', { name: 'AI 규칙관리' })
    await expect(aiRulesLink).toBeVisible({ timeout: 8_000 })
    await aiRulesLink.click()
    await page.waitForURL(/\/admin\/ai-rules/, { timeout: 8_000 })
    await expect(page.getByText('AI 규칙 관리')).toBeVisible({ timeout: 8_000 })
  })
})

test.describe('Journey 11-I: 기본 프롬프트 템플릿 CRUD', () => {
  test('PUT /prompts/voice/post → 저장 성공 (슬래시 키 경로 버그 회귀)', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' }

    const getResp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/post`, { headers })
    expect(getResp.ok()).toBeTruthy()
    const original = await getResp.json()

    const putResp = await request.put(`${BASE}/api/admin/ai-rules/prompts/voice/post`, {
      headers,
      data: { content: original.content },
    })
    expect(putResp.ok()).toBeTruthy()
    const updated = await putResp.json()
    expect(updated.key).toBe('voice/post')
    expect(typeof updated.content).toBe('string')
  })
})

test.describe('Journey 11-J: voice 가이드 품질 회귀 가드', () => {
  test('voice/comment — casual에 존댓말 어미 없음 + polite 해요체', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    expect(accessToken).toBeTruthy()
    const headers = { Authorization: `Bearer ${accessToken}` }

    // Flyway V72는 content='' 로 키만 심음. ai-user-llm 시드·prod-dev-sync 전에
    // 비면 classpath 가이드로 채워 회귀 가드가 레이스에 깨지지 않게 한다.
    let resp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/comment`, { headers })
    expect(resp.ok()).toBeTruthy()
    let content: string = (await resp.json()).content ?? ''
    if (content.length <= 100) {
      const seedPath = path.resolve(__dirname, '../../../../ai-user/llm/src/main/resources/voice/comment.md')
      const seed = fs.readFileSync(seedPath, 'utf8')
      const putResp = await request.put(`${BASE}/api/admin/ai-rules/prompts/voice/comment`, {
        headers,
        data: { content: seed },
      })
      expect(putResp.ok()).toBeTruthy()
      resp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/comment`, { headers })
      content = (await resp.json()).content ?? ''
    }
    expect(content.length).toBeGreaterThan(100)
    expect(content).toMatch(/반말만|요.*금지|습니다.*금지/)

    const casualSection = content.split(/존댓말\s*모드|polite\s*mode/i)[0]
    expect(casualSection).not.toMatch(/일 것 같아요/)
    expect(casualSection).not.toMatch(/더라고요.*조언|조언.*더라고요/)

    expect(content).toMatch(/존댓말\s*모드|formality.*polite|polite.*페르소나/i)
    const politeSection = content.split(/존댓말\s*모드|polite\s*mode/i)[1] ?? ''
    expect(politeSection).toMatch(/어요|더라고요|것 같아요/)
  })

  test('voice/post·reply — 핵심 규칙 + 4템플릿 로드', async ({ request }) => {
    const accessToken = tokenFromStorageState(PERSONA_TEST1.email)
    const headers = { Authorization: `Bearer ${accessToken}` }

    const listResp = await request.get(`${BASE}/api/admin/ai-rules/prompts`, { headers })
    expect(listResp.ok()).toBeTruthy()
    const list = await listResp.json()
    const keys = list.map((t: { key?: string; name?: string }) => t.key ?? t.name ?? '')
    for (const key of ['voice/comment', 'voice/post', 'voice/reply', 'voice/partner']) {
      expect(keys.some((k: string) => k.includes(key.split('/')[1]))).toBeTruthy()
    }

    const postResp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/post`, { headers })
    const postContent: string = (await postResp.json()).content ?? ''
    expect(postContent).toMatch(/구체.*사건|trigger|X가 Y/)
    expect(postContent).toMatch(/온점.*금지|금지.*온점/)

    const replyResp = await request.get(`${BASE}/api/admin/ai-rules/prompts/voice/reply`, { headers })
    const replyContent: string = (await replyResp.json()).content ?? ''
    expect(replyContent).toMatch(/초단|15.*40자|40자/)
    expect(replyContent).toMatch(/쌍따옴표.*금지|금지.*쌍따옴표/)
  })
})
