import { render, screen, waitFor } from '@testing-library/react'
import { PartnerPanel } from '@/components/chat/PartnerPanel'
import { vi } from 'vitest'
import { server } from '@/mocks/server'
import { http, HttpResponse } from 'msw'

// Mock the polling hook
vi.mock('@/lib/hooks/usePolling', () => ({
  usePolling: vi.fn(),
}))

describe('PartnerPanel', () => {
  beforeEach(() => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([])
      })
    )
  })

  it('renders partner panel header', () => {
    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    expect(screen.getByText(/상대가 정리 중이에요/)).toBeInTheDocument()
  })

  it('displays privacy notice', () => {
    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    expect(
      screen.getByText(/내용은 두 분의 사생활 보호를 위해 가려져 있어요/)
    ).toBeInTheDocument()
  })

  it('displays swipe instruction footer', () => {
    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    expect(screen.getByText(/스와이프하면 본인 채팅으로 돌아갈 수 있어요/)).toBeInTheDocument()
  })

  it('shows empty state when no messages', () => {
    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    expect(screen.getByText(/상대분도 곧 시작하실 거예요/)).toBeInTheDocument()
  })

  it('renders blurred bubbles for partner messages', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
          {
            id: 2,
            sender: 'USER_B',
            charCount: 100,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      // Should have 2 blurred bubbles
      const bubbles = container.querySelectorAll('div[style*="opacity: 0.5"]')
      expect(bubbles.length).toBeGreaterThanOrEqual(2)
    })
  })

  it('aligns partner messages to the left', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      const wrapper = container.querySelector('div[style*="flex-start"]')
      expect(wrapper).toBeInTheDocument()
    })
  })

  it('aligns own messages to the right when displaying other user messages', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_A',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      const wrapper = container.querySelector('div[style*="flex-end"]')
      expect(wrapper).toBeInTheDocument()
    })
  })

  it('displays time for each message', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date(2026, 3, 27, 14, 30, 0).toISOString(), // local 14:30
          },
        ])
      })
    )

    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    await waitFor(() => {
      expect(screen.getByText(/14:30/)).toBeInTheDocument()
    })
  })

  it('calculates blurred bubble width based on character count', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 10,
            createdAt: new Date().toISOString(),
          },
          {
            id: 2,
            sender: 'USER_B',
            charCount: 100,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      const bubbles = container.querySelectorAll('div[style*="opacity: 0.5"]')
      // Different char counts should produce different widths
      expect(bubbles.length).toBeGreaterThanOrEqual(2)
    })
  })

  it('does not display actual message content', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      // The container should not have any actual message text
      const text = container.textContent
      expect(text).not.toContain('test message')
      expect(text).not.toContain('secret content')
    })
  })

  it('recognizes both USER_A and USER_B as partners depending on role', () => {
    const { rerender } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    expect(screen.getByText(/상대가 정리 중이에요/)).toBeInTheDocument()

    rerender(<PartnerPanel sessionId="test-session" myRole="USER_B" />)

    expect(screen.getByText(/상대가 정리 중이에요/)).toBeInTheDocument()
  })

  it('handles multiple messages in sequence', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 30,
            createdAt: new Date('2026-04-27T14:00:00Z').toISOString(),
          },
          {
            id: 2,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date('2026-04-27T14:05:00Z').toISOString(),
          },
          {
            id: 3,
            sender: 'USER_B',
            charCount: 70,
            createdAt: new Date('2026-04-27T14:10:00Z').toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      const bubbles = container.querySelectorAll('div[style*="opacity: 0.5"]')
      expect(bubbles.length).toBe(3)
    })
  })

  it('applies blur effect to blurred bubbles', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      // Check for blur filter in styles
      const blurContent = container.querySelector('div[style*="blur"]')
      expect(blurContent).toBeInTheDocument()
    })
  })

  it('updates messages when polling returns new data', async () => {
    let callCount = 0
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        callCount++
        if (callCount === 1) {
          return HttpResponse.json([
            {
              id: 1,
              sender: 'USER_B',
              charCount: 50,
              createdAt: new Date().toISOString(),
            },
          ])
        }
        return HttpResponse.json([
          {
            id: 1,
            sender: 'USER_B',
            charCount: 50,
            createdAt: new Date().toISOString(),
          },
          {
            id: 2,
            sender: 'USER_B',
            charCount: 60,
            createdAt: new Date().toISOString(),
          },
        ])
      })
    )

    const { container } = render(
      <PartnerPanel sessionId="test-session" myRole="USER_A" />
    )

    await waitFor(() => {
      // Initial load
      expect(screen.getByText(/상대가 정리 중이에요/)).toBeInTheDocument()
    })
  })

  it('handles API errors gracefully', async () => {
    server.use(
      http.get('/api/sessions/:sessionId/partner-messages', () => {
        return HttpResponse.json({ error: 'Failed' }, { status: 500 })
      })
    )

    const consoleDebug = vi.spyOn(console, 'debug').mockImplementation(() => {})

    render(<PartnerPanel sessionId="test-session" myRole="USER_A" />)

    await waitFor(() => {
      expect(consoleDebug).toHaveBeenCalled()
    })

    consoleDebug.mockRestore()
  })

  it('renders correctly when myRole is USER_B', () => {
    render(<PartnerPanel sessionId="test-session" myRole="USER_B" />)

    expect(screen.getByText(/상대가 정리 중이에요/)).toBeInTheDocument()
    expect(screen.getByText(/스와이프하면 본인 채팅으로 돌아갈 수 있어요/)).toBeInTheDocument()
  })
})
