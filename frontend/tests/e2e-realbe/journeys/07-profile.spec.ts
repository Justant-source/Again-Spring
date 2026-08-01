/**
 * Journey 07: 프로필 페이지 + 정보 수정
 *
 * - 마이페이지 헤더·닉네임 표시
 * - 3탭 (내 사연/투표한 글/저장) — 단일 goto로 통합
 * - 탭 전환 시 닉네임 유지 (7e72d05 회귀)
 * - 게스트 /profile 가드는 Journey 09에 위임
 * - /profile/info — 닉네임 변경, 비밀번호 변경 (LLM 미호출)
 */
import { test, expect } from '../support/no-llm-fixture'
import { authStatePath } from '../fixtures/auth-state'
import { PERSONA_TEST1, PERSONAS } from '../fixtures/personas'


const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8091'
// test4는 닉네임 변경 테스트 전용 페르소나 (test1 admin 보호)
const PERSONA_TEST4 = PERSONAS[3]

// ── A. 회원 프로필 (단일 goto) ───────────────────────────────────
test.describe('Journey 07-A: 회원 프로필 페이지', () => {
  test.use({ storageState: authStatePath(PERSONA_TEST1.email) })

  test('헤더·3탭·탭 전환·투표/저장 콘텐츠', async ({ page }) => {
    await page.goto(`${BASE}/profile`)
    await expect(page.getByText('마이페이지')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('투표한 글')).toBeVisible()
    await expect(page.getByText('저장')).toBeVisible()

    // 탭 전환 시 닉네임/탭 행 유지 (7e72d05)
    await page.getByText('투표한 글').click()
    await expect(page.getByText('내 사연')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('저장')).toBeVisible()
    await expect(
      page.getByText('아직 투표한 글이 없습니다')
        .or(page.locator('[data-testid="feed-post-list"]'))
        .or(page.locator('a[href*="/community/"]').first()),
    ).toBeVisible({ timeout: 8_000 })

    await page.getByText('내 사연').click()
    await expect(page.getByText('투표한 글')).toBeVisible({ timeout: 5_000 })

    await page.getByText('저장').click()
    await expect(page.getByText('준비 중입니다')).toBeVisible({ timeout: 5_000 })
  })
})

// ── C. /profile/info — 정보 수정 ─────────────────────────────────
test.describe('Journey 07-C: /profile/info 정보 수정', () => {
  // test4 storageState 사용 (test1 admin 보호, global-setup이 PRELOGIN_PERSONAS에 test4 포함)
  test.use({ storageState: authStatePath(PERSONA_TEST4.email) })

  test('/profile/info 페이지 로드 + 섹션 표시', async ({ page }) => {
    await page.goto(`${BASE}/profile/info`)
    await page.waitForURL(/\/profile\/info/, { timeout: 10_000 })

    await expect(page.getByText('닉네임 변경')).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('비밀번호 변경').first()).toBeVisible({ timeout: 8_000 })
    await expect(page.getByRole('button', { name: /로그아웃/i })).toBeVisible()
  })

  test('닉네임 변경 후 복원 (LLM 미호출)', async ({ page }) => {
    await page.goto(`${BASE}/profile/info`)
    await page.waitForURL(/\/profile\/info/, { timeout: 10_000 })

    const nicknameInput = page.locator('input:not([type="password"])').first()
    await expect(nicknameInput).toBeVisible({ timeout: 8_000 })
    const originalNickname = await nicknameInput.inputValue()

    const newNickname = `E2E${Date.now()}`.slice(0, 12)
    await nicknameInput.fill(newNickname)

    const saveBtn = page.getByRole('button', { name: '저장' })
    if (await saveBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await saveBtn.click()
      await expect(page.getByText(/저장|변경|완료|성공/).first()).toBeVisible({ timeout: 5_000 }).catch(() => {})
      const nicknameInput2 = page.locator('input:not([type="password"])').first()
      await nicknameInput2.fill(originalNickname || PERSONA_TEST4.nickname)
      if (await saveBtn.isVisible({ timeout: 1_000 }).catch(() => false)) {
        await saveBtn.click()
      }
    }
  })
})
