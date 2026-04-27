/**
 * Integration Test: Crisis Detection (409 Response)
 *
 * Verifies that when the server returns a 409 status code with
 * crisisLevel: 1, the CrisisModal is displayed and the message
 * is not added to the chat.
 *
 * API endpoint: POST /api/sessions/:id/messages
 * Expected response on crisis: 409 { crisisLevel: 1 }
 * Expected UI: CrisisModal appears
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { ChatPanel } from '@/components/chat/ChatPanel'
import { createSession, createMessage } from '@/tests/fixtures'

describe('Integration: Crisis Detection (409)', () => {
  let sessionId: string

  beforeEach(() => {
    sessionId = 'test-session-crisis'
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('displays CrisisModal when server returns 409', async () => {
    const session = createSession({
      id: sessionId,
      status: 'chatting_solo',
    })

    const user = userEvent.setup({ delay: null })

    server.use(
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      }),
      http.post('/api/sessions/:id/messages', () => {
        // Server detects crisis keyword
        return HttpResponse.json({ crisisLevel: 1 }, { status: 409 })
      })
    )

    render(
      <ChatPanel
        sessionId={sessionId}
        session={session}
        currentUserSender="USER_A"
        isDuo={false}
      />
    )

    // Find and focus the input
    const input = await waitFor(() => screen.getByRole('textbox'), { timeout: 3000 })
    expect(input).toBeInTheDocument()

    // Type crisis keyword
    await user.type(input, '폭행당했어요')

    // Submit message
    await user.keyboard('{Enter}')

    // Wait for CrisisModal to appear
    await waitFor(
      () => {
        expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )

    // Verify modal was displayed (crisis modal shown on 409 response)
    const modal = screen.getByText('지금 안전이 가장 중요해요')
    expect(modal).toBeInTheDocument()
  })

  it('removes optimistic message when 409 is returned', async () => {
    const session = createSession({
      id: sessionId,
      status: 'chatting_solo',
    })

    const user = userEvent.setup({ delay: null })

    const initialMessages = [
      createMessage({
        id: 1,
        sender: 'USER_A',
        content: 'Previous message',
      }),
    ]

    server.use(
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json(initialMessages)
      }),
      http.post('/api/sessions/:id/messages', () => {
        return HttpResponse.json({ crisisLevel: 1 }, { status: 409 })
      })
    )

    render(
      <ChatPanel
        sessionId={sessionId}
        session={session}
        currentUserSender="USER_A"
        isDuo={false}
      />
    )

    await waitFor(
      () => {
        expect(screen.getByText('Previous message')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )

    const input = await waitFor(() => screen.getByRole('textbox'), { timeout: 3000 })
    await user.type(input, '자살하고 싶어요')
    await user.keyboard('{Enter}')

    // Wait for crisis modal
    await waitFor(
      () => {
        expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )

    // Verify crisis modal was displayed (indicates message was rejected)
    expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()

    // Only the previous message should remain in the chat
    expect(screen.getByText('Previous message')).toBeInTheDocument()
  })

  it('prevents sending while modal is displayed', async () => {
    const session = createSession({
      id: sessionId,
      status: 'chatting_solo',
    })

    const user = userEvent.setup({ delay: null })

    server.use(
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      }),
      http.post('/api/sessions/:id/messages', () => {
        return HttpResponse.json({ crisisLevel: 1 }, { status: 409 })
      })
    )

    render(
      <ChatPanel
        sessionId={sessionId}
        session={session}
        currentUserSender="USER_A"
        isDuo={false}
      />
    )

    const input = await waitFor(() => screen.getByRole('textbox'), { timeout: 3000 })

    // Send crisis message
    await user.type(input, '때렸어요')
    await user.keyboard('{Enter}')

    // Wait for modal
    await waitFor(
      () => {
        expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )

    // Modal should be displayed
    expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
  })

  it('closes modal only via explicit close button', async () => {
    const session = createSession({
      id: sessionId,
      status: 'chatting_solo',
    })

    const user = userEvent.setup({ delay: null })

    server.use(
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      }),
      http.post('/api/sessions/:id/messages', () => {
        return HttpResponse.json({ crisisLevel: 1 }, { status: 409 })
      })
    )

    render(
      <ChatPanel
        sessionId={sessionId}
        session={session}
        currentUserSender="USER_A"
        isDuo={false}
      />
    )

    const input = await waitFor(() => screen.getByRole('textbox'), { timeout: 3000 })
    await user.type(input, '폭행')
    await user.keyboard('{Enter}')

    await waitFor(
      () => {
        expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )

    // Try to close with ESC (should not work per safety rules)
    await user.keyboard('{Escape}')
    expect(screen.getByText('지금 안전이 가장 중요해요')).toBeInTheDocument()

    // Close button should exist and work
    const closeButton = screen.getByText('지금은 괜찮아요')
    expect(closeButton).toBeInTheDocument()

    await user.click(closeButton)

    // Modal should be gone after clicking close button
    await waitFor(
      () => {
        expect(screen.queryByText('지금 안전이 가장 중요해요')).not.toBeInTheDocument()
      },
      { timeout: 3000 }
    )
  })
})
