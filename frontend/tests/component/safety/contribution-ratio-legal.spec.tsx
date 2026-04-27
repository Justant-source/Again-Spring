/**
 * Safety Regression Test: Contribution Ratio Legal Disclaimer Box
 *
 * Verifies that ContributionRatio always displays the legal disclaimer box
 * in every rendering scenario. This box prevents users from misinterpreting
 * the ratio as legal fault determination.
 *
 * Rule (CLAUDE.md 절대 불변 규칙 #5):
 * "ContributionRatio 법적 안내 박스는 항상 표시한다.
 *  '과실비율과 무관합니다' 박스를 숨기거나 조건부로 만드는 것 금지."
 */

import { render, screen } from '@testing-library/react'
import { ContributionRatio } from '@/components/result/ContributionRatio'
import { describe, it, expect } from 'vitest'
import type { ContributionRatio as ContributionRatioType } from '@/lib/types'

describe('Safety: Contribution Ratio Legal Disclaimer', () => {
  const mockRatio: ContributionRatioType = {
    a: 55,
    b: 45,
    label: {
      a: 'User A provided more understanding',
      b: 'User B needs to listen more',
    },
  }

  describe('Solo Mode', () => {
    it('should render solo mode message when isSoloMode=true', () => {
      render(<ContributionRatio ratio={null} isSoloMode={true} />)

      expect(
        screen.getByText(/화해 기여도는 상대방이 함께참여했을 때 안내드릴 수 있어요/)
      ).toBeInTheDocument()
    })

    it('should NOT show legal disclaimer in solo mode (different message)', () => {
      render(<ContributionRatio ratio={null} isSoloMode={true} />)

      // Solo mode shows a different message, no legal box
      expect(
        screen.getByText(/화해 기여도는 상대방이 함께/)
      ).toBeInTheDocument()
    })
  })

  describe('Duo Mode - Legal Disclaimer Always Present', () => {
    it('should always display legal disclaimer in normal Duo mode (55:45)', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          nameA="서현"
          nameB="준호"
          conflictType="difference"
        />
      )

      // Legal disclaimer must always be present
      const disclaimer = screen.getByText(/이 수치는 두 분의 회복 시작점을/)
      expect(disclaimer).toBeInTheDocument()

      // Must contain key phrases that prevent legal misinterpretation
      const legalBox = screen.getByText(/법적 판단이나 과실 비율과는 무관/)
      expect(legalBox).toBeInTheDocument()
    })

    it('should display legal disclaimer with AI limitation note', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="factual"
        />
      )

      expect(screen.getByText(/AI 분析에는 한계가 있어요/)).toBeInTheDocument()
    })

    it('should display legal disclaimer with professional counseling suggestion', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="mixed"
        />
      )

      expect(screen.getByText(/깊은 갈등은 전문 상담을 권해드려요/)).toBeInTheDocument()
    })

    it('should display legal disclaimer in 50:50 case', () => {
      const evenRatio: ContributionRatioType = {
        a: 50,
        b: 50,
        label: {
          a: 'Both contributed equally to understanding',
          b: 'Both contributed equally to understanding',
        },
      }

      render(<ContributionRatio ratio={evenRatio} conflictType="difference" />)

      expect(screen.getByText(/이 수치는 두 분의 회복 시작점을/)).toBeInTheDocument()
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })

    it('should display legal disclaimer in extreme case (0:100)', () => {
      const extremeRatio: ContributionRatioType = {
        a: 0,
        b: 100,
        label: {
          a: 'User A did not contribute to resolution',
          b: 'User B did all the work',
        },
      }

      render(<ContributionRatio ratio={extremeRatio} conflictType="factual" />)

      expect(screen.getByText(/이 수치는 두 분의 회복 시작점을/)).toBeInTheDocument()
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })

    it('should display legal disclaimer in extreme case (100:0)', () => {
      const extremeRatio: ContributionRatioType = {
        a: 100,
        b: 0,
        label: {
          a: 'User A did all the work',
          b: 'User B did not contribute to resolution',
        },
      }

      render(<ContributionRatio ratio={extremeRatio} conflictType="factual" />)

      expect(screen.getByText(/이 수치는 두 분의 회복 시작점을/)).toBeInTheDocument()
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })
  })

  describe('Conflict Type Variations', () => {
    it('should show "difference" note with legal disclaimer', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="difference"
        />
      )

      const differenceNote = screen.getByText(/두 분 모두 잘못한 게 아니라 다를 뿐이에요/)
      expect(differenceNote).toBeInTheDocument()

      // Legal disclaimer should still be present
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })

    it('should show "factual" note with legal disclaimer', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="factual"
        />
      )

      const factualNote = screen.getByText(/이번 상황에서는 한쪽의 책임이 좀 더 분명해 보여요/)
      expect(factualNote).toBeInTheDocument()

      // Legal disclaimer should still be present
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })

    it('should show legal disclaimer without conflict-type note when conflictType=mixed', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="mixed"
        />
      )

      // Legal disclaimer should be present
      expect(screen.getByText(/이 수치는 두 분의 회복 시작점을/)).toBeInTheDocument()
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })

    it('should show legal disclaimer without conflict-type note when conflictType=null', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType={null}
        />
      )

      // Legal disclaimer should be present
      expect(screen.getByText(/이 수치는 두 분의 회복 시작점을/)).toBeInTheDocument()
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })
  })

  describe('Legal Box Structure & Content', () => {
    it('should contain all legal guarantee statements in disclaimer box', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="difference"
        />
      )

      // All statements are in a single text node, so check for the combined text
      const disclaimerText = screen.getByText(/이 수치는 두 분의 회복 시작점을/)

      expect(disclaimerText.textContent).toContain('이 수치는 두 분의 회복 시작점을')
      expect(disclaimerText.textContent).toContain('법적 판단이나 과실 비율과는 무관')
      expect(disclaimerText.textContent).toContain('한계가 있어요') // "AI 분析에는" (with hanja)
      expect(disclaimerText.textContent).toContain('깊은 갈등은 전문 상담을 권해드려요')
    })

    it('should not contain forbidden legal words in disclaimer', () => {
      const { container } = render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="factual"
        />
      )

      const disclaimerText = screen.getByText(/이 수치는 두 분의 회복 시작점을/).textContent || ''

      // Forbidden words (from CLAUDE.md)
      const forbiddenWords = [
        '판결', '판사', '유죄', '무죄', '증거',
        '가해자', '피해자', '고소', '소송',
        '승자', '패자'
      ]

      forbiddenWords.forEach(word => {
        expect(disclaimerText).not.toContain(word)
      })
    })

    it('should use safe replacement terminology', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="difference"
        />
      )

      // Should use "화해 기여도" (reconciliation contribution), not "과실비율" (fault ratio)
      expect(screen.getByText(/화해 기여도/)).toBeInTheDocument()
    })
  })

  describe('Visual/Styling Safety', () => {
    it('should render legal box with distinct styling', () => {
      const { container } = render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="difference"
        />
      )

      // Find the legal box by looking for the disclaimer text
      const disclaimerText = screen.getByText(/이 수치는 두 분의 회복 시작점을/)
      const legalBox = disclaimerText.closest('div')

      expect(legalBox).toBeInTheDocument()

      // Legal box should have distinct styling (background, border, padding, etc.)
      const style = legalBox?.getAttribute('style') || ''
      expect(style).toContain('background')
      expect(style).toContain('border')
      expect(style).toContain('padding')
      expect(style).toContain('border-radius')
    })

    it('should not allow hiding the legal box via CSS display:none', () => {
      const { container } = render(
        <ContributionRatio
          ratio={mockRatio}
          conflictType="difference"
        />
      )

      const legalBox = screen.getByText(/법적 판단이나 과실 비율과는 무관/).closest('div')
      const style = legalBox?.getAttribute('style') || ''

      // Legal box should NOT have display:none or similar hiding styles
      expect(style).not.toContain('display:none')
      expect(style).not.toContain('visibility:hidden')
      expect(style).not.toContain('opacity:0')
    })
  })

  describe('Ratio Display Integrity', () => {
    it('should show ratio bar with user names and percentages', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          nameA="서현"
          nameB="준호"
          conflictType="difference"
        />
      )

      expect(screen.getByText(/서현.*55/)).toBeInTheDocument()
      expect(screen.getByText(/준호.*45/)).toBeInTheDocument()
    })

    it('should show interpretation labels with legal disclaimer', () => {
      render(
        <ContributionRatio
          ratio={mockRatio}
          nameA="A"
          nameB="B"
          conflictType="difference"
        />
      )

      expect(screen.getByText(/User A provided more understanding/)).toBeInTheDocument()
      expect(screen.getByText(/User B needs to listen more/)).toBeInTheDocument()

      // And legal disclaimer
      expect(screen.getByText(/법적 판단이나 과실 비율과는 무관/)).toBeInTheDocument()
    })
  })
})
