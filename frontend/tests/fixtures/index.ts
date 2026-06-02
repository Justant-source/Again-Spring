import type {
  User,
  CommunicationStyle,
} from '@/lib/types'

// Legacy types removed — use any for fixtures
type Message = any;
type Session = any;
type Report = any;
type RelationType = string;
type MessageSender = string;
type SessionStatus = string;

// ============================================================================
// User Fixtures
// ============================================================================

export function createUser(overrides?: Partial<User>): User {
  return {
    id: '550e8400-e29b-41d4-a716-446655440000',
    email: 'test@example.com',
    nickname: 'TestUser',
    isGuest: false,
    communicationStyle: 'wave',
    onboardingAnswers: [0, 1, 2, 3, 4, 5],
    onboardingCompletedAt: new Date().toISOString(),
    mbtiType: 'ENFP',
    onboardingMethod: 'test',
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

export function createGuestUser(overrides?: Partial<User>): User {
  return createUser({
    id: '550e8400-e29b-41d4-a716-446655440001',
    email: undefined,
    nickname: 'Guest_' + Math.random().toString(36).substr(2, 9),
    isGuest: true,
    onboardingCompletedAt: null,
    ...overrides,
  })
}

// ============================================================================
// Session Fixtures
// ============================================================================

export function createSession(overrides?: Partial<Session>): Session {
  return {
    id: '660e8400-e29b-41d4-a716-446655440000',
    status: 'chatting_solo' as SessionStatus,
    relationType: 'couple' as RelationType,
    // V47~: 중·소분류 제거
    category: {
      majorId: 'major-1',
      customText: 'Custom conflict text',
    },
    createdByUserId: '550e8400-e29b-41d4-a716-446655440000',
    inviteeUserId: null,
    inviteToken: 'token_' + Math.random().toString(36).substr(2, 32),
    inviteExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    userAMessageCount: 0,
    userBMessageCount: 0,
    partnerJoinedAt: null,
    finalizeSuggestedAt: null,
    finalizeAgreedByA: false,
    finalizeAgreedByB: false,
    myRole: 'USER_A' as const,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

export function createDuoSession(overrides?: Partial<Session>): Session {
  return createSession({
    status: 'chatting_duo' as SessionStatus,
    inviteeUserId: '550e8400-e29b-41d4-a716-446655440001',
    partnerJoinedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    userAMessageCount: 5,
    userBMessageCount: 4,
    ...overrides,
  })
}

// ============================================================================
// Message Fixtures
// ============================================================================

export function createMessage(overrides?: Partial<Message>): Message {
  const content = 'This is a test message.'
  return {
    id: 1,
    sender: 'USER_A' as MessageSender,
    content,
    charCount: content.length,
    isFinalizeSuggestion: false,
    isPartnerJoinNotice: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

export function createMediatorMessage(
  overrides?: Partial<Message>,
  sender: 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B' = 'MEDIATOR_TO_A',
): Message {
  const content =
    "I can see both perspectives. Let me help reframe this conversation using NVC..."
  return createMessage({
    sender,
    content,
    charCount: content.length,
    ...overrides,
  })
}

export function createSessionMessages(
  count: number,
  sender: MessageSender = 'USER_A',
): Message[] {
  const messages: Message[] = []
  for (let i = 0; i < count; i++) {
    messages.push(
      createMessage({
        id: i,
        sender,
        content: `Message ${i + 1} from ${sender}`,
      }),
    )
  }
  return messages
}

export function createMessageSequence(
  pattern: MessageSender[],
): Message[] {
  const messages: Message[] = []
  pattern.forEach((sender, idx) => {
    messages.push(
      createMessage({
        id: idx,
        sender,
        content: `Message ${idx + 1} from ${sender}`,
      }),
    )
  })
  return messages
}

// ============================================================================
// Report Fixtures
// ============================================================================

export function createReport(overrides?: Partial<Report>): Report {
  return {
    id: '770e8400-e29b-41d4-a716-446655440000',
    sessionId: '660e8400-e29b-41d4-a716-446655440000',
    conflictType: 'difference',
    contributionRatio: {
      a: 55,
      b: 45,
      label: {
        a: 'User A gave more understanding',
        b: 'User B gave more understanding',
      },
    },
    needsMap: {
      axisX: 'Independence',
      axisY: 'Connection',
      positionA: { x: 70, y: 30 },
      positionB: { x: 40, y: 80 },
      interpretation: 'User A values independence more, User B values connection.',
    },
    repairSuggestions: [
      'Practice active listening',
      'Use NVC framework',
      'Schedule regular check-ins',
    ],
    isSoloMode: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

export function createSoloModeReport(overrides?: Partial<Report>): Report {
  return createReport({
    isSoloMode: true,
    contributionRatio: null,
    ...overrides,
  })
}

// ============================================================================
// Batch Creation Helpers
// ============================================================================

export function createUserWithSessions(
  sessionCount: number = 3,
  userOverrides?: Partial<User>,
): { user: User; sessions: Session[] } {
  const user = createUser(userOverrides)
  const sessions = Array.from({ length: sessionCount }, (_, i) =>
    createSession({
      id: `session-${i}`,
      createdByUserId: user.id,
    }),
  )
  return { user, sessions }
}

export function createSessionWithMessages(
  messageCount: number = 10,
  sessionOverrides?: Partial<Session>,
): { session: Session; messages: Message[] } {
  const session = createSession(sessionOverrides)
  const messages = createSessionMessages(messageCount, 'USER_A')
  return { session, messages }
}
