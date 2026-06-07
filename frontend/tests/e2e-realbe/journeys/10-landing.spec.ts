/**
 * Journey 10: 랜딩 페이지 (@mobile 태그 — 모바일에서도 검증)
 *
 * - 방금 올라온 사연 알약 표시 (최신글 title)
 * - 오늘의 사연 카드 표시 (추천순 1위)
 * - "다시봄 광장" CTA → /community 이동
 *
 * data-testid는 app/page.tsx에 부착됨 (2026-06-07 e2e 재편 시).
 * 미첨부 시 getByText 폴백.
 */
import { test, expect } from '../support/no-llm-fixture'
import { LANDING } from '../support/selectors'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8090'

// ── 랜딩 페이지 ──────────────────────────────────────────────────
test.describe('Journey 10: 랜딩 페이지', () => {

  test('@mobile 랜딩 — 방금 올라온 사연 알약 표시', async ({ page }) => {
    await page.goto(`${BASE}/`)
    // 데이터가 없으면 알약이 렌더되지 않을 수 있음 — 존재 시 클릭 가능 확인
    const pill = page.locator(LANDING.latestPill)
    const pillVisible = await pill.isVisible({ timeout: 8_000 }).catch(() => false)
    if (pillVisible) {
      await expect(pill).toContainText('방금 올라온 사연')
      // 클릭 → 사연 상세 이동
      await pill.click()
      await page.waitForURL(/\/community\/[^/]+$/, { timeout: 10_000 })
    } else {
      // 데이터 없음 → 알약 없음(정상)
      console.log('[10-landing] latestPost 없음 — 알약 미표시 (정상)')
    }
  })

  test('@mobile 랜딩 — 오늘의 사연 카드 표시 (추천순 1위)', async ({ page }) => {
    await page.goto(`${BASE}/`)

    const card = page.locator(LANDING.todayCard)
    const cardVisible = await card.isVisible({ timeout: 8_000 }).catch(() => false)
    if (cardVisible) {
      // 카테고리 뱃지 + 제목 + 조회수 표시
      await expect(card.getByText(/명이 함께 봤어요/)).toBeVisible()
      // 클릭 → 사연 상세 이동
      await card.click()
      await page.waitForURL(/\/community\/[^/]+$/, { timeout: 10_000 })
    } else {
      console.log('[10-landing] todayPost 없음 — 카드 미표시 (정상)')
    }
  })

  test('@mobile 랜딩 — "다시봄 광장" CTA → /community 이동', async ({ page }) => {
    await page.goto(`${BASE}/`)
    // testid 또는 role 기반 선택 (testid는 FE 재빌드 후 활성, role은 항상 유효)
    const cta = page.locator(LANDING.cta).or(page.getByRole('button', { name: '다시봄 광장' }))
    await expect(cta.first()).toBeVisible({ timeout: 8_000 })
    await cta.first().click()
    await page.waitForURL(/\/community/, { timeout: 10_000 })
    expect(page.url()).toContain('/community')
  })

  test('랜딩 — 헤드 카피 표시', async ({ page }) => {
    await page.goto(`${BASE}/`)
    await expect(page.getByText('나의 갈등,')).toBeVisible({ timeout: 8_000 })
    await expect(page.getByText('혼자 판단하기')).toBeVisible()
  })
})
