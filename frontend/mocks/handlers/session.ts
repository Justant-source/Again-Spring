import { http, HttpResponse, delay } from 'msw';

const SESSIONS = new Map<string, any>();

export const sessionHandlers = [
  http.post('/api/sessions', async ({ request }) => {
    await delay(600);
    const body: any = await request.json();
    const id = `sess_${Date.now().toString(36)}`;
    const token = `tok_${Math.random().toString(36).slice(2, 10)}`;
    const session = {
      id,
      inviteToken: token,
      status: 'waiting_b',
      currentTurn: 1,
      turns: [],
      createdAt: new Date().toISOString(),
      ...body,
    };
    SESSIONS.set(id, session);
    return HttpResponse.json(session);
  }),

  http.get('/api/sessions/:id', async ({ params }) => {
    await delay(200);
    const s = SESSIONS.get(String(params.id));
    if (!s) return HttpResponse.json({ error: 'not_found' }, { status: 404 });
    return HttpResponse.json(s);
  }),

  http.post('/api/sessions/:id/join', async ({ params, request }) => {
    await delay(400);
    const body: any = await request.json();
    const s = SESSIONS.get(String(params.id));
    if (!s) return HttpResponse.json({ error: 'not_found' }, { status: 404 });
    s.inviteeGuestName = body.nickname;
    s.status = 'b_joined';
    return HttpResponse.json(s);
  }),

  http.post('/api/sessions/:id/solo', async ({ params }) => {
    await delay(300);
    const s = SESSIONS.get(String(params.id));
    if (!s) return HttpResponse.json({ error: 'not_found' }, { status: 404 });
    s.status = 'solo_mode';
    return HttpResponse.json(s);
  }),

  http.get('/api/sessions/by-token/:token', async ({ params }) => {
    await delay(200);
    const token = String(params.token);
    const s = Array.from(SESSIONS.values()).find((v) => v.inviteToken === token);
    if (!s) {
      // 데모용 샘플 세션 반환 (초대 링크 직접 방문)
      return HttpResponse.json({
        id: `sess_demo_${token}`,
        inviteToken: token,
        status: 'waiting_b',
        relationType: 'marriage',
        category: {
          majorId: 'marriage',
          middleId: 'marriage_chores',
          minorId: 'chores_tilt',
        },
        inviterNickname: '서현',
        createdAt: new Date().toISOString(),
      });
    }
    return HttpResponse.json(s);
  }),
];
