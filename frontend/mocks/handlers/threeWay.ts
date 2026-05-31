import { http, HttpResponse } from 'msw';

export const threeWayHandlers = [
  http.post('/api/three-way', () => {
    return HttpResponse.json(
      {
        id: 'tws_test001',
        status: 'WAITING',
        inviteToken: 'test-invite-token-123',
        partyAUserId: 'user_a',
        partyBUserId: null,
        category: 'relationship_conflict',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      { status: 201 }
    );
  }),

  http.post('/api/three-way/join/:token', ({ params }) => {
    return HttpResponse.json({
      id: 'tws_test001',
      status: 'ACTIVE',
      inviteToken: params.token as string,
      partyAUserId: 'user_a',
      partyBUserId: 'user_b',
      category: 'relationship_conflict',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
  }),

  http.get('/api/three-way/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      status: 'ACTIVE',
      inviteToken: 'test-invite-token-123',
      partyAUserId: 'user_a',
      partyBUserId: 'user_b',
      category: 'relationship_conflict',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
  }),

  http.get('/api/three-way/:id/messages', ({ params }) => {
    return HttpResponse.json([
      {
        id: 1,
        twsId: params.id,
        authorRole: 'PARTY_A',
        content: '최근에 자주 싸우고 있어요',
        createdAt: new Date(Date.now() - 30000).toISOString(),
        llmModel: null,
      },
      {
        id: 2,
        twsId: params.id,
        authorRole: 'PARTY_B',
        content: '저도 그렇게 느껴요. 어떻게 하면 좋을까요?',
        createdAt: new Date(Date.now() - 20000).toISOString(),
        llmModel: null,
      },
      {
        id: 3,
        twsId: params.id,
        authorRole: 'MEDIATOR',
        content:
          '두 분 다 이 상황을 개선하고 싶으신 것 같네요. 최근에 싸운 구체적인 일들은 어떤 것들이 있었나요?',
        createdAt: new Date(Date.now() - 10000).toISOString(),
        llmModel: 'claude-haiku-4-5-20251001',
      },
    ]);
  }),

  http.post('/api/three-way/:id/messages', ({ params, request }) => {
    // Simulate message sent
    return HttpResponse.json(
      {
        id: 99,
        twsId: params.id,
        authorRole: 'PARTY_A',
        content: '새 메시지',
        createdAt: new Date().toISOString(),
        llmModel: null,
      },
      { status: 201 }
    );
  }),

  http.get('/api/three-way/:id/invite-url', ({ params }) => {
    return HttpResponse.json({
      url: `http://localhost:3000/three-way/join/test-invite-token-123`,
    });
  }),
];
