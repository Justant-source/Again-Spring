import { http, HttpResponse, delay } from 'msw';

const USERS = new Map<string, any>();
const SESSION_HISTORY: any[] = [
  {
    id: 'sess_history_1',
    partnerNickname: '준호',
    relationType: 'marriage',
    conflictType: 'difference',
    temperature: 36.2,
    completedAt: '2026-04-12T09:21:00.000Z',
    summary: '주말 집안일 분담에 대한 서로 다른 쉼의 정의',
  },
  {
    id: 'sess_history_2',
    partnerNickname: '지민',
    relationType: 'friend',
    conflictType: 'factual',
    temperature: 35.8,
    completedAt: '2026-03-02T12:05:00.000Z',
    summary: '반복된 약속 취소와 신뢰 회복',
  },
  {
    id: 'sess_history_3',
    partnerNickname: '엄마',
    relationType: 'parent_child',
    conflictType: 'mixed',
    temperature: 36.5,
    completedAt: '2026-02-17T18:40:00.000Z',
    summary: '독립 시기와 돌봄 방식의 간극',
  },
];

export const userHandlers = [
  http.post('/api/auth/signup', async ({ request }) => {
    await delay(600);
    const body: any = await request.json();
    const id = `user_${Date.now().toString(36)}`;
    const user = {
      id,
      email: body.email,
      nickname: body.nickname,
      isGuest: false,
      temperatureHistory: [],
      createdAt: new Date().toISOString(),
    };
    USERS.set(id, user);
    return HttpResponse.json(user);
  }),

  http.post('/api/auth/login', async ({ request }) => {
    await delay(400);
    const body: any = await request.json();
    const user = {
      id: 'user_demo',
      email: body.email,
      nickname: '서현',
      isGuest: false,
      communicationStyle: 'wave',
      temperatureHistory: [],
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json(user);
  }),

  http.post('/api/auth/guest', async ({ request }) => {
    await delay(200);
    const body: any = await request.json();
    return HttpResponse.json({
      id: `guest_${Date.now().toString(36)}`,
      nickname: body?.nickname || '손님',
      isGuest: true,
      temperatureHistory: [],
      createdAt: new Date().toISOString(),
    });
  }),

  http.get('/api/users/me/history', async () => {
    await delay(300);
    return HttpResponse.json(SESSION_HISTORY);
  }),
];
