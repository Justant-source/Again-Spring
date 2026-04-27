import { describe, it, expect } from 'vitest'

describe('smoke test', () => {
  it('vitest works', () => {
    expect(1 + 1).toBe(2)
  })

  it('can import fixtures', async () => {
    const { createUser, createSession, createMessage } = await import('@/tests/fixtures')

    const user = createUser()
    expect(user.id).toBeDefined()
    expect(user.nickname).toBe('TestUser')

    const session = createSession()
    expect(session.id).toBeDefined()
    expect(session.status).toBe('chatting_solo')

    const message = createMessage()
    expect(message.sender).toBe('USER_A')
  })

  it('MSW server is available', async () => {
    const { server } = await import('@/mocks/server')
    expect(server).toBeDefined()
    expect(server.listen).toBeDefined()
  })
})
