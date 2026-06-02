'use client';

interface CommunityCommentProps {
  nick: string;
  isAuthor: boolean;
  isPartner: boolean;
  time: string;
  text: string;
  likeCount: number;
  isReply?: boolean;
}

export function CommunityComment({
  nick,
  isAuthor,
  isPartner,
  time,
  text,
  likeCount,
  isReply = false,
}: CommunityCommentProps) {
  let nickColor = 'var(--L-ink)';
  let nickPrefix = '';

  if (isAuthor) {
    nickColor = 'var(--grn)';
    nickPrefix = '* ';
  } else if (isPartner) {
    nickColor = 'var(--red)';
    nickPrefix = '* ';
  }

  return (
    <div
      style={{
        paddingLeft: isReply ? 20 : 0,
        marginBottom: 12,
      }}
    >
      {/* 헤더: 닉네임 + 시간 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        {isReply && <span style={{ color: 'var(--L-sub)', fontSize: 12 }}>↳</span>}
        <span
          style={{
            fontSize: 13,
            fontWeight: 600,
            color: nickColor,
          }}
        >
          {nickPrefix}{nick}
        </span>
        <span style={{ fontSize: 11, color: 'var(--L-sub)' }}>{time}</span>
      </div>

      {/* 본문 */}
      <p
        style={{
          margin: '0 0 6px 0',
          fontSize: 13,
          lineHeight: 1.6,
          color: 'var(--L-ink)',
        }}
      >
        {text}
      </p>

      {/* 하단: 좋아요 */}
      <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>
        좋아요 {likeCount}
      </div>
    </div>
  );
}
