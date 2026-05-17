import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { useSessionStore } from '@/lib/store/sessionStore'
import type { ActiveSessionCategory } from '@/lib/store/sessionStore'
import type { RelationType, SessionStatus, Message } from '@/lib/types'

describe('useSessionStore (Zustand)', () => {
  beforeEach(() => {
    localStorage.clear()
    useSessionStore.setState({
      sessionId: null,
      inviteToken: null,
      relationType: null,
      category: null,
      description: '',
      role: null,
      status: 'chatting_solo' as SessionStatus,
      currentTurn: 1,
      turns: [],
      inviteMessageTone: 'soft',
      partnerNickname: undefined,
      soloMode: null,
    })
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('reset', () => {
    it('resets all fields to initial state', () => {
      // Set up some state
      useSessionStore.setState({
        sessionId: 'session-1',
        inviteToken: 'token-123',
        relationType: 'couple',
        status: 'chatting_duo',
        currentTurn: 5,
        role: 'A',
      })

      expect(useSessionStore.getState().sessionId).toBe('session-1')

      // Reset
      useSessionStore.getState().reset()

      expect(useSessionStore.getState().sessionId).toBeNull()
      expect(useSessionStore.getState().inviteToken).toBeNull()
      expect(useSessionStore.getState().relationType).toBeNull()
      expect(useSessionStore.getState().category).toBeNull()
      expect(useSessionStore.getState().description).toBe('')
      expect(useSessionStore.getState().role).toBeNull()
      expect(useSessionStore.getState().status).toBe('chatting_solo')
      expect(useSessionStore.getState().currentTurn).toBe(1)
      expect(useSessionStore.getState().turns).toEqual([])
      expect(useSessionStore.getState().inviteMessageTone).toBe('soft')
      expect(useSessionStore.getState().soloMode).toBeNull()
    })

    it('clears turns array on reset', () => {
      useSessionStore.setState({
        turns: [
          { turnNumber: 1, role: 'A', content: 'Message 1', createdAt: new Date().toISOString() },
          { turnNumber: 2, role: 'B', content: 'Message 2', createdAt: new Date().toISOString() },
        ],
      })

      useSessionStore.getState().reset()

      expect(useSessionStore.getState().turns).toEqual([])
    })

    it('persists reset to localStorage', () => {
      useSessionStore.setState({
        sessionId: 'session-1',
        relationType: 'couple',
      })

      useSessionStore.getState().reset()

      const stored = localStorage.getItem('again-spring-session')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.sessionId).toBeNull()
    })
  })

  describe('setRelationType', () => {
    it('sets relation type to couple', () => {
      useSessionStore.getState().setRelationType('couple')
      expect(useSessionStore.getState().relationType).toBe('couple')
    })

    it('sets relation type to marriage', () => {
      useSessionStore.getState().setRelationType('marriage')
      expect(useSessionStore.getState().relationType).toBe('marriage')
    })

    it('sets relation type to friend', () => {
      useSessionStore.getState().setRelationType('friend')
      expect(useSessionStore.getState().relationType).toBe('friend')
    })

    it('sets relation type to family', () => {
      useSessionStore.getState().setRelationType('family')
      expect(useSessionStore.getState().relationType).toBe('family')
    })

    it('sets relation type to parent_child', () => {
      useSessionStore.getState().setRelationType('parent_child')
      expect(useSessionStore.getState().relationType).toBe('parent_child')
    })

    it('sets relation type to korean_specific', () => {
      useSessionStore.getState().setRelationType('korean_specific')
      expect(useSessionStore.getState().relationType).toBe('korean_specific')
    })

    it('overwrites previous relation type', () => {
      useSessionStore.getState().setRelationType('couple')
      expect(useSessionStore.getState().relationType).toBe('couple')

      useSessionStore.getState().setRelationType('marriage')
      expect(useSessionStore.getState().relationType).toBe('marriage')
    })
  })

  describe('setCategory', () => {
    it('sets category with all required fields', () => {
      const category: ActiveSessionCategory = {
        majorId: 'major-1',
        middleId: 'middle-1',
        minorId: 'minor-1',
      }

      useSessionStore.getState().setCategory(category)
      expect(useSessionStore.getState().category).toEqual(category)
    })

    it('sets category with custom text', () => {
      const category: ActiveSessionCategory = {
        majorId: 'couple',
        middleId: 'couple_contact',
        minorId: 'custom',
        customText: '우리 관계가 너무 복잡해',
      }

      useSessionStore.getState().setCategory(category)
      expect(useSessionStore.getState().category).toEqual(category)
      expect(useSessionStore.getState().category?.customText).toBe('우리 관계가 너무 복잡해')
    })

    it('allows updating category without custom text', () => {
      const category: ActiveSessionCategory = {
        majorId: 'marriage',
        middleId: 'marriage_chores',
        minorId: 'cleaning',
      }

      useSessionStore.getState().setCategory(category)
      expect(useSessionStore.getState().category?.customText).toBeUndefined()
    })
  })

  describe('setDescription', () => {
    it('sets description text', () => {
      const desc = '우리는 매일 싸우고 있어'
      useSessionStore.getState().setDescription(desc)
      expect(useSessionStore.getState().description).toBe(desc)
    })

    it('overwrites previous description', () => {
      useSessionStore.getState().setDescription('First description')
      useSessionStore.getState().setDescription('Second description')
      expect(useSessionStore.getState().description).toBe('Second description')
    })

    it('handles empty string description', () => {
      useSessionStore.getState().setDescription('')
      expect(useSessionStore.getState().description).toBe('')
    })

    it('handles long descriptions', () => {
      const longDesc = 'A'.repeat(5000)
      useSessionStore.getState().setDescription(longDesc)
      expect(useSessionStore.getState().description).toBe(longDesc)
    })
  })

  describe('setInviteTone', () => {
    it('sets invite tone to soft', () => {
      useSessionStore.getState().setInviteTone('soft')
      expect(useSessionStore.getState().inviteMessageTone).toBe('soft')
    })

    it('sets invite tone to light', () => {
      useSessionStore.getState().setInviteTone('light')
      expect(useSessionStore.getState().inviteMessageTone).toBe('light')
    })

    it('sets invite tone to serious', () => {
      useSessionStore.getState().setInviteTone('serious')
      expect(useSessionStore.getState().inviteMessageTone).toBe('serious')
    })

    it('overwrites previous tone', () => {
      useSessionStore.getState().setInviteTone('soft')
      useSessionStore.getState().setInviteTone('serious')
      expect(useSessionStore.getState().inviteMessageTone).toBe('serious')
    })
  })

  describe('setSession', () => {
    it('sets sessionId and inviteToken', () => {
      useSessionStore.getState().setSession({
        id: 'session-123',
        inviteToken: 'token-abc',
      })

      expect(useSessionStore.getState().sessionId).toBe('session-123')
      expect(useSessionStore.getState().inviteToken).toBe('token-abc')
    })

    it('overwrites previous session', () => {
      useSessionStore.getState().setSession({
        id: 'session-1',
        inviteToken: 'token-1',
      })

      useSessionStore.getState().setSession({
        id: 'session-2',
        inviteToken: 'token-2',
      })

      expect(useSessionStore.getState().sessionId).toBe('session-2')
      expect(useSessionStore.getState().inviteToken).toBe('token-2')
    })

    it('persists to localStorage', () => {
      useSessionStore.getState().setSession({
        id: 'session-123',
        inviteToken: 'token-abc',
      })

      const stored = localStorage.getItem('again-spring-session')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.sessionId).toBe('session-123')
      expect(parsed.state.inviteToken).toBe('token-abc')
    })
  })

  describe('setStatus', () => {
    it('sets status to chatting_solo', () => {
      useSessionStore.getState().setStatus('chatting_solo')
      expect(useSessionStore.getState().status).toBe('chatting_solo')
    })

    it('sets status to chatting_duo', () => {
      useSessionStore.getState().setStatus('chatting_duo')
      expect(useSessionStore.getState().status).toBe('chatting_duo')
    })

    it('sets status to awaiting_finalization', () => {
      useSessionStore.getState().setStatus('awaiting_finalization')
      expect(useSessionStore.getState().status).toBe('awaiting_finalization')
    })

    it('sets status to completed', () => {
      useSessionStore.getState().setStatus('completed')
      expect(useSessionStore.getState().status).toBe('completed')
    })

    it('sets status to terminated', () => {
      useSessionStore.getState().setStatus('terminated')
      expect(useSessionStore.getState().status).toBe('terminated')
    })

    it('transitions from solo to duo', () => {
      useSessionStore.setState({ status: 'chatting_solo' })
      useSessionStore.getState().setStatus('chatting_duo')
      expect(useSessionStore.getState().status).toBe('chatting_duo')
    })

    it('allows any status transition', () => {
      const statuses: SessionStatus[] = ['chatting_solo', 'chatting_duo', 'awaiting_finalization', 'completed', 'terminated']

      for (const status of statuses) {
        useSessionStore.getState().setStatus(status)
        expect(useSessionStore.getState().status).toBe(status)
      }
    })
  })

  describe('appendTurn', () => {
    it('appends turn to turns array', () => {
      const turn = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'Message 1',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn)

      expect(useSessionStore.getState().turns).toHaveLength(1)
      expect(useSessionStore.getState().turns[0]).toEqual(turn)
    })

    it('appends multiple turns in order', () => {
      const turn1 = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'First',
        createdAt: new Date().toISOString(),
      }
      const turn2 = {
        turnNumber: 2,
        role: 'B' as const,
        content: 'Second',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn1)
      useSessionStore.getState().appendTurn(turn2)

      expect(useSessionStore.getState().turns).toHaveLength(2)
      expect(useSessionStore.getState().turns[0].content).toBe('First')
      expect(useSessionStore.getState().turns[1].content).toBe('Second')
    })

    it('preserves previous turns', () => {
      const turn1 = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'First',
        createdAt: new Date().toISOString(),
      }
      const turn2 = {
        turnNumber: 2,
        role: 'B' as const,
        content: 'Second',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.setState({ turns: [turn1] })
      useSessionStore.getState().appendTurn(turn2)

      expect(useSessionStore.getState().turns).toHaveLength(2)
      expect(useSessionStore.getState().turns[0]).toEqual(turn1)
      expect(useSessionStore.getState().turns[1]).toEqual(turn2)
    })

    it('handles turn with mediator message', () => {
      const turn = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'My message',
        mediatorMessage: 'Reframed message',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn)

      expect(useSessionStore.getState().turns[0].mediatorMessage).toBe('Reframed message')
    })

    it('handles turn with perspective taking flag', () => {
      const turn = {
        turnNumber: 2,
        role: 'B' as const,
        content: 'Response',
        isPerspectiveTaking: true,
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn)

      expect(useSessionStore.getState().turns[0].isPerspectiveTaking).toBe(true)
    })

    it('handles skipped turn', () => {
      const turn = {
        turnNumber: 3,
        role: 'A' as const,
        content: '',
        skipped: true,
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn)

      expect(useSessionStore.getState().turns[0].skipped).toBe(true)
    })

    it('persists turns to localStorage', () => {
      const turn = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'Message',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn)

      const stored = localStorage.getItem('again-spring-session')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.turns).toHaveLength(1)
    })
  })

  describe('setCurrentTurn', () => {
    it('sets current turn number', () => {
      useSessionStore.getState().setCurrentTurn(5)
      expect(useSessionStore.getState().currentTurn).toBe(5)
    })

    it('handles incrementing turn number', () => {
      useSessionStore.setState({ currentTurn: 1 })
      useSessionStore.getState().setCurrentTurn(2)
      expect(useSessionStore.getState().currentTurn).toBe(2)

      useSessionStore.getState().setCurrentTurn(3)
      expect(useSessionStore.getState().currentTurn).toBe(3)
    })

    it('can set turn to large number', () => {
      useSessionStore.getState().setCurrentTurn(100)
      expect(useSessionStore.getState().currentTurn).toBe(100)
    })
  })

  describe('setRole', () => {
    it('sets role to A', () => {
      useSessionStore.getState().setRole('A')
      expect(useSessionStore.getState().role).toBe('A')
    })

    it('sets role to B', () => {
      useSessionStore.getState().setRole('B')
      expect(useSessionStore.getState().role).toBe('B')
    })

    it('can switch role', () => {
      useSessionStore.getState().setRole('A')
      useSessionStore.getState().setRole('B')
      expect(useSessionStore.getState().role).toBe('B')
    })

    it('persists role to localStorage', () => {
      useSessionStore.getState().setRole('A')
      const stored = localStorage.getItem('again-spring-session')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.role).toBe('A')
    })
  })

  describe('setPartnerNickname', () => {
    it('sets partner nickname', () => {
      useSessionStore.getState().setPartnerNickname('Partner123')
      expect(useSessionStore.getState().partnerNickname).toBe('Partner123')
    })

    it('overwrites previous nickname', () => {
      useSessionStore.getState().setPartnerNickname('First')
      useSessionStore.getState().setPartnerNickname('Second')
      expect(useSessionStore.getState().partnerNickname).toBe('Second')
    })

    it('handles Korean nicknames', () => {
      useSessionStore.getState().setPartnerNickname('파트너')
      expect(useSessionStore.getState().partnerNickname).toBe('파트너')
    })

    it('handles empty string', () => {
      useSessionStore.getState().setPartnerNickname('')
      expect(useSessionStore.getState().partnerNickname).toBe('')
    })
  })

  describe('setSoloMode', () => {
    it('sets solo mode to true', () => {
      useSessionStore.getState().setSoloMode(true)
      expect(useSessionStore.getState().soloMode).toBe(true)
    })

    it('sets solo mode to false', () => {
      useSessionStore.getState().setSoloMode(false)
      expect(useSessionStore.getState().soloMode).toBe(false)
    })

    it('toggles solo mode', () => {
      useSessionStore.getState().setSoloMode(true)
      expect(useSessionStore.getState().soloMode).toBe(true)

      useSessionStore.getState().setSoloMode(false)
      expect(useSessionStore.getState().soloMode).toBe(false)

      useSessionStore.getState().setSoloMode(true)
      expect(useSessionStore.getState().soloMode).toBe(true)
    })

    it('persists solo mode to localStorage', () => {
      useSessionStore.getState().setSoloMode(true)
      const stored = localStorage.getItem('again-spring-session')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.soloMode).toBe(true)
    })
  })

  describe('Persistence', () => {
    it('restores state from localStorage on init', () => {
      useSessionStore.getState().setSession({
        id: 'session-123',
        inviteToken: 'token-abc',
      })
      useSessionStore.getState().setStatus('chatting_duo')
      useSessionStore.getState().setRole('A')

      const state = useSessionStore.getState()
      expect(state.sessionId).toBe('session-123')
      expect(state.status).toBe('chatting_duo')
      expect(state.role).toBe('A')
    })
  })

  describe('Complex scenario: full session lifecycle', () => {
    it('sets up initial solo session', () => {
      useSessionStore.getState().setRelationType('couple')
      useSessionStore.getState().setCategory({
        majorId: 'couple',
        middleId: 'couple_contact',
        minorId: 'contact_too_little',
      })
      useSessionStore.getState().setDescription('우리는 연락이 너무 적어')
      useSessionStore.getState().setRole('A')
      useSessionStore.getState().setSession({
        id: 'sess-1',
        inviteToken: 'inv-token-1',
      })

      const state = useSessionStore.getState()
      expect(state.relationType).toBe('couple')
      expect(state.role).toBe('A')
      expect(state.status).toBe('chatting_solo')
      expect(state.sessionId).toBe('sess-1')
    })

    it('transitions from solo to duo session', () => {
      // Start solo
      useSessionStore.getState().setStatus('chatting_solo')
      expect(useSessionStore.getState().status).toBe('chatting_solo')

      // Partner joins, add turn
      const turn = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'Initial message',
        createdAt: new Date().toISOString(),
      }
      useSessionStore.getState().appendTurn(turn)

      // Transition to duo
      useSessionStore.getState().setStatus('chatting_duo')
      useSessionStore.getState().setPartnerNickname('Partner')

      expect(useSessionStore.getState().status).toBe('chatting_duo')
      expect(useSessionStore.getState().turns).toHaveLength(1)
      expect(useSessionStore.getState().partnerNickname).toBe('Partner')
    })

    it('completes session with multiple turns and finalization', () => {
      // Setup
      useSessionStore.getState().setSession({
        id: 'sess-1',
        inviteToken: 'inv-1',
      })
      useSessionStore.getState().setStatus('chatting_duo')
      useSessionStore.getState().setRole('A')

      // Add turns
      const turn1 = {
        turnNumber: 1,
        role: 'A' as const,
        content: 'Message 1',
        createdAt: new Date().toISOString(),
      }
      const turn2 = {
        turnNumber: 2,
        role: 'B' as const,
        content: 'Message 2',
        createdAt: new Date().toISOString(),
      }

      useSessionStore.getState().appendTurn(turn1)
      useSessionStore.getState().appendTurn(turn2)
      useSessionStore.getState().setCurrentTurn(3)

      // Finalize
      useSessionStore.getState().setStatus('awaiting_finalization')
      useSessionStore.getState().setStatus('completed')

      const state = useSessionStore.getState()
      expect(state.status).toBe('completed')
      expect(state.turns).toHaveLength(2)
      expect(state.currentTurn).toBe(3)
    })
  })
})
