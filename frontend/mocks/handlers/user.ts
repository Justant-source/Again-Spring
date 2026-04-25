import { http, HttpResponse, delay } from 'msw';
import { generateGuestNickname } from '@/lib/utils/guestNickname';

const USERS = new Map<string, any>();
const GUEST_SESSIONS = new Map<string, string>(); // inviteToken → guestId

const SESSION_HISTORY: any[] = [
  {
    id: 'sess_history_1',
    partnerNickname: '준호',
    relationType: 'marriage',
    conflictType: 'difference',
    completedAt: '2026-04-12T09:21:00.000Z',
    summary: '주말 집안일 분담에 대한 서로 다른 쉼의 정의',
  },
  {
    id: 'sess_history_2',
    partnerNickname: '지민',
    relationType: 'friend',
    conflictType: 'factual',
    completedAt: '2026-03-02T12:05:00.000Z',
    summary: '반복된 약속 취소와 신뢰 회복',
  },
  {
    id: 'sess_history_3',
    partnerNickname: '엄마',
    relationType: 'parent_child',
    conflictType: 'mixed',
    completedAt: '2026-02-17T18:40:00.000Z',
    summary: '독립 시기와 돌봄 방식의 간극',
  },
];

function makeGuestId() {
  return `Guest-${String(Math.floor(Math.random() * 1_000_000)).padStart(6, '0')}`;
}

export const userHandlers = [
  http.post('/api/auth/send-verification', async ({ request }) => {
    await delay(300);
    return HttpResponse.json(null, { status: 200 });
  }),

  http.post('/api/auth/signup', async ({ request }) => {
    await delay(600);
    const body: any = await request.json();
    const id = `user_${Date.now().toString(36)}`;
    const user = {
      id,
      email: body.email,
      nickname: body.nickname,
      isGuest: false,
      onboardingCompletedAt: null,
      createdAt: new Date().toISOString(),
    };
    USERS.set(id, user);
    return HttpResponse.json({
      user,
      token: { accessToken: `mock-token-${id}`, expiresIn: 86400 },
    });
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
      onboardingCompletedAt: '2026-04-01T10:00:00.000Z',
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json({
      user,
      token: { accessToken: 'mock-token-user_demo', expiresIn: 86400 },
    });
  }),

  // 소셜 로그인 콜백 mock
  http.post('/api/auth/oauth2/:provider', async ({ params }) => {
    await delay(500);
    const provider = params.provider as string;
    const id = `oauth_${provider}_${Date.now().toString(36)}`;
    const user = {
      id,
      email: `demo@${provider}.com`,
      nickname: `${provider}사용자`,
      isGuest: false,
      onboardingCompletedAt: null,
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json({
      user,
      token: { accessToken: `mock-token-${id}`, expiresIn: 86400 },
    });
  }),

  http.post('/api/auth/guest', async ({ request }) => {
    await delay(200);
    const body: any = await request.json().catch(() => ({}));
    const inviteToken: string | undefined = body?.inviteToken;

    let guestId: string;
    if (inviteToken) {
      // 같은 초대 URL은 동일한 Guest ID 반환
      if (!GUEST_SESSIONS.has(inviteToken)) {
        GUEST_SESSIONS.set(inviteToken, makeGuestId());
      }
      guestId = GUEST_SESSIONS.get(inviteToken)!;
    } else {
      guestId = makeGuestId();
    }

    return HttpResponse.json({
      user: {
        id: guestId,
        nickname: body?.nickname?.trim() || generateGuestNickname(),
        isGuest: true,
          createdAt: new Date().toISOString(),
      },
      token: { accessToken: `mock-guest-token-${guestId}`, expiresIn: 7200 },
    });
  }),

  http.get('/api/users/me/history', async () => {
    await delay(300);
    return HttpResponse.json(SESSION_HISTORY);
  }),

  http.get('/api/users/me', async () => {
    await delay(200);
    return HttpResponse.json({
      id: 'user_demo',
      email: 'demo@example.com',
      nickname: '서현',
      isGuest: false,
      communicationStyle: 'wave',
      onboardingCompletedAt: '2026-04-01T10:00:00.000Z',
      createdAt: new Date().toISOString(),
    });
  }),
];
