import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InviteModal } from '@/components/chat/InviteModal'
import { vi } from 'vitest'
import { server } from '@/mocks/server'
import { http, HttpResponse } from 'msw'

describe('InviteModal', () => {
  beforeEach(() => {
    // Mock the invite token API (component tries GET first, then POST)
    server.use(
      http.get('/api/sessions/:sessionId/invite', () => {
        return HttpResponse.json({
          inviteToken: 'test_invite_token_123',
        })
      }),
      http.post('/api/sessions/:sessionId/invite', () => {
        return HttpResponse.json({
          inviteToken: 'test_invite_token_123',
        })
      })
    )
  })

  it('renders modal with title', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    expect(screen.getByText(/상대도 함께 정리하면/)).toBeInTheDocument()
  })

  it('displays explanatory text about privacy', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    expect(
      screen.getByText(/상대분이 합류해도 두 분의 대화는 서로 보이지 않아요/)
    ).toBeInTheDocument()
  })

  it('renders three tone selection buttons', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    expect(screen.getByRole('button', { name: '부드럽게' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가볍게' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '진지하게' })).toBeInTheDocument()
  })

  it('selects first tone by default', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const softButton = screen.getByRole('button', { name: '부드럽게' }) as HTMLButtonElement
    // First button should be highlighted (has darker background)
    expect(softButton.style.background).toContain('var(--P-ink)')
  })

  it('changes tone when button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const lightButton = screen.getByRole('button', { name: '가볍게' })
    await user.click(lightButton)

    // Verify textarea content changed (light tone message should be selected)
    const textarea = screen.getByDisplayValue(
      /요즘 마음 정리하는 도구 써보고 있어/
    ) as HTMLTextAreaElement
    expect(textarea).toBeInTheDocument()
  })

  it('allows editing the invitation message', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement
    const originalText = textarea.value

    // Clear and type new text
    await user.clear(textarea)
    await user.type(textarea, 'Custom invitation message')

    expect(textarea.value).toBe('Custom invitation message')
  })

  it('displays share URL after token is loaded', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    await waitFor(() => {
      expect(screen.getByText(/session\/join\/test_invite_token_123/)).toBeInTheDocument()
    })
  })

  it('shows loading state while fetching invite token', () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    // Initially should show "링크 생성 중..."
    expect(screen.getByText(/링크 생성 중/)).toBeInTheDocument()
  })

  it('disables share button while loading token', () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const shareButton = screen.getByRole('button', {
      name: /카톡으로 공유하기/,
    }) as HTMLButtonElement
    expect(shareButton.disabled).toBe(true)
  })

  it('enables share button after token loads', async () => {
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const shareButton = screen.getByRole('button', {
      name: /카톡으로 공유하기/,
    }) as HTMLButtonElement

    await waitFor(() => {
      expect(shareButton.disabled).toBe(false)
    })
  })

  it('closes modal when close button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const closeButton = screen.getByRole('button', { name: '나중에 할게요' })
    await user.click(closeButton)

    expect(onClose).toHaveBeenCalled()
  })

  it('closes modal when backdrop is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const { container } = render(
      <InviteModal sessionId="test-session" onClose={onClose} />
    )

    // Find the backdrop (the fixed positioned div)
    const backdrop = container.firstChild as HTMLElement
    await user.click(backdrop)

    expect(onClose).toHaveBeenCalled()
  })

  it('does not close modal when content is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    const content = screen.getByText(/상대도 함께 정리하면/)
    await user.click(content)

    expect(onClose).not.toHaveBeenCalled()
  })

  it('copies full text including URL to clipboard', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    // Make clipboard API available (JSDOM isSecureContext may be undefined/false)
    const clipboardWriteText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('isSecureContext', true)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: clipboardWriteText },
      writable: true,
      configurable: true,
    })

    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    await waitFor(() => {
      expect(screen.getByText(/session\/join/)).toBeInTheDocument()
    })

    const shareButton = screen.getByRole('button', {
      name: /카톡으로 공유하기/,
    })
    await user.click(shareButton)

    // navigator.share not available → clipboard fallback
    await waitFor(() => {
      expect(clipboardWriteText).toHaveBeenCalled()
    })

    vi.unstubAllGlobals()
  })

  it('shows copied feedback after sharing', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    // Make clipboard API available
    vi.stubGlobal('isSecureContext', true)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    })

    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    await waitFor(() => {
      expect(screen.getByText(/session\/join/)).toBeInTheDocument()
    })

    const shareButton = screen.getByRole('button', {
      name: /카톡으로 공유하기/,
    })
    await user.click(shareButton)

    // Button text changes to "메시지+링크 복사됐어요" after successful copy
    await waitFor(() => {
      expect(screen.getByText(/복사됐어요/)).toBeInTheDocument()
    })

    vi.unstubAllGlobals()
  })

  it('displays all three tone messages correctly', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    // Check soft tone message
    expect(screen.getByDisplayValue(/우리 얘기 좀 정리해보고 싶어서/)).toBeInTheDocument()

    // Click light tone
    await user.click(screen.getByRole('button', { name: '가볍게' }))
    expect(screen.getByDisplayValue(/요즘 마음 정리하는 도구 써보고 있어/)).toBeInTheDocument()

    // Click serious tone
    await user.click(screen.getByRole('button', { name: '진지하게' }))
    expect(
      screen.getByDisplayValue(/우리 사이에 쌓인 마음을/)
    ).toBeInTheDocument()
  })

  it('handles API error gracefully', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/invite', () => {
        return HttpResponse.json({ error: 'Failed' }, { status: 500 })
      }),
      http.post('/api/sessions/:sessionId/invite', () => {
        return HttpResponse.json({ error: 'Failed' }, { status: 500 })
      })
    )

    const onClose = vi.fn()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<InviteModal sessionId="test-session" onClose={onClose} />)

    await waitFor(() => {
      expect(consoleError).toHaveBeenCalled()
    })

    consoleError.mockRestore()
  })
})
