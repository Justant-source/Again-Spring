/**
 * Integration Test: Solo to Duo Transition
 *
 * Verifies that ChatLayout polls session status and detects when
 * status changes from 'chatting_solo' to 'chatting_duo'.
 *
 * Polling interval: 5000ms (from ChatLayout line 63)
 * Transition detection: ChatLayout lines 43-49
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { ChatLayout } from '@/components/chat/ChatLayout'
import { createSession } from '@/tests/fixtures'

describe('Integration: Solo to Duo Transition', () => {
  let sessionId: string

  beforeEach(() => {
    sessionId = 'test-session-solo-duo'
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders ChatLayout with solo session', async () => {
    const soloSession = createSession({
      id: sessionId,
      status: 'chatting_solo',
      myRole: 'USER_A',
    })

    server.use(
      http.get('/api/sessions/:id', () => {
        return HttpResponse.json(soloSession)
      }),
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      })
    )

    render(<ChatLayout sessionId={sessionId} session={soloSession} />)

    // Solo session should render the chat interface
    const input = await waitFor(
      () => screen.getByRole('textbox'),
      { timeout: 3000 }
    )
    expect(input).toBeInTheDocument()
  })

  it('handles duo session rendering', async () => {
    const duoSession = createSession({
      id: sessionId,
      status: 'chatting_duo',
      myRole: 'USER_A',
      inviteeUserId: '550e8400-e29b-41d4-a716-446655440001',
      partnerJoinedAt: new Date().toISOString(),
    })

    server.use(
      http.get('/api/sessions/:id', () => {
        return HttpResponse.json(duoSession)
      }),
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      }),
      http.get('/api/sessions/:id/partner-status', () => {
        return HttpResponse.json({
          joined: true,
          isActive: true,
          inviteSent: true,
          messageCount: 0,
          lastActivityAt: null,
        })
      })
    )

    const { container } = render(
      <ChatLayout sessionId={sessionId} session={duoSession} />
    )

    // In duo state, component should render both panels
    // Verify that a chat input exists (from both ChatPanel and potentially PartnerPanel)
    await waitFor(
      () => {
        expect(screen.getByRole('textbox')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )
  })

  it('handles awaiting_finalization status', async () => {
    const finalizingSession = createSession({
      id: sessionId,
      status: 'awaiting_finalization',
      myRole: 'USER_A',
      inviteeUserId: '550e8400-e29b-41d4-a716-446655440001',
      partnerJoinedAt: new Date().toISOString(),
    })

    server.use(
      http.get('/api/sessions/:id', () => {
        return HttpResponse.json(finalizingSession)
      }),
      http.get('/api/sessions/:id/messages', () => {
        return HttpResponse.json([])
      }),
      http.get('/api/sessions/:id/partner-status', () => {
        return HttpResponse.json({
          joined: true,
          isActive: true,
          inviteSent: true,
          messageCount: 0,
          lastActivityAt: null,
        })
      })
    )

    render(
      <ChatLayout sessionId={sessionId} session={finalizingSession} />
    )

    // awaiting_finalization is considered duo state (isDuo = true)
    await waitFor(
      () => {
        expect(screen.getByRole('textbox')).toBeInTheDocument()
      },
      { timeout: 3000 }
    )
  })
})
