import { http, HttpResponse } from 'msw';

export const communityHandlers = [
  http.get('/api/community/posts', ({ request }) => {
    const url = new URL(request.url);
    const sort = url.searchParams.get('sort') || 'latest';

    return HttpResponse.json({
      content: [
        {
          id: 'post_test001',
          title: '주말 약속 갈등',
          category: 'couple',
          visibility: 'PUBLIC',
          status: 'VOTING',
          voteCount: 15,
          createdAt: new Date().toISOString(),
          userTitle: '외갓집 가느냐 마느냐',
          authorPct: 70,
          partnerPct: 30,
          paired: true,
        },
      ],
      totalElements: 1,
      totalPages: 1,
    });
  }),

  http.get('/api/community/posts/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      title: '주말 약속 갈등',
      bodyPublished: '두 분이 주말 계획에 대해 의견 차이가 있습니다. A님은 가족과 시간을 보내고 싶어하고, B님은 둘만의 시간을 원합니다.',
      category: 'couple',
      visibility: 'PUBLIC',
      status: 'VOTING',
      voteOptions: [
        { id: 1, label: 'A님 입장이 더 이해됩니다', orderIdx: 0 },
        { id: 2, label: 'B님 입장이 더 이해됩니다', orderIdx: 1 },
        { id: 3, label: '서로 오해가 있어 보입니다', orderIdx: 2 },
      ],
      createdAt: new Date().toISOString(),
      isVoted: false,
      userTitle: '외갓집 가느냐 마느냐',
      authorPct: 70,
      partnerPct: 30,
      paired: true,
      isAuthor: false,
      hasVoted: false,
    });
  }),

  http.post('/api/community/posts', () => {
    return HttpResponse.json({
      id: 'post_newtest001',
      title: '새 사연',
      bodyPublished: '중립화된 본문입니다.',
      category: 'couple',
      visibility: 'PRIVATE',
      status: 'VOTING',
      voteOptions: [
        { id: 1, label: 'A님 입장이 더 이해됩니다', orderIdx: 0 },
        { id: 2, label: 'B님 입장이 더 이해됩니다', orderIdx: 1 },
        { id: 3, label: '서로 오해가 있어 보입니다', orderIdx: 2 },
      ],
      createdAt: new Date().toISOString(),
    });
  }),

  http.post('/api/community/posts/:id/vote', () => {
    return HttpResponse.json({
      options: [
        { id: 1, label: 'A님 입장이 더 이해됩니다', count: 8, percentage: 53 },
        { id: 2, label: 'B님 입장이 더 이해됩니다', count: 5, percentage: 33 },
        { id: 3, label: '서로 오해가 있어 보입니다', count: 2, percentage: 14 },
      ],
      totalVotes: 15,
      myVotedOptionId: 1,
    });
  }),

  http.post('/api/community/posts/:id/invite', ({ params }) => {
    return HttpResponse.json({
      inviteToken: 'tok_' + Math.random().toString(36).substr(2, 9),
      inviteUrl: 'https://example.com/s/tok_' + Math.random().toString(36).substr(2, 9),
    });
  }),

  http.get('/api/s/:token', ({ params }) => {
    return HttpResponse.json({
      postId: 'post_test001',
      userTitle: '외갓집 가느냐 마느냐',
      authorBodyPublished: 'A님의 입장 설명입니다.',
      category: 'couple',
    });
  }),

  http.post('/api/s/:token/answer', () => {
    return HttpResponse.json({ success: true });
  }),

  http.patch('/api/community/posts/:id/publish-mode', () => {
    return HttpResponse.json({ success: true });
  }),

  http.post('/api/community/posts/:id/publish-now', () => {
    return HttpResponse.json({ success: true });
  }),

  http.get('/api/community/posts/:id/comments', () => {
    return HttpResponse.json([
      {
        id: 1,
        authorId: 'user_a',
        body: '공감합니다',
        likeCount: 3,
        isLiked: false,
        createdAt: new Date().toISOString(),
        replies: [],
      },
    ]);
  }),

  http.post('/api/community/posts/:id/comments', () => {
    return HttpResponse.json({
      id: 99,
      authorId: 'current_user',
      body: '댓글 내용',
      likeCount: 0,
      isLiked: false,
      createdAt: new Date().toISOString(),
    });
  }),

  http.post('/api/community/posts/:id/comments/:commentId/like', () => {
    return HttpResponse.json({ liked: true, count: 1 });
  }),

  http.post('/api/community/posts/:id/like', () => {
    return HttpResponse.json({ liked: true, count: 1 });
  }),

  http.post('/api/community/posts/:id/report', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete('/api/community/posts/:id', () => {
    return new HttpResponse(null, { status: 204 });
  }),
];
