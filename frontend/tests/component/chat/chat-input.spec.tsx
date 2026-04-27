import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatInput } from '@/components/chat/ChatInput'
import { vi } from 'vitest'

// Mock the keyword guard utility
vi.mock('@/lib/utils/keywordGuard', () => ({
  checkKeywords: vi.fn(() => ({ level: null })),
}))

describe('ChatInput', () => {
  it('renders textarea with placeholder text', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    expect(textarea).toBeInTheDocument()
  })

  it('updates textarea value on user input', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요') as HTMLTextAreaElement
    await user.type(textarea, 'Hello, this is a test message')

    expect(textarea.value).toBe('Hello, this is a test message')
  })

  it('calls onSend with content when Enter key is pressed', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    await user.type(textarea, 'Test message')
    await user.keyboard('{Enter}')

    expect(onSend).toHaveBeenCalledWith('Test message')
  })

  it('calls onSend when send button is clicked', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    const sendButton = screen.getByRole('button', { name: '전송' })

    await user.type(textarea, 'Test message')
    await user.click(sendButton)

    expect(onSend).toHaveBeenCalledWith('Test message')
  })

  it('clears textarea after sending', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요') as HTMLTextAreaElement
    await user.type(textarea, 'Test message')
    await user.keyboard('{Enter}')

    expect(textarea.value).toBe('')
  })

  it('does not send empty or whitespace-only messages', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    await user.type(textarea, '   ')
    await user.keyboard('{Enter}')

    expect(onSend).not.toHaveBeenCalled()
  })

  it('disables textarea and button when disabled prop is true', async () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} disabled={true} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요') as HTMLTextAreaElement
    const sendButton = screen.getByRole('button', { name: '전송' }) as HTMLButtonElement

    expect(textarea.disabled).toBe(true)
    expect(sendButton.disabled).toBe(true)
  })

  it('disables send button when textarea is empty', () => {
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const sendButton = screen.getByRole('button', { name: '전송' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(true)
  })

  it('enables send button when textarea has text', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    const sendButton = screen.getByRole('button', { name: '전송' }) as HTMLButtonElement

    await user.type(textarea, 'Test')
    expect(sendButton.disabled).toBe(false)
  })

  it('allows Shift+Enter to create new lines without sending', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요') as HTMLTextAreaElement
    await user.type(textarea, 'First line')
    await user.keyboard('{Shift>}{Enter}{/Shift}')
    await user.type(textarea, 'Second line')

    expect(onSend).not.toHaveBeenCalled()
    expect(textarea.value).toContain('First line')
    expect(textarea.value).toContain('Second line')
  })

  it('trims whitespace from message before sending', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} />)

    const textarea = screen.getByPlaceholderText('편한 말로 적어주세요')
    await user.type(textarea, '  Test message  ')
    await user.keyboard('{Enter}')

    expect(onSend).toHaveBeenCalledWith('Test message')
  })

  it('renders info text about message handling', () => {
    render(<ChatInput onSend={vi.fn()} />)
    expect(screen.getByText(/이 글은 AI가 정리해서 전달돼요/)).toBeInTheDocument()
  })
})
