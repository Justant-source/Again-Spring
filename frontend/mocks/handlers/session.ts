import { http, HttpResponse, delay } from 'msw';
import { HISTORY_MESSAGES_MOCK } from './historyMessages';

const SESSIONS = new Map<string, any>();

// 히스토리 데모 세션 + 활성 세션 (GET /api/sessions/:id 에서 반환)
const HISTORY_SESSIONS: Record<string, any> = {
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
  // 활성 Solo 세션 — E2E 채팅 테스트용 (completed가 아니므로 채팅 페이지에서 리다이렉트 안 됨)
  sess_active: {
    id: 'sess_active',
    status: 'chatting_solo',
    relationType: 'couple',
    // V47~: category는 majorId만 잔존 (중·소분류 제거)
    category: { majorId: 'couple' },
    title: '연락 빈도 갈등',
    keywords: ['연락 빈도', '서운함'],
    koreanTag: null,
    partnerNickname: '준호',
    soloMode: true,
    myRole: 'A',
    inviteToken: 'tok_active_test',
    createdAt: new Date().toISOString(),
  },
};

// 히스토리 데모 메시지
const HISTORY_MESSAGES: Record<string, any[]> = {
  sess_history_1: [
    { id: 1, sender: 'USER_A', content: '오늘도 제가 혼자 다 치운 것 같아서 속상해요.', charCount: 22, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:05:00.000Z' },
    { id: 2, sender: 'MEDIATOR_TO_A', content: '그 마음이 많이 쌓였겠어요. 혼자 해야 했을 때 어떤 감정이 들었는지 더 들려주실 수 있어요?', charCount: 45, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:05:30.000Z' },
    { id: 3, sender: 'USER_A', content: '무시당하는 느낌이요. 내가 하는 일이 당연하게 여겨지는 것 같아서요.', charCount: 33, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:07:00.000Z' },
    { id: 4, sender: 'MEDIATOR_TO_A', content: '인정받고 싶은 마음이 있으셨군요. 그 부분을 상대방이 어떻게 알아줬으면 했는지 떠올려 보실 수 있어요?', charCount: 51, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:07:30.000Z' },
    { id: 5, sender: 'USER_A', content: '그냥 고마워요, 한 마디면 충분할 것 같아요.', charCount: 21, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:09:00.000Z' },
    { id: 6, sender: 'MEDIATOR_TO_A', content: '작은 말 한마디가 얼마나 큰 위로가 되는지 느껴지네요. 이만큼 이야기 나눠주셔서 고마워요.', charCount: 44, isFinalizeSuggestion: true, isPartnerJoinNotice: false, createdAt: '2026-04-12T08:09:30.000Z' },
  ],
  sess_history_2: [
    { id: 1, sender: 'USER_A', content: '약속을 또 취소했어요. 이번이 세 번째인데 정말 힘드네요.', charCount: 28, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-03-02T11:05:00.000Z' },
    { id: 2, sender: 'MEDIATOR_TO_A', content: '반복되니 더 힘드셨겠어요. 그 상황에서 어떤 생각이 가장 먼저 드셨어요?', charCount: 38, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-03-02T11:05:30.000Z' },
    { id: 3, sender: 'USER_A', content: '나를 중요하게 생각하지 않는구나, 싶었어요.', charCount: 21, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-03-02T11:07:00.000Z' },
    { id: 4, sender: 'MEDIATOR_TO_A', content: '관계에서 소중히 여겨지고 싶은 마음이 있으셨군요. 신뢰를 회복하려면 무엇이 필요할 것 같으세요?', charCount: 47, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-03-02T11:07:30.000Z' },
  ],
  sess_history_3: [
    { id: 1, sender: 'USER_A', content: '엄마가 제 방식을 계속 무시하는 것 같아요.', charCount: 21, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-02-17T17:35:00.000Z' },
    { id: 2, sender: 'MEDIATOR_TO_A', content: '내 방식이 받아들여지지 않는다는 느낌, 어떤 상황에서 가장 강하게 드셨어요?', charCount: 39, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-02-17T17:35:30.000Z' },
    { id: 3, sender: 'USER_A', content: '진로 결정할 때요. 제가 하고 싶은 걸 말했는데 바로 반대하셨어요.', charCount: 33, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-02-17T17:37:00.000Z' },
    { id: 4, sender: 'MEDIATOR_TO_A', content: '본인의 선택을 믿어주길 바라는 마음이 있으셨겠어요. 엄마가 어떻게 반응해줬으면 했는지 말씀해 주실 수 있어요?', charCount: 54, isFinalizeSuggestion: false, isPartnerJoinNotice: false, createdAt: '2026-02-17T17:37:30.000Z' },
  ],
};

export const sessionHandlers = [
  http.post('/api/sessions', async ({ request }) => {
    await delay(600);
    const body: any = await request.json();
    const id = `sess_${Date.now().toString(36)}`;
    const token = `tok_${Math.random().toString(36).slice(2, 10)}`;
    const session = {
      id,
      inviteToken: token,
      status: 'chatting_solo',
      currentTurn: 0,
      turns: [],
      // V47: title/keywords는 비동기 추론 — 초기엔 null
      title: null,
      keywords: null,
      koreanTag: null,
      createdAt: new Date().toISOString(),
      ...body,
    };
    SESSIONS.set(id, session);
    return HttpResponse.json(session);
  }),

  // V47 신규: 세션 제목 수정
  http.patch('/api/sessions/:id/title', async ({ params, request }) => {
    await delay(200);
    const id = String(params.id);
    const body: any = await request.json();
    const s = SESSIONS.get(id) ?? HISTORY_SESSIONS[id];
    if (!s) return HttpResponse.json({ error: 'not_found' }, { status: 404 });
    s.title = body.title;
    s.titleEditedByUser = true;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/sessions/:id', async ({ params }) => {
    await delay(200);
    const id = String(params.id);
    const s = SESSIONS.get(id) ?? HISTORY_SESSIONS[id];
    if (!s) return HttpResponse.json({ error: 'not_found' }, { status: 404 });
    return HttpResponse.json(s);
  }),

  http.delete('/api/sessions/:id', async ({ params }) => {
    await delay(300);
    const id = String(params.id);
    SESSIONS.delete(id);
    delete HISTORY_SESSIONS[id];
    delete HISTORY_MESSAGES_MOCK[id];
    return new HttpResponse(null, { status: 204 });
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
