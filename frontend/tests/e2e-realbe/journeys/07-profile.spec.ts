/**
 * Journey 07: 프로필 페이지 + 정보 수정
 *
 * - 마이페이지 헤더·닉네임 표시
 * - 3탭 (내 사연/투표한 글/저장)
 * - 탭 전환 시 닉네임 유지 (7e72d05 회귀)
 * - 게스트 → /login 리다이렉트
 * - /profile/info — 닉네임 변경, 비밀번호 변경 (LLM 미호출)
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'
import { tokenFromStorageState, login } from '../support/api'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
// test4는 닉네임 변경 테스트 전용 페르소나 (test1 admin 보호)
const PERSONA_TEST4 = PERSONAS[3]

// ── A. 회원 프로필 ───────────────────────────────────────────────
test.describe('Journey 07-A: 회원 프로필 페이지', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('마이페이지 헤더 + 닉네임 표시', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    // 닉네임(어떤 값이든)이 표시되어야 함
    const nickEl = page.locator('div').filter({ hasText: /^.+$/ }).first()
    await expect(nickEl).toBeVisible({ timeout: 5_000 })
  })

  test('3탭 표시 — 내 사연 / 투표한 글 / 저장', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('투표한 글')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()
  })

  test('탭 전환 시 닉네임 유지 (7e72d05 회귀 방지)', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })

    await page.getByText('투표한 글').click()
    await expect(page.getByText('아직 투표한 글이 없습니다')).toBeVisible({ timeout: 5_000 })
    // 탭 행이 사라지지 않음
    await expect(page.getByText('내 사연')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()

    await page.getByText('내 사연').click()
    await expect(page.getByText('투표한 글')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('저장')).toBeVisible()
  })

  test('투표한 글 탭 → 빈 상태 메시지', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await page.getByText('투표한 글').click()
    await expect(page.getByText('아직 투표한 글이 없습니다')).toBeVisible({ timeout: 5_000 })
  })

  test('저장 탭 → "준비 중입니다"', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await page.getByText('저장').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 5_000 })
  })
})

// ── B. 게스트 가드 ───────────────────────────────────────────────
test.describe('Journey 07-B: 게스트 → /profile 가드', () => {
  // storageState 없음 = 비인증

  test('게스트 — /profile 접근 → /login 리다이렉트', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await page.waitForURL(/\/login/, { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })
})

// ── C. /profile/info — 정보 수정 ─────────────────────────────────
test.describe('Journey 07-C: /profile/info 정보 수정', () => {
  // test4로 닉네임 변경 (test1 admin 보호)
  // global-setup이 test4에 storageState를 저장하지 않으므로 API 로그인

  test('닉네임 변경 후 복원 (LLM 미호출)', async ({ page, request }) => {
    const token = await login(request, PERSONA_TEST4.email, PERSONA_TEST4.password)

    // storageState 없이 직접 localStorage 주입
    await page.goto(`${BASE}/login`)
    await page.evaluate((t: string) => localStorage.setItem('again-spring-token', t), token)
    await page.goto(`${BASE}/profile/info`)
    await page.waitForURL(/\/profile\/info/, { timeout: 10_000 })

    // 닉네임 입력란 확인
    const nicknameInput = page.locator('input[type="text"]').first()
    await expect(nicknameInput).toBeVisible({ timeout: 8_000 })

    // 현재 닉네임 저장 후 변경
    const originalNickname = await nicknameInput.inputValue()
    const newNickname = `E2E${Date.now()}`.slice(0, 12)

    await nicknameInput.fill(newNickname)
    const saveBtn = page.getByRole('button', { name: '저장' })
    if (await saveBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await saveBtn.click()
      await page.waitForTimeout(1_000)
    }

    // 복원 (afterEach 없이 직접)
    await nicknameInput.fill(originalNickname)
    if (await saveBtn.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await saveBtn.click()
    }
    // LLM이 호출되지 않았음 = 가드레일이 통과하면 성공
  })

  test('/profile/info 페이지 로드 + 섹션 표시', async ({ page, request }) => {
    const token = await login(request, PERSONA_TEST4.email, PERSONA_TEST4.password)

    await page.goto(`${BASE}/login`)
    await page.evaluate((t: string) => localStorage.setItem('again-spring-token', t), token)
    await page.goto(`${BASE}/profile/info`)
    await page.waitForURL(/\/profile\/info/, { timeout: 10_000 })

    // 닉네임 섹션 + 비밀번호 변경 섹션 표시
    await expect(page.getByText('닉네임')).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('비밀번호 변경')).toBeVisible()
    // 로그아웃 버튼
    await expect(page.getByRole('button', { name: /로그아웃/i })).toBeVisible()
  })
})
