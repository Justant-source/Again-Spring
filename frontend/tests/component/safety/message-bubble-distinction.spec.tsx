/**
 * Safety Regression Test: Message Bubble Visual Distinction
 *
 * Verifies that AI mediator messages and user messages are visually
 * distinct. Users must never mistake AI advice for another user's statement.
 *
 * Rule (CLAUDE.md 절대 불변 규칙 #3):
 * "AI 메시지와 사용자 메시지는 시각적으로 명확히 구분된다.
 *  MEDIATOR_TO_A/B sender를 사용자 메시지와 동일하게 표시 금지."
 */

import { render } from '@testing-library/react'
import { MessageBubble } from '@/components/chat/MessageBubble'
import { describe, it, expect } from 'vitest'
import type { Message } from '@/lib/types'

describe('Safety: Message Bubble Visual Distinction', () => {
  const baseMessage: Message = {
    id: 1,
    content: 'Test message content',
    charCount: 21,
    isFinalizeSuggestion: false,
    isPartnerJoinNotice: false,
    createdAt: new Date().toISOString(),
  }

  describe('User Message vs Mediator Message - Layout Distinction', () => {
    it('should use different flex justification for user vs mediator', () => {
      const userMessage: Message = { ...baseMessage, sender: 'USER_A' }
      const mediatorMessage: Message = { ...baseMessage, sender: 'MEDIATOR_TO_A' }

      const { container: userContainer } = render(
        <MessageBubble message={userMessage} isMine={true} />
      )
      const { container: mediatorContainer } = render(
        <MessageBubble message={mediatorMessage} isMine={false} />
      )

      const userWrapper = userContainer.firstChild as HTMLElement
      const mediatorWrapper = mediatorContainer.firstChild as HTMLElement

      // User message: right-aligned
      expect(userWrapper.style.justifyContent).toBe('flex-end')
      // Mediator message: left-aligned
      expect(mediatorWrapper.style.justifyContent).toBe('flex-start')
    })

    it('should use different background colors for user vs mediator', () => {
      const userMessage: Message = { ...baseMessage, sender: 'USER_A' }
      const mediatorMessage: Message = { ...baseMessage, sender: 'MEDIATOR_TO_A' }

      const { container: userContainer } = render(
        <MessageBubble message={userMessage} isMine={true} />
      )
      const { container: mediatorContainer } = render(
        <MessageBubble message={mediatorMessage} isMine={false} />
      )

      // Extract background styles
      const userWrapper = userContainer.firstChild as HTMLElement
      const mediatorWrapper = mediatorContainer.firstChild as HTMLElement

      const userBubbleEl = Array.from(userWrapper.children).find(
        el => el.textContent === baseMessage.content
      ) as HTMLElement | undefined
      const mediatorBubbleEl = Array.from(mediatorWrapper.children).find(
        el => el.textContent === baseMessage.content
      ) as HTMLElement | undefined

      const userBgStyle = userBubbleEl?.getAttribute('style')
      const mediatorBgStyle = mediatorBubbleEl?.getAttribute('style')

      // Verify different background color variables
      expect(userBgStyle).toContain('var(--P-a)')
      expect(mediatorBgStyle).toContain('var(--P-card)')
      // They should NOT be the same
      expect(userBgStyle).not.toBe(mediatorBgStyle)
    })

    it('should have proper bubble styling for both message types', () => {
      const userMessage: Message = { ...baseMessage, sender: 'USER_A' }

      const { container } = render(
        <MessageBubble message={userMessage} isMine={true} />
      )

      const wrapper = container.firstChild as HTMLElement
      const bubbleEl = Array.from(wrapper.children).find(
        el => el.textContent === baseMessage.content
      ) as HTMLElement | undefined

      const style = bubbleEl?.getAttribute('style')

      // Verify core bubble styling properties exist
      expect(style).toContain('max-width') // CSS may be kebab-cased
      expect(style).toContain('padding')
      expect(style).toContain('border-radius')
      expect(style).toContain('pre-wrap') // whitespace handling
      expect(style).toContain('break-word') // word wrapping
    })
  })

  describe('USER_B vs MEDIATOR_TO_B Distinction', () => {
    it('should handle USER_B and MEDIATOR_TO_B with same distinction', () => {
      const userBMessage: Message = { ...baseMessage, sender: 'USER_B' }
      const mediatorBMessage: Message = { ...baseMessage, sender: 'MEDIATOR_TO_B' }

      const { container: userBContainer } = render(
        <MessageBubble message={userBMessage} isMine={true} />
      )
      const { container: mediatorBContainer } = render(
        <MessageBubble message={mediatorBMessage} isMine={false} />
      )

      const userBWrapper = userBContainer.firstChild as HTMLElement
      const mediatorBWrapper = mediatorBContainer.firstChild as HTMLElement

      // Same distinction as A
      expect(userBWrapper.style.justifyContent).toBe('flex-end')
      expect(mediatorBWrapper.style.justifyContent).toBe('flex-start')
    })
  })

  describe('Timestamp Positioning', () => {
    it('should render timestamp for user message', () => {
      const message: Message = { ...baseMessage, sender: 'USER_A' }
      const { container } = render(
        <MessageBubble message={message} isMine={true} />
      )

      const wrapper = container.firstChild as HTMLElement
      // For isMine=true, first child is timestamp
      const firstChild = wrapper.children[0] as HTMLElement

      expect(firstChild.textContent).toMatch(/\d{2}:\d{2}/)
    })

    it('should render timestamp for mediator message', () => {
      const message: Message = { ...baseMessage, sender: 'MEDIATOR_TO_A' }
      const { container } = render(
        <MessageBubble message={message} isMine={false} />
      )

      const wrapper = container.firstChild as HTMLElement
      // For isMine=false, last child is timestamp
      const lastChild = wrapper.children[wrapper.children.length - 1] as HTMLElement

      expect(lastChild.textContent).toMatch(/\d{2}:\d{2}/)
    })
  })

  describe('Content Integrity', () => {
    it('should preserve multiline content with pre-wrap', () => {
      const message: Message = {
        ...baseMessage,
        sender: 'USER_A',
        content: 'Line 1\nLine 2\nLine 3',
      }

      const { container } = render(
        <MessageBubble message={message} isMine={true} />
      )

      const wrapper = container.firstChild as HTMLElement
      const content = wrapper.textContent

      expect(content).toContain('Line 1')
      expect(content).toContain('Line 2')
      expect(content).toContain('Line 3')
    })

    it('should handle very long content without visual distortion', () => {
      const longContent = 'a'.repeat(100)
      const message: Message = {
        ...baseMessage,
        sender: 'USER_A',
        content: longContent,
      }

      const { container } = render(
        <MessageBubble message={message} isMine={true} />
      )

      const wrapper = container.firstChild as HTMLElement
      const bubbleEl = Array.from(wrapper.children).find(
        el => el.textContent?.includes(longContent)
      ) as HTMLElement | undefined

      expect(bubbleEl).toBeDefined()

      // Should have max-width to prevent overflow
      const style = bubbleEl?.getAttribute('style')
      expect(style).toContain('max-width')
    })
  })

  describe('Safety: Visual Distinction Cannot be Overridden', () => {
    it('user message (isMine=true) should ALWAYS have flex-end justification', () => {
      const message: Message = { ...baseMessage, sender: 'USER_A' }

      const { container } = render(
        <MessageBubble message={message} isMine={true} />
      )

      const wrapper = container.firstChild as HTMLElement

      // This is a critical safety check - must not be changed
      expect(wrapper.style.justifyContent).toBe('flex-end')
    })

    it('mediator message (isMine=false) should ALWAYS have flex-start justification', () => {
      const message: Message = { ...baseMessage, sender: 'MEDIATOR_TO_A' }

      const { container } = render(
        <MessageBubble message={message} isMine={false} />
      )

      const wrapper = container.firstChild as HTMLElement

      // This is a critical safety check - must not be changed
      expect(wrapper.style.justifyContent).toBe('flex-start')
    })

    it('user and mediator bubbles should use different CSS color variables', () => {
      const messages: Array<{ message: Message; isMine: boolean }> = [
        { message: { ...baseMessage, sender: 'USER_A' }, isMine: true },
        { message: { ...baseMessage, sender: 'USER_B' }, isMine: true },
        { message: { ...baseMessage, sender: 'MEDIATOR_TO_A' }, isMine: false },
        { message: { ...baseMessage, sender: 'MEDIATOR_TO_B' }, isMine: false },
      ]

      const userStyles: string[] = []
      const mediatorStyles: string[] = []

      messages.forEach(({ message, isMine }) => {
        const { container } = render(
          <MessageBubble message={message} isMine={isMine} />
        )

        const wrapper = container.firstChild as HTMLElement
        const bubbleEl = Array.from(wrapper.children).find(
          el => el.textContent === baseMessage.content
        ) as HTMLElement | undefined

        const style = bubbleEl?.getAttribute('style') || ''

        if (isMine) {
          userStyles.push(style)
        } else {
          mediatorStyles.push(style)
        }
      })

      // All user messages should have var(--P-a)
      userStyles.forEach(style => {
        expect(style).toContain('var(--P-a)')
      })

      // All mediator messages should have var(--P-card)
      mediatorStyles.forEach(style => {
        expect(style).toContain('var(--P-card)')
      })

      // They should be different
      expect(userStyles[0]).not.toBe(mediatorStyles[0])
    })
  })
})
