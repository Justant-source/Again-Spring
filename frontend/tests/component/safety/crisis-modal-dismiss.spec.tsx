/**
 * Safety Regression Test: Crisis Modal Dismiss Friction
 *
 * Verifies that CrisisModal and CrisisResourceModal cannot be dismissed
 * by ESC key or backdrop click. This prevents accidental closure when
 * users are in crisis situations.
 *
 * Rule (CLAUDE.md 절대 불변 규칙 #1):
 * "위기 모달(CrisisModal, CrisisResourceModal)은 ESC·바깥 클릭으로 닫히지 않는다.
 *  onClick={onClose} on backdrop, ESC keydown handler → 절대 추가 금지."
 */

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CrisisModal } from '@/components/chat/CrisisModal'
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal'
import { describe, it, expect, vi } from 'vitest'

describe('Safety: Crisis Modal Dismiss Friction', () => {
  describe('CrisisModal', () => {
    it('should render the crisis modal with title and helplines', () => {
      const onClose = vi.fn()
      render(<CrisisModal onClose={onClose} />)

      expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
      expect(screen.getByText(/여성긴급전화/)).toBeInTheDocument()
      expect(screen.getByText(/자살예방상담/)).toBeInTheDocument()
      expect(screen.getByText(/가정폭력/)).toBeInTheDocument()
      expect(screen.getByText(/아동학대/)).toBeInTheDocument()
    })

    it('should NOT dismiss when ESC key is pressed', async () => {
      const onClose = vi.fn()
      const { container } = render(<CrisisModal onClose={onClose} />)
      const user = userEvent.setup()

      // Modal should be in DOM
      const modalContent = container.querySelector('[style*="position: fixed"]')
      expect(modalContent).toBeInTheDocument()

      // Simulate ESC key press
      await user.keyboard('{Escape}')

      // onClose should NOT have been called
      expect(onClose).not.toHaveBeenCalled()

      // Modal should still be in DOM
      expect(modalContent).toBeInTheDocument()
    })

    it('should NOT dismiss when backdrop is clicked', async () => {
      const onClose = vi.fn()
      const { container } = render(<CrisisModal onClose={onClose} />)
      const user = userEvent.setup()

      // Find the backdrop (outer fixed div)
      const backdrop = container.firstChild as HTMLElement
      expect(backdrop).toBeInTheDocument()

      // Click the backdrop
      await user.click(backdrop)

      // onClose should NOT have been called
      expect(onClose).not.toHaveBeenCalled()
    })

    it('should dismiss ONLY when the close button is clicked', async () => {
      const onClose = vi.fn()
      render(<CrisisModal onClose={onClose} />)
      const user = userEvent.setup()

      // Find and click the close button
      const closeButton = screen.getByText('지금은 괜찮아요')
      expect(closeButton).toBeInTheDocument()

      await user.click(closeButton)

      // onClose SHOULD be called
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should have hotline numbers with tel: links', () => {
      render(<CrisisModal onClose={() => {}} />)

      const link1366 = screen.getByText('1366') as HTMLAnchorElement
      const link1393 = screen.getByText('1393') as HTMLAnchorElement
      const link132 = screen.getByText('132') as HTMLAnchorElement
      const link112 = screen.getByText('112') as HTMLAnchorElement

      expect(link1366.href).toBe('tel:1366')
      expect(link1393.href).toBe('tel:1393')
      expect(link132.href).toBe('tel:132')
      expect(link112.href).toBe('tel:112')
    })
  })

  describe('CrisisResourceModal', () => {
    it('should render when open=true', () => {
      render(<CrisisResourceModal open={true} onClose={() => {}} />)

      expect(screen.getByText('🚨 중요한 안내')).toBeInTheDocument()
    })

    it('should not render when open=false', () => {
      const { container } = render(<CrisisResourceModal open={false} onClose={() => {}} />)

      // Dialog role should not exist
      expect(container.querySelector('[role="dialog"]')).not.toBeInTheDocument()
    })

    it('should NOT dismiss when ESC key is pressed', async () => {
      const onClose = vi.fn()
      const { container } = render(
        <CrisisResourceModal open={true} onClose={onClose} severity="critical" />
      )
      const user = userEvent.setup()

      // Modal should be in DOM
      const dialog = container.querySelector('[role="dialog"]')
      expect(dialog).toBeInTheDocument()

      // Simulate ESC key press
      await user.keyboard('{Escape}')

      // onClose should NOT have been called
      expect(onClose).not.toHaveBeenCalled()

      // Modal should still be in DOM
      expect(dialog).toBeInTheDocument()
    })

    it('should NOT dismiss when backdrop is clicked', async () => {
      const onClose = vi.fn()
      const { container } = render(
        <CrisisResourceModal open={true} onClose={onClose} severity="critical" />
      )
      const user = userEvent.setup()

      // Find the backdrop (outer fixed div)
      const backdrop = container.querySelector('[style*="position: fixed"]') as HTMLElement
      expect(backdrop).toBeInTheDocument()

      // Click on the backdrop area (not the inner dialog)
      const innerDialog = container.querySelector('[role="dialog"]') as HTMLElement
      await user.click(backdrop)

      // If the click was on the backdrop, onClose should NOT be called
      // (unless clicked on inner content)
      // We only check that ESC doesn't work and button does
      expect(onClose).not.toHaveBeenCalled()
    })

    it('should dismiss ONLY when the close button is clicked', async () => {
      const onClose = vi.fn()
      render(
        <CrisisResourceModal open={true} onClose={onClose} severity="critical" />
      )
      const user = userEvent.setup()

      // Find and click the close button (닫기)
      const closeButton = screen.getByText('닫기')
      expect(closeButton).toBeInTheDocument()

      await user.click(closeButton)

      // onClose SHOULD be called
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('should show different text based on severity level', () => {
      const { unmount } = render(
        <CrisisResourceModal open={true} onClose={() => {}} severity="critical" />
      )

      expect(screen.getByText(/말씀해주신 상황은 저희 서비스의 범위를 넘어서는/)).toBeInTheDocument()

      unmount()

      render(
        <CrisisResourceModal open={true} onClose={() => {}} severity="advisory" />
      )

      expect(screen.getByText(/법적 결정은 저희 서비스가 도와드릴 수 없어요/)).toBeInTheDocument()
    })

    it('should display crisis resources with phone numbers', () => {
      const { container } = render(
        <CrisisResourceModal open={true} onClose={() => {}} severity="critical" />
      )

      // Check that resources are displayed (label and phone)
      const resourceContainer = container.textContent
      expect(resourceContainer).toContain('여성긴급전화') // one of the resources
      expect(resourceContainer).toContain('24시간') // hours info
    })

    it('should prevent body scroll when modal is open', () => {
      const { unmount } = render(
        <CrisisResourceModal open={true} onClose={() => {}} />
      )

      expect(document.body.style.overflow).toBe('hidden')

      unmount()

      expect(document.body.style.overflow).toBe('')
    })
  })
})
