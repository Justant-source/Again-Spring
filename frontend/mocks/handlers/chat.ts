import { http, HttpResponse } from 'msw';
import { HISTORY_MESSAGES_MOCK } from './historyMessages';

const messages: Map<string, any[]> = new Map();
const sessionState: Map<string, any> = new Map();

export const chatHandlers = [
  http.post('/api/sessions/:id/messages', async ({ params, request }) => {
    const { id } = params as { id: string };
    const body = await request.json() as { content: string };

    // 위기 키워드 체크 (간단)
    if (/때리|자살|폭행/.test(body.content)) {
      return HttpResponse.json({ crisisLevel: 1 }, { status: 409 });
    }

    const list = messages.get(id) || [];
    const userMsg = {
      id: list.length + 1,
      sender: 'USER_A',
      content: body.content,
      charCount: body.content.length,
      isFinalizeSuggestion: false,
      isPartnerJoinNotice: false,
      createdAt: new Date().toISOString(),
    };
    const mediatorMsg = {
      id: list.length + 2,
      sender: 'MEDIATOR_TO_A',
      content: '그러셨군요. 그 마음을 좀 더 들려주실 수 있을까요?',
      charCount: 24,
      isFinalizeSuggestion: false,
      isPartnerJoinNotice: false,
      createdAt: new Date(Date.now() + 1000).toISOString(),
    };
    list.push(userMsg, mediatorMsg);
    messages.set(id, list);

    const userMessageCount = list.filter(m => m.sender === 'USER_A').length;
    const finalizeSuggested = userMessageCount >= 5;

    return HttpResponse.json({
      userMessage: userMsg,
      mediatorMessage: mediatorMsg,
      finalizeSuggested,
      crisisLevel: null,
    });
  }),

  http.get('/api/sessions/:id/messages', ({ params, request }) => {
    const { id } = params as { id: string };
    const url = new URL(request.url);
    const since = url.searchParams.get('since');
    const list = messages.get(id) || HISTORY_MESSAGES_MOCK[id] || [];

    if (since) {
      const sinceMs = Number(since);
      return HttpResponse.json(list.filter((m: any) => new Date(m.createdAt).getTime() > sinceMs));
    }
    return HttpResponse.json(list);
  }),

  http.get('/api/sessions/:id/partner-messages', ({ params }) => {
    // mock에서는 빈 배열 (단일 사용자 테스트)
    return HttpResponse.json([]);
  }),

  http.get('/api/sessions/:id/partner-status', ({ params }) => {
    const { id } = params as { id: string };
    const state = sessionState.get(id) || { joined: false, inviteSent: false };
    return HttpResponse.json({
      joined: state.joined || false,
      isActive: false,
      inviteSent: state.inviteSent || false,
      messageCount: 0,
      lastActivityAt: null,
    });
  }),

  http.post('/api/sessions/:id/invite', ({ params }) => {
    const { id } = params as { id: string };
    const token = `inv_${Math.random().toString(36).slice(2, 14)}`;
    sessionState.set(id, { ...sessionState.get(id), inviteSent: true });
    return HttpResponse.json({
      inviteToken: token,
      inviteExpiresAt: new Date(Date.now() + 72 * 3600 * 1000).toISOString(),
    });
  }),

  http.post('/api/sessions/:id/finalize', ({ params }) => {
    return HttpResponse.json({ completed: true, awaitingPartner: false });
  }),

  http.post('/api/sessions/:id/finalize/agree', ({ params }) => {
    return HttpResponse.json({ completed: true, awaitingPartner: false });
  }),

  http.post('/api/sessions/:id/finalize/decline', ({ params }) => {
    return new HttpResponse(null, { status: 204 });
  }),
];
