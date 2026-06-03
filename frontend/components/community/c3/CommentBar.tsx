'use client';

interface CommentBarProps {
  replyTo?: string;
  onClick: () => void;
}

export function CommentBar({ replyTo, onClick }: CommentBarProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onClick(); }}
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 100,
        background: 'var(--L-bg)',
        borderTop: '1px solid var(--L-border)',
        padding: 'max(14px, env(safe-area-inset-bottom, 14px)) 20px 14px',
        cursor: 'text',
        userSelect: 'none',
      }}
    >
      <span style={{ fontSize: 14.5, color: 'var(--L-sub)' }}>
        {replyTo ? `↩ @${replyTo} 에게 대댓글` : '댓글을 남겨주세요.'}
      </span>
    </div>
  );
}
