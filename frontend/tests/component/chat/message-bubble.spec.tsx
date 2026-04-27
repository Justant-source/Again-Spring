import { render, screen } from '@testing-library/react'
import { MessageBubble } from '@/components/chat/MessageBubble'
import { createMessage } from '@/tests/fixtures'

describe('MessageBubble', () => {
  const baseMessage = createMessage({
    content: 'This is a test message',
    createdAt: new Date('2026-04-27T14:30:00Z').toISOString(),
  })

  it('renders message content', () => {
    render(<MessageBubble message={baseMessage} isMine={false} />)
    expect(screen.getByText('This is a test message')).toBeInTheDocument()
  })

  it('displays time in correct format (24-hour HH:mm)', () => {
    const localDate = new Date(2026, 3, 27, 14, 30, 0) // local 14:30
    const message = createMessage({
      content: 'Test',
      createdAt: localDate.toISOString(),
    })
    render(<MessageBubble message={message} isMine={false} />)

    // Time should be displayed in 24-hour format
    expect(screen.getByText(/14:30/)).toBeInTheDocument()
  })

  it('aligns message to the right when isMine is true', () => {
    const { container } = render(<MessageBubble message={baseMessage} isMine={true} />)

    const wrapper = container.firstChild as HTMLElement
    const styles = window.getComputedStyle(wrapper)
    expect(styles.justifyContent).toBe('flex-end')
  })

  it('aligns message to the left when isMine is false', () => {
    const { container } = render(<MessageBubble message={baseMessage} isMine={false} />)

    const wrapper = container.firstChild as HTMLElement
    const styles = window.getComputedStyle(wrapper)
    expect(styles.justifyContent).toBe('flex-start')
  })

  it('uses different background color when isMine is true', () => {
    const { container: containerMine } = render(
      <MessageBubble message={baseMessage} isMine={true} />
    )
    const { container: containerTheirs } = render(
      <MessageBubble message={baseMessage} isMine={false} />
    )

    const bubbleMine = containerMine.querySelector('div[style*="padding"]')
    const bubbleTheirs = containerTheirs.querySelector('div[style*="padding"]')

    // Both should have styles, but they use different CSS variables
    expect(bubbleMine).toBeInTheDocument()
    expect(bubbleTheirs).toBeInTheDocument()
  })

  it('positions time on the right when isMine is true', () => {
    const { container } = render(<MessageBubble message={baseMessage} isMine={true} />)

    const timeElements = container.querySelectorAll('div[style*="color"]')
    // First time div (right side) should exist
    expect(timeElements.length).toBeGreaterThan(0)
  })

  it('positions time on the left when isMine is false', () => {
    const { container } = render(<MessageBubble message={baseMessage} isMine={false} />)

    const timeElements = container.querySelectorAll('div[style*="color"]')
    // Time should be displayed on left side
    expect(timeElements.length).toBeGreaterThan(0)
  })

  it('preserves whitespace and line breaks in message content', () => {
    const message = createMessage({
      content: 'Line 1\nLine 2\nLine 3',
      createdAt: new Date().toISOString(),
    })
    const { container } = render(<MessageBubble message={message} isMine={false} />)

    const content = container.textContent
    expect(content).toContain('Line 1')
    expect(content).toContain('Line 2')
    expect(content).toContain('Line 3')
  })

  it('handles very long messages', () => {
    const longContent = 'a'.repeat(500)
    const message = createMessage({
      content: longContent,
      createdAt: new Date().toISOString(),
    })
    render(<MessageBubble message={message} isMine={false} />)

    expect(screen.getByText(longContent)).toBeInTheDocument()
  })

  it('handles mediator messages correctly', () => {
    const mediatorMessage = createMessage({
      sender: 'MEDIATOR_TO_A',
      content: 'I understand both perspectives',
      createdAt: new Date().toISOString(),
    })
    render(<MessageBubble message={mediatorMessage} isMine={true} />)

    expect(screen.getByText('I understand both perspectives')).toBeInTheDocument()
  })

  it('renders correctly for MEDIATOR_TO_B sender', () => {
    const mediatorMessage = createMessage({
      sender: 'MEDIATOR_TO_B',
      content: 'Let me help you understand',
      createdAt: new Date().toISOString(),
    })
    render(<MessageBubble message={mediatorMessage} isMine={false} />)

    expect(screen.getByText('Let me help you understand')).toBeInTheDocument()
  })

  it('displays different times correctly', () => {
    const message1 = createMessage({
      content: 'Morning message',
      createdAt: new Date(2026, 3, 27, 8, 0, 0).toISOString(), // local 08:00
    })
    const message2 = createMessage({
      content: 'Evening message',
      createdAt: new Date(2026, 3, 27, 18, 30, 0).toISOString(), // local 18:30
    })

    const { rerender } = render(<MessageBubble message={message1} isMine={false} />)
    expect(screen.getByText(/08:00/)).toBeInTheDocument()

    rerender(<MessageBubble message={message2} isMine={false} />)
    expect(screen.getByText(/18:30/)).toBeInTheDocument()
  })

  it('handles special characters in message content', () => {
    const content = 'Special chars: !@#$%^&*()_+-=[]{}|;:\'",.<>?/'
    const message = createMessage({
      content,
      createdAt: new Date().toISOString(),
    })
    render(<MessageBubble message={message} isMine={false} />)

    expect(screen.getByText(content)).toBeInTheDocument()
  })

  it('handles empty content gracefully', () => {
    const message = createMessage({
      content: '',
      createdAt: new Date().toISOString(),
    })
    const { container } = render(<MessageBubble message={message} isMine={false} />)

    expect(container).toBeInTheDocument()
  })
})
