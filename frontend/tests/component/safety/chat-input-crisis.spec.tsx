/**
 * Safety Regression Test: Chat Input Crisis Keyword Detection
 *
 * Verifies that ChatInput correctly detects crisis (Level 1) keywords
 * and blocks sending, triggering the onCrisis callback instead.
 *
 * Rule (CLAUDE.md 절대 불변 규칙 #2):
 * "위기 감지는 FE(ChatInput)와 BE(KeywordGuard) 이중 구현을 유지한다.
 *  클라이언트 우회 가능성을 가정. 어느 한쪽 제거 금지."
 */

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatInput } from '@/components/chat/ChatInput'
import { describe, it, expect, vi } from 'vitest'

describe('Safety: Chat Input Crisis Keyword Detection', () => {
  describe('Domestic Violence Keywords (Level 1)', () => {
    it('should block sending when "때렸어" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '남편이 때렸어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "폭력" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '폭력이 없었어야 했어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "폭행" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '친구의 폭행을 목격했어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "때려" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '자꾸 때려요')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "폭행" context is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '폭행을 당했어요')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })
  })

  describe('Sexual Violence Keywords (Level 1)', () => {
    it('should block sending when "강간" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '강간당했어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "성폭행" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '성폭행을 당했어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "성폭력" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '성폭력이 있었어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })
  })

  describe('Self-harm Keywords (Level 1)', () => {
    it('should block sending when "죽고 싶" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '죽고 싶어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "자살" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '자살을 생각하고 있어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "자해" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '자해하고 싶어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })
  })

  describe('Child Abuse Keywords (Level 1)', () => {
    it('should block sending when "아이를 때" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '아이를 때렸어')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })

    it('should block sending when "아동학대" is entered', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '아동학대 걱정이 돼')
      await user.keyboard('{Enter}')

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).toHaveBeenCalledTimes(1)
    })
  })

  describe('Normal Messages (Non-crisis)', () => {
    it('should allow sending normal messages', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '우리 관계를 개선하고 싶어요')
      await user.keyboard('{Enter}')

      expect(onSend).toHaveBeenCalledTimes(1)
      expect(onSend).toHaveBeenCalledWith('우리 관계를 개선하고 싶어요')
      expect(onCrisis).not.toHaveBeenCalled()
    })

    it('should allow sending message with button click', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '대화가 필요해')

      const sendButton = screen.getByText('전송')
      await user.click(sendButton)

      expect(onSend).toHaveBeenCalledTimes(1)
      expect(onSend).toHaveBeenCalledWith('대화가 필요해')
      expect(onCrisis).not.toHaveBeenCalled()
    })
  })

  describe('Edge Cases', () => {
    it('should not send when text is empty', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const sendButton = screen.getByText('전송')
      expect(sendButton).toBeDisabled()

      await user.click(sendButton)

      expect(onSend).not.toHaveBeenCalled()
      expect(onCrisis).not.toHaveBeenCalled()
    })

    it('should trim whitespace before checking keywords', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '  자살  ')
      await user.keyboard('{Enter}')

      expect(onCrisis).toHaveBeenCalledTimes(1)
      expect(onSend).not.toHaveBeenCalled()
    })

    it('should detect keywords even without spaces', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      // Keyword without spaces: 죽고싶
      await user.type(textarea, '죽고싶어')
      await user.keyboard('{Enter}')

      expect(onCrisis).toHaveBeenCalledTimes(1)
      expect(onSend).not.toHaveBeenCalled()
    })

    it('should be disabled when disabled prop is true', async () => {
      const onSend = vi.fn()
      const onCrisis = vi.fn()
      render(<ChatInput onSend={onSend} disabled={true} onCrisis={onCrisis} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
      await user.type(textarea, '대화해요')

      const sendButton = screen.getByText('전송')
      await user.click(sendButton)

      expect(onSend).not.toHaveBeenCalled()
    })

    it('should clear text after successful send', async () => {
      const onSend = vi.fn()
      render(<ChatInput onSend={onSend} />)
      const user = userEvent.setup()

      const textarea = screen.getByPlaceholderText('편한 말로 적어주세요') as HTMLTextAreaElement
      await user.type(textarea, '안녕하세요')
      await user.keyboard('{Enter}')

      expect(textarea.value).toBe('')
    })
  })
})
