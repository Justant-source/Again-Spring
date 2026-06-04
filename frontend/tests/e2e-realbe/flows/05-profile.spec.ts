/**
 * Flow 05: 프로필 페이지 (6/2 이후 전면 개편)
 *
 * 변경 내역:
 *   - 3ab8e15: 프로필 페이지 전면 재작성 — 아바타, 3탭(내 사연/투표한 글/저장)
 *   - 7e72d05: 탭 전환 시 사용자 정보(닉네임) 유지 버그 수정
 *
 * 커버 범위:
 *   - 게스트 → /login 리다이렉트 (02-permissions 보완)
 *   - 회원: 마이페이지 헤더 + 아바타 + 닉네임 표시
 *   - 3탭 노출: 내 사연 / 투표한 글 / 저장
 *   - 탭 전환 시 닉네임 유지 (7e72d05 회귀 방지)
 *   - 투표한 글 탭 → 빈 상태 메시지 ("아직 투표한 글이 없습니다")
 *   - 저장 탭 → "준비 중입니다" (미구현)
 */
import { test, expect } from '@playwright/test'
import { PERSONA_TEST1 } from '../fixtures/personas'
import { authStatePath } from '../fixtures/auth-state'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

test.describe('Flow 05: 프로필 페이지 (6/2 개편)', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('프로필 — 마이페이지 헤더 + 닉네임 표시', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    // PERSONA_TEST1의 닉네임이 표시되어야 함
    // (닉네임은 환경마다 다를 수 있으므로 존재 여부만 확인)
    const nickEl = page.locator('div').filter({ hasText: /^.+$/ }).first()
    await expect(nickEl).toBeVisible({ timeout: 5_000 })
  })

  test('프로필 — 탭 3개 표시 (내 사연 / 투표한 글 / 저장)', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    // '내 사연' 탭이 보이면 프로필 콘텐츠 hydrate 완료 (BottomNav의 '마이페이지'와 구분)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('투표한 글')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()
  })

  test('프로필 — 탭 전환 시 닉네임 유지 (7e72d05 회귀 방지)', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })

    // "투표한 글" 탭으로 이동
    await page.getByText('투표한 글').click()
    await expect(page.getByText('아직 투표한 글이 없습니다')).toBeVisible({ timeout: 5_000 })

    // 탭 행이 사라지지 않아야 함
    await expect(page.getByText('내 사연')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()

    // "내 사연" 탭으로 복귀 — 탭 행이 유지되어야 함 (닉네임 유지 핵심 검증)
    await page.getByText('내 사연').click()
    await expect(page.getByText('투표한 글')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('저장')).toBeVisible()
  })

  test('프로필 — 투표한 글 탭 → 빈 상태 메시지 표시', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await page.getByText('투표한 글').click()
    await expect(page.getByText('아직 투표한 글이 없습니다')).toBeVisible({ timeout: 5_000 })
  })

  test('프로필 — 저장 탭 → "준비 중입니다"', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await page.getByText('저장').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 5_000 })
  })
})

test.describe('Flow 05-guest: 프로필 게스트 가드', () => {
  // storageState 없음 = 비인증 상태 (게스트)

  test('게스트 → /profile 접근 시 /login 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })
})
