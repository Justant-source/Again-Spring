'use client';

import { useState } from 'react';

interface CommunityCommentProps {
  nick: string;
  isAuthor: boolean;
  isPartner: boolean;
  time: string;
  text: string;
  likeCount: number;
  isLiked?: boolean;
  isReply?: boolean;
  onLike?: () => void;
  onReply?: () => void;
  onReport?: () => void;
}

function ThumbIcon({ color, filled }: { color: string; filled?: boolean }) {
  return (
    <svg width={15} height={15} viewBox="0 0 24 24" fill={filled ? color : 'none'} stroke={color} strokeWidth="1.6">
      <path d="M7 11v9M3.5 12.5V19a1 1 0 001 1H17a2 2 0 002-1.7l1.1-6.5a1.4 1.4 0 00-1.4-1.6h-5.2l.8-3.9a1.6 1.6 0 00-3-.9L7 11" strokeLinejoin="round" strokeLinecap="round"/>
    </svg>
  );
}

function BubbleIcon({ color }: { color: string }) {
  return (
    <svg width={15} height={15} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.6">
      <path d="M21 12a8 7 0 01-11.3 6.4L4 20l1.6-4.2A8 7 0 1121 12z" strokeLinejoin="round" strokeLinecap="round"/>
    </svg>
  );
}

export function CommunityComment({
  nick,
  isAuthor,
  isPartner,
  time,
  text,
  likeCount,
  isLiked = false,
  isReply = false,
  onLike,
  onReply,
  onReport,
}: CommunityCommentProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  const sub = 'var(--L-sub)';
  const ink = 'var(--L-ink)';
  const nickColor = isAuthor ? 'var(--faction-author)' : isPartner ? 'var(--faction-partner)' : sub;
  const hasStar = isAuthor || isPartner;
  const likeColor = isLiked ? 'var(--faction-author)' : sub;

  return (
    <div
      style={{
        position: 'relative',
        background: isReply ? '#EDE6D8' : 'transparent',
        borderTop: isReply ? 'none' : '1px solid var(--L-border)',
        padding: isReply ? '12px 20px 12px 36px' : '13px 20px',
        marginLeft: -20,
        marginRight: -20,
      }}
    >
      {/* 닉네임 + 작성자 * 표식 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <span style={{ fontSize: 12.5, color: nickColor, fontWeight: 500, letterSpacing: '0.02em' }}>{nick}</span>
        {hasStar && (
          <span style={{ fontSize: 13, color: nickColor, fontWeight: 700, marginLeft: 3 }}>*</span>
        )}
      </div>

      {/* 본문 */}
      <div style={{ fontSize: 14, color: ink, lineHeight: 1.55, marginTop: 6, whiteSpace: 'pre-line' }}>
        {text}
      </div>

      {/* 메타 행: 시간 · 👍 좋아요 N · 💬 대댓글 · ⋯ */}
      <div style={{ display: 'flex', alignItems: 'center', marginTop: 9 }}>
        <span style={{ fontSize: 12, color: sub }}>{time}</span>
        <span style={{ fontSize: 12, color: sub, margin: '0 8px' }}>·</span>
        <button
          onClick={onLike}
          style={{
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 5,
            fontSize: 12,
            color: likeColor,
            fontFamily: 'inherit',
          }}
        >
          <ThumbIcon color={likeColor} filled={isLiked} /> 좋아요 {likeCount}
        </button>
        {!isReply && (
          <>
            <span style={{ fontSize: 12, color: sub, margin: '0 8px' }}>·</span>
            <button
              onClick={onReply}
              style={{
                background: 'none',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                fontSize: 12,
                color: sub,
                fontFamily: 'inherit',
              }}
            >
              <BubbleIcon color={sub} /> 대댓글
            </button>
          </>
        )}
        <button
          onClick={() => setMenuOpen((o) => !o)}
          style={{
            marginLeft: 'auto',
            background: 'none',
            border: 'none',
            padding: '0 0 0 8px',
            cursor: 'pointer',
            fontSize: 15,
            color: sub,
            letterSpacing: 1,
            fontFamily: 'inherit',
          }}
        >
          ⋯
        </button>
      </div>

      {/* ⋯ 드롭다운 */}
      {menuOpen && (
        <>
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 49 }}
            onClick={() => setMenuOpen(false)}
          />
          <div
            style={{
              position: 'absolute',
              right: 12,
              top: 8,
              background: 'var(--L-bg)',
              border: '1px solid var(--L-border)',
              borderRadius: 8,
              boxShadow: '0 4px 12px rgba(0,0,0,0.10)',
              zIndex: 50,
              minWidth: 80,
              overflow: 'hidden',
            }}
          >
            <button
              onClick={() => {
                setMenuOpen(false);
                onReport?.();
              }}
              style={{
                display: 'block',
                width: '100%',
                padding: '11px 16px',
                background: 'none',
                border: 'none',
                textAlign: 'left',
                fontSize: 13,
                color: 'var(--faction-partner)',
                cursor: 'pointer',
                fontFamily: 'inherit',
              }}
            >
              신고
            </button>
          </div>
        </>
      )}
    </div>
  );
}
