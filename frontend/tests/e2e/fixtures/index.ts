import { test as base, expect, type Page } from '@playwright/test';
import { pickReport } from '../../../mocks/fixtures/mockReports';

const MOCK_SESSIONS: Record<string, any> = {
  sess_history_1: {
    id: 'sess_history_1',
    status: 'completed',
    relationType: 'marriage',
    conflictType: 'difference',
    partnerNickname: '준호',
    soloMode: false,
    myRole: 'A',
    completedAt: '2026-04-12T09:21:00.000Z',
    createdAt: '2026-04-12T08:00:00.000Z',
  },
  sess_history_2: {
    id: 'sess_history_2',
    status: 'completed',
    relationType: 'friend',
    conflictType: 'factual',
    partnerNickname: '지민',
    soloMode: false,
    myRole: 'A',
    completedAt: '2026-03-02T12:05:00.000Z',
    createdAt: '2026-03-02T11:00:00.000Z',
  },
  sess_history_3: {
    id: 'sess_history_3',
    status: 'completed',
    relationType: 'parent_child',
    conflictType: 'mixed',
    partnerNickname: '엄마',
    soloMode: false,
    myRole: 'A',
    completedAt: '2026-02-17T18:40:00.000Z',
    createdAt: '2026-02-17T17:30:00.000Z',
  },
  sess_active: {
    id: 'sess_active',
    status: 'chatting_solo',
    relationType: 'couple',
    category: {
      majorId: 'couple',
      middleId: 'couple_communication',
      minorId: 'contact_too_little',
    },
    partnerNickname: '준호',
    soloMode: true,
    myRole: 'A',
    inviteToken: 'tok_active_test',
    createdAt: new Date().toISOString(),
  },
};

const MOCK_USER = {
  id: 'user_test',
  email: 'test@example.com',
  nickname: '테스트',
  communicationStyle: 'wave',
  communicationStyleScore: { wave: 10, anchor: 7, explorer: 5, bridge: 8, mirror: 6, signal: 4 },
  onboardingCompleted: true,
};

async function setupApiMocks(page: Page) {
  await page.route(/\/api\//, async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    // ── /api/sessions/:id/report ──────────────────────────────────────
    const reportMatch = path.match(/^\/api\/sessions\/([^/]+)\/report$/);
    if (reportMatch) {
      const sessionId = reportMatch[1];
      const report = pickReport(sessionId);
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(report) });
    }

    // ── /api/mock/report?scenario=... ─────────────────────────────────
    if (path === '/api/mock/report') {
      const scenario = url.searchParams.get('scenario') ?? 'difference';
      const report = pickReport(`force_${scenario}`);
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(report) });
    }

    // ── /api/sessions/:id/messages ────────────────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/messages/)) {
      if (method === 'POST') {
        let body: any = {};
        try { body = JSON.parse(route.request().postData() || '{}'); } catch {}
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            userMessage: {
              id: Date.now(),
              sender: 'USER_A',
              content: body.content || '',
              charCount: (body.content || '').length,
              isFinalizeSuggestion: false,
              isPartnerJoinNotice: false,
              createdAt: new Date().toISOString(),
            },
            mediatorMessage: {
              id: Date.now() + 1,
              sender: 'MEDIATOR_TO_A',
              content: '그러셨군요. 그 마음이 어떤 감정으로 느껴지셨는지 조금 더 들려주시겠어요?',
              charCount: 37,
              isFinalizeSuggestion: false,
              isPartnerJoinNotice: false,
              createdAt: new Date(Date.now() + 1000).toISOString(),
            },
            finalizeSuggested: false,
            crisisLevel: null,
          }),
        });
      }
      // GET messages
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }

    // ── /api/sessions/:id/partner-status ─────────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/partner-status$/)) {
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ joined: false, isActive: false, inviteSent: false, messageCount: 0, lastActivityAt: null }),
      });
    }

    // ── /api/sessions/:id/partner-messages ───────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/partner-messages$/)) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }

    // ── /api/sessions/:id/invite ──────────────────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/invite$/) && method === 'POST') {
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ inviteToken: 'inv_mock_test', inviteExpiresAt: new Date(Date.now() + 72 * 3600000).toISOString() }),
      });
    }

    // ── /api/sessions/:id/finalize ────────────────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/finalize/) && method === 'POST') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"completed":true,"awaitingPartner":false}' });
    }

    // ── /api/sessions/:id/solo ────────────────────────────────────────
    if (path.match(/^\/api\/sessions\/[^/]+\/solo$/) && method === 'POST') {
      const sessionId = path.split('/')[3];
      const session = MOCK_SESSIONS[sessionId] ?? { id: sessionId, status: 'chatting_solo' };
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...session, status: 'chatting_solo' }) });
    }

    // ── GET /api/sessions/:id ─────────────────────────────────────────
    const sessionMatch = path.match(/^\/api\/sessions\/([^/]+)$/);
    if (sessionMatch && method === 'GET') {
      const sessionId = sessionMatch[1];
      const session = MOCK_SESSIONS[sessionId];
      if (session) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(session) });
      }
      return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"not_found"}' });
    }

    // ── POST /api/sessions (create) ───────────────────────────────────
    if (path === '/api/sessions' && method === 'POST') {
      const id = `sess_test_${Date.now().toString(36)}`;
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          id,
          inviteToken: `tok_${Math.random().toString(36).slice(2, 10)}`,
          status: 'chatting_solo',
          currentTurn: 1,
          turns: [],
          createdAt: new Date().toISOString(),
        }),
      });
    }

    // ── GET /api/sessions (list) ──────────────────────────────────────
    if (path === '/api/sessions' && method === 'GET') {
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify([MOCK_SESSIONS.sess_history_1, MOCK_SESSIONS.sess_history_2, MOCK_SESSIONS.sess_history_3]),
      });
    }

    // ── /api/sessions/by-token/:token ─────────────────────────────────
    if (path.match(/^\/api\/sessions\/by-token\//)) {
      const token = path.split('/').pop() ?? '';
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          id: `sess_demo_${token}`,
          inviteToken: token,
          status: 'waiting_b',
          relationType: 'marriage',
          inviterNickname: '서현',
          createdAt: new Date().toISOString(),
        }),
      });
    }

    // ── /api/auth/login ───────────────────────────────────────────────
    if (path === '/api/auth/login' && method === 'POST') {
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ user: MOCK_USER, token: { accessToken: 'mock_token_e2e_test' } }),
      });
    }

    // ── /api/auth/guest ───────────────────────────────────────────────
    if (path === '/api/auth/guest' && method === 'POST') {
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          user: { ...MOCK_USER, id: 'guest_test', email: '', nickname: '테스트게스트' },
          token: { accessToken: 'mock_guest_token_e2e' },
        }),
      });
    }

    // ── /api/auth/* (signup, logout, etc.) ───────────────────────────
    if (path.startsWith('/api/auth/')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"ok":true}' });
    }

    // ── GET /api/users/me ─────────────────────────────────────────────
    if (path === '/api/users/me' && method === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) });
    }

    // ── PUT/PATCH /api/users/me (profile update) ──────────────────────
    if (path.startsWith('/api/users/me') && (method === 'PUT' || method === 'PATCH' || method === 'POST')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) });
    }

    // ── /api/health ───────────────────────────────────────────────────
    if (path === '/api/health') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' });
    }

    // Allow everything else through
    return route.continue();
  });
}

export const test = base.extend<{ page: any }>({
  page: async ({ page }, use) => {
    await setupApiMocks(page);
    await use(page);
  },
});

export { expect };
