import { http, HttpResponse, delay } from 'msw';
import { generateGuestNickname } from '@/lib/utils/guestNickname';

const USERS = new Map<string, any>();
const GUEST_SESSIONS = new Map<string, string>(); // inviteToken → guestId

// V47~: title/keywords/koreanTag 추가, middleId/minorId 제거
const SESSION_HISTORY: any[] = [
  {
    id: 'sess_history_1',
    status: 'completed',
    partnerNickname: '준호',
    relationType: 'marriage',
    title: '가사 분담 갈등',
    keywords: ['가사 분담', '누적 불만'],
    koreanTag: null,
    soloMode: false,
    completedAt: '2026-04-12T09:21:00.000Z',
    createdAt: '2026-04-12T08:00:00.000Z',
  },
  {
    id: 'sess_history_2',
    status: 'completed',
    partnerNickname: '지민',
    relationType: 'friend',
    title: '약속 취소 반복',
    keywords: ['약속 취소', '서운함'],
    koreanTag: null,
    soloMode: false,
    completedAt: '2026-03-02T12:05:00.000Z',
    createdAt: '2026-03-02T11:00:00.000Z',
  },
  {
    id: 'sess_history_3',
    status: 'completed',
    partnerNickname: '엄마',
    relationType: 'parent_child',
    title: '진로 결정 갈등',
    keywords: ['진로 결정', '선택 존중'],
    koreanTag: null,
    soloMode: false,
    completedAt: '2026-02-17T18:40:00.000Z',
    createdAt: '2026-02-17T17:30:00.000Z',
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
      // null → ConsentReconfirmModal이 표시되어 dev에서 동의 흐름 테스트 가능
      termsAgreedAt: null,
      privacyAgreedAt: null,
      disclaimerAgreedAt: null,
      marketingAgreedAt: null,
      createdAt: new Date().toISOString(),
    });
  }),

  http.post('/api/auth/agree', async ({ request }) => {
    await delay(300);
    const body: any = await request.json();
    const now = new Date().toISOString();
    return HttpResponse.json({
      termsAgreedAt: body.termsAgreed ? now : null,
      privacyAgreedAt: body.privacyAgreed ? now : null,
      disclaimerAgreedAt: body.disclaimerAgreed ? now : null,
      marketingAgreedAt: body.marketingAgreed ? now : null,
    });
  }),

  http.post('/api/feedbacks', async ({ request }) => {
    await delay(400);
    const body: any = await request.json();
    return HttpResponse.json({
      id: `feedback_${Date.now().toString(36)}`,
      category: body.category,
      status: 'received',
      createdAt: new Date().toISOString(),
    }, { status: 201 });
  }),

  http.post('/api/users/me/onboarding', async ({ request }) => {
    await delay(300);
    const body: any = await request.json();
    const style = body.communicationStyle ?? 'wave';
    return HttpResponse.json({
      communicationStyle: style,
      styleInfo: {
        label: '파도형',
        description: '감정 표현이 풍부하고 즉각적인 스타일',
        strengths: ['진솔한 감정 표현', '따뜻한 공감 능력'],
        caution: ['감정 격앙 시 휴식 필요'],
      },
    });
  }),

  // V47 신규: 중재자 성향 기본값 저장
  http.patch('/api/users/me/mediator-style', async ({ request }) => {
    await delay(200);
    return new HttpResponse(null, { status: 204 });
  }),
];
