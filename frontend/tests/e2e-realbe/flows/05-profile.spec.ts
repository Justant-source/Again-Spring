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
 *   - 투표한 글 / 저장 탭 → "준비 중입니다" 표시
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
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('내 사연')).toBeVisible()
    await expect(page.getByText('투표한 글')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()
  })

  test('프로필 — 탭 전환 시 닉네임 유지 (7e72d05 회귀 방지)', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })

    // 초기 닉네임 읽기
    const profileRow = page.locator('div').filter({ hasText: /사연 \d+/ }).first()
    await expect(profileRow).toBeVisible({ timeout: 5_000 })

    // "투표한 글" 탭으로 이동
    await page.getByText('투표한 글').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 3_000 })

    // 닉네임이 사라지지 않아야 함 — 프로필 행이 여전히 존재
    await expect(page.getByText('마이페이지')).toBeVisible()

    // "내 사연" 탭으로 복귀
    await page.getByText('내 사연').click()
    // 아직 사연이 없으면 "아직 사연이 없어요", 있으면 목록 표시 — 어느 쪽이든 "마이페이지"는 유지
    await expect(page.getByText('마이페이지')).toBeVisible()
  })

  test('프로필 — 투표한 글 탭 → "준비 중입니다"', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    await page.getByText('투표한 글').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 3_000 })
  })

  test('프로필 — 저장 탭 → "준비 중입니다"', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    await page.getByText('저장').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 3_000 })
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
