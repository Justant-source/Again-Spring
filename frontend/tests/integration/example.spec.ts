import { describe, it, expect } from 'vitest'
import {
  createUser,
  createSession,
  createMessage,
  createSessionWithMessages,
  createUserWithSessions,
} from '@/tests/fixtures'

describe('Example: Using fixtures in integration tests', () => {
  it('creates a user with default values', () => {
    const user = createUser()

    expect(user.id).toBeDefined()
    expect(user.nickname).toBe('TestUser')
    expect(user.isGuest).toBe(false)
    expect(user.communicationStyle).toBe('wave')
  })

  it('creates a user with custom overrides', () => {
    const customUser = createUser({
      nickname: 'Alice',
      communicationStyle: 'flame',
      isGuest: true,
    })

    expect(customUser.nickname).toBe('Alice')
    expect(customUser.communicationStyle).toBe('flame')
    expect(customUser.isGuest).toBe(true)
  })

  it('creates a session with duo status', () => {
    const session = createSession({
      status: 'chatting_duo',
      userAMessageCount: 10,
      userBMessageCount: 8,
    })

    expect(session.status).toBe('chatting_duo')
    expect(session.userAMessageCount).toBe(10)
    expect(session.userBMessageCount).toBe(8)
  })

  it('creates messages from different senders', () => {
    const userAMessage = createMessage({ sender: 'USER_A' })
    const userBMessage = createMessage({ sender: 'USER_B' })
    const mediatorMessage = createMessage({ sender: 'MEDIATOR_TO_A' })

    expect(userAMessage.sender).toBe('USER_A')
    expect(userBMessage.sender).toBe('USER_B')
    expect(mediatorMessage.sender).toBe('MEDIATOR_TO_A')
  })

  it('creates a batch of messages with a sequence', () => {
    const pattern = ['USER_A', 'USER_B', 'MEDIATOR_TO_A', 'MEDIATOR_TO_B'] as const
    const messages = pattern.map((sender) =>
      createMessage({ sender, content: `Message from ${sender}` }),
    )

    expect(messages).toHaveLength(4)
    expect(messages[0].sender).toBe('USER_A')
    expect(messages[1].sender).toBe('USER_B')
    expect(messages[2].sender).toBe('MEDIATOR_TO_A')
    expect(messages[3].sender).toBe('MEDIATOR_TO_B')
  })

  it('creates a session with messages via helper', () => {
    const { session, messages } = createSessionWithMessages(5)

    expect(session.id).toBeDefined()
    expect(messages).toHaveLength(5)
    expect(messages[0].sender).toBe('USER_A')
  })

  it('creates a user with multiple sessions', () => {
    const { user, sessions } = createUserWithSessions(3)

    expect(user.id).toBeDefined()
    expect(sessions).toHaveLength(3)
    expect(sessions.every((s) => s.createdByUserId === user.id)).toBe(true)
  })
})
