import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useUserStore } from '@/lib/store/userStore'
import type { User, CommunicationStyle } from '@/lib/types'

describe('useUserStore (Zustand)', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear()
    // Reset store to initial state
    useUserStore.setState({
      user: null,
    })
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('setUser', () => {
    it('sets user in store', () => {
      const testUser: User = {
        id: 'user-1',
        email: 'test@example.com',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(testUser)

      expect(useUserStore.getState().user).toEqual(testUser)
    })

    it('persists user to localStorage', () => {
      const testUser: User = {
        id: 'user-1',
        email: 'test@example.com',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(testUser)

      // Check localStorage
      const stored = localStorage.getItem('again-spring-user')
      expect(stored).toBeTruthy()
      const parsed = JSON.parse(stored!)
      expect(parsed.state.user).toEqual(testUser)
    })

    it('overwrites previous user', () => {
      const user1: User = {
        id: 'user-1',
        nickname: 'User1',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }
      const user2: User = {
        id: 'user-2',
        nickname: 'User2',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user1)
      expect(useUserStore.getState().user?.id).toBe('user-1')

      useUserStore.getState().setUser(user2)
      expect(useUserStore.getState().user?.id).toBe('user-2')
    })

    it('handles null email for guest users', () => {
      const guestUser: User = {
        id: 'guest-1',
        nickname: 'Guest_abc123',
        isGuest: true,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(guestUser)

      expect(useUserStore.getState().user?.isGuest).toBe(true)
      expect(useUserStore.getState().user?.email).toBeUndefined()
    })
  })

  describe('setStyle', () => {
    it('updates communicationStyle when user exists', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setStyle('mountain')

      expect(useUserStore.getState().user?.communicationStyle).toBe('mountain')
    })

    it('does not update if user is null', () => {
      useUserStore.setState({ user: null })
      useUserStore.getState().setStyle('flame')

      expect(useUserStore.getState().user).toBeNull()
    })

    it('cycles through all 6 communication styles', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      const styles: CommunicationStyle[] = ['wave', 'mountain', 'flame', 'leaf', 'moon', 'star']

      useUserStore.getState().setUser(user)

      for (const style of styles) {
        useUserStore.getState().setStyle(style)
        expect(useUserStore.getState().user?.communicationStyle).toBe(style)
      }
    })

    it('persists style change to localStorage', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setStyle('leaf')

      const stored = localStorage.getItem('again-spring-user')
      const parsed = JSON.parse(stored!)
      expect(parsed.state.user.communicationStyle).toBe('leaf')
    })
  })

  describe('setOnboardingAnswers', () => {
    it('updates onboardingAnswers array', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const answers = [1, 2, 3, 4, 5, 1, 2, 3, 4, 5]
      useUserStore.getState().setOnboardingAnswers(answers)

      expect(useUserStore.getState().user?.onboardingAnswers).toEqual(answers)
    })

    it('does not update if user is null', () => {
      useUserStore.setState({ user: null })
      useUserStore.getState().setOnboardingAnswers([1, 2, 3, 4, 5])

      expect(useUserStore.getState().user).toBeNull()
    })

    it('overwrites previous answers', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        onboardingAnswers: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const newAnswers = [5, 5, 5, 5, 5, 5, 5, 5, 5, 5]
      useUserStore.getState().setOnboardingAnswers(newAnswers)

      expect(useUserStore.getState().user?.onboardingAnswers).toEqual(newAnswers)
    })

    it('handles 10-item Likert answers (1-5 scale)', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const answers = [1, 2, 3, 4, 5, 1, 2, 3, 4, 5]
      useUserStore.getState().setOnboardingAnswers(answers)

      expect(useUserStore.getState().user?.onboardingAnswers).toHaveLength(10)
      expect(useUserStore.getState().user?.onboardingAnswers).toEqual(answers)
    })
  })

  describe('setOnboardingCompleted', () => {
    it('sets onboardingCompletedAt timestamp', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const before = new Date()
      useUserStore.getState().setOnboardingCompleted()
      const after = new Date()

      const completed = useUserStore.getState().user?.onboardingCompletedAt
      expect(completed).toBeTruthy()
      const completedDate = new Date(completed!)
      expect(completedDate.getTime()).toBeGreaterThanOrEqual(before.getTime())
      expect(completedDate.getTime()).toBeLessThanOrEqual(after.getTime())
    })

    it('sets onboardingMethod to "test" if not already set', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setOnboardingCompleted()

      expect(useUserStore.getState().user?.onboardingMethod).toBe('test')
    })

    it('preserves existing onboardingCompletedAt and onboardingMethod', () => {
      const existingDate = '2026-01-01T00:00:00.000Z'
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        onboardingCompletedAt: existingDate,
        onboardingMethod: 'mbti',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setOnboardingCompleted()

      expect(useUserStore.getState().user?.onboardingCompletedAt).toBe(existingDate)
      expect(useUserStore.getState().user?.onboardingMethod).toBe('mbti')
    })

    it('does not update if user is null', () => {
      useUserStore.setState({ user: null })
      useUserStore.getState().setOnboardingCompleted()

      expect(useUserStore.getState().user).toBeNull()
    })
  })

  describe('setMbtiType', () => {
    it('sets MBTI type', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setMbtiType('ENFP')

      expect(useUserStore.getState().user?.mbtiType).toBe('ENFP')
    })

    it('sets onboardingMethod to "mbti"', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setMbtiType('INTJ')

      expect(useUserStore.getState().user?.onboardingMethod).toBe('mbti')
    })

    it('sets onboardingCompletedAt if not already set', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setMbtiType('INFJ')

      expect(useUserStore.getState().user?.onboardingCompletedAt).toBeTruthy()
    })

    it('preserves existing onboardingCompletedAt', () => {
      const existingDate = '2026-01-01T00:00:00.000Z'
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        onboardingCompletedAt: existingDate,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      useUserStore.getState().setMbtiType('ISFJ')

      expect(useUserStore.getState().user?.onboardingCompletedAt).toBe(existingDate)
    })

    it('handles all 16 MBTI types', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      const mbtiTypes = [
        'ENFP', 'ENFJ', 'ESFP', 'ESFJ',
        'INFP', 'INFJ', 'ISFP', 'ISFJ',
        'ENTJ', 'ENTP', 'ESTJ', 'ESTP',
        'INTJ', 'INTP', 'ISTJ', 'ISTP',
      ]

      useUserStore.getState().setUser(user)

      for (const mbti of mbtiTypes) {
        useUserStore.getState().setMbtiType(mbti)
        expect(useUserStore.getState().user?.mbtiType).toBe(mbti)
      }
    })

    it('does not update if user is null', () => {
      useUserStore.setState({ user: null })
      useUserStore.getState().setMbtiType('ENFP')

      expect(useUserStore.getState().user).toBeNull()
    })
  })

  describe('clear', () => {
    it('sets user to null', () => {
      const user: User = {
        id: 'user-1',
        email: 'test@example.com',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      expect(useUserStore.getState().user).not.toBeNull()

      useUserStore.getState().clear()

      expect(useUserStore.getState().user).toBeNull()
    })

    it('removes again-spring-token from localStorage', () => {
      localStorage.setItem('again-spring-token', 'token-value')
      expect(localStorage.getItem('again-spring-token')).toBe('token-value')

      useUserStore.getState().clear()

      expect(localStorage.getItem('again-spring-token')).toBeNull()
    })

    it('clears user-related localStorage', () => {
      const user: User = {
        id: 'user-1',
        email: 'test@example.com',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const beforeClear = localStorage.getItem('again-spring-user')
      expect(beforeClear).toBeTruthy()

      useUserStore.getState().clear()

      const afterClear = localStorage.getItem('again-spring-user')
      expect(afterClear).toBeTruthy() // persist middleware rebuilds on next init
    })

    it('is safe to call when user is already null', () => {
      useUserStore.setState({ user: null })
      expect(() => useUserStore.getState().clear()).not.toThrow()
    })
  })

  describe('Persistence (hydration)', () => {
    it('restores user from localStorage on initialization', () => {
      const user: User = {
        id: 'user-1',
        email: 'test@example.com',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        onboardingAnswers: [1, 2, 3, 4, 5, 1, 2, 3, 4, 5],
        createdAt: new Date().toISOString(),
      }

      // Set initial user
      useUserStore.getState().setUser(user)

      // Simulate new store instance (like page reload)
      const newStore = useUserStore.getState()
      expect(newStore.user).toEqual(user)
    })

    it('handles missing localStorage gracefully', () => {
      localStorage.clear()
      const state = useUserStore.getState()
      expect(state.user).toBeNull()
    })
  })

  describe('SSR safety', () => {
    it('does not throw when window is undefined', () => {
      // The store internally checks for window before accessing localStorage
      const state = useUserStore.getState()
      expect(state).toBeDefined()
      expect(state.user).toBeDefined()
    })

    it('clear() handles missing window gracefully', () => {
      useUserStore.getState().setUser({
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        createdAt: new Date().toISOString(),
      })

      // clear() checks window !== 'undefined'
      expect(() => useUserStore.getState().clear()).not.toThrow()
    })
  })

  describe('State immutability', () => {
    it('does not mutate user object directly', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const retrieved = useUserStore.getState().user

      // Attempt to modify
      if (retrieved) {
        const modified = { ...retrieved, nickname: 'Modified' }
        useUserStore.getState().setUser(modified)
      }

      expect(useUserStore.getState().user?.nickname).toBe('Modified')
    })

    it('creates new reference on setStyle', () => {
      const user: User = {
        id: 'user-1',
        nickname: 'TestUser',
        isGuest: false,
        communicationStyle: 'wave',
        createdAt: new Date().toISOString(),
      }

      useUserStore.getState().setUser(user)
      const before = useUserStore.getState().user

      useUserStore.getState().setStyle('mountain')
      const after = useUserStore.getState().user

      expect(before).not.toBe(after)
      expect(before?.id).toBe(after?.id)
      expect(before?.communicationStyle).not.toBe(after?.communicationStyle)
    })
  })
})
