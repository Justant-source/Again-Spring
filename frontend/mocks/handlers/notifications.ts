import { http, HttpResponse } from 'msw';

export const notificationHandlers = [
  http.get('/api/notifications', () => {
    return HttpResponse.json([
      {
        id: 'notif_001',
        type: 'PARTNER_ANSWERED',
        title: '상대방이 답변했어요',
        subtitle: '당신의 질문에 대한 입장을 정리했습니다',
        refPostId: 'post_test001',
        isRead: false,
        createdAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: 'notif_002',
        type: 'NEW_VOTE',
        title: '새로운 투표가 올라왔어요',
        subtitle: '관계 갈등에 대한 투표입니다',
        refPostId: 'post_test002',
        isRead: false,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
      },
      {
        id: 'notif_003',
        type: 'NEW_COMMENT',
        title: '댓글이 달렸어요',
        subtitle: '당신의 게시물에 새 댓글이 있습니다',
        refPostId: 'post_test003',
        isRead: true,
        createdAt: new Date(Date.now() - 86400000).toISOString(),
      },
    ]);
  }),

  http.post('/api/notifications/read-all', () => {
    return HttpResponse.json({ success: true });
  }),
];
