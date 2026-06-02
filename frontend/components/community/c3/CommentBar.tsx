'use client';

interface CommentBarProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  replyTo?: string;
  onFocus?: () => void;
}

export function CommentBar({ value, onChange, onSubmit, replyTo, onFocus }: CommentBarProps) {
  return (
    <div
      style={{
        position: 'fixed',
        bottom: 74,
        left: 0,
        right: 0,
        zIndex: 100,
        background: 'white',
        borderTop: '1px solid var(--P-border)',
        padding: '10px 16px max(10px, env(safe-area-inset-bottom, 0px))',
      }}
    >
      {/* 답글 대상 표시 */}
      {replyTo && (
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8 }}>
          ↩ @{replyTo} 에게 답글 중
        </div>
      )}

      {/* 입력 컨테이너 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {/* 입력 필드 */}
        <input
          type="text"
          placeholder="댓글 달기…"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={onFocus}
          style={{
            flex: 1,
            padding: '10px 12px',
            border: '1px solid var(--P-border)',
            borderRadius: 6,
            fontSize: 13,
            fontFamily: 'var(--font-sans)',
            outline: 'none',
            transition: 'border-color 0.15s',
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLInputElement).style.borderColor = 'var(--P-sub)';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLInputElement).style.borderColor = 'var(--P-border)';
          }}
        />

        {/* 전송 버튼 (원형) */}
        <button
          type="button"
          onClick={onSubmit}
          style={{
            width: 32,
            height: 32,
            borderRadius: '50%',
            background: 'var(--P-ink)',
            border: 'none',
            color: 'white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            fontSize: 14,
            fontWeight: 600,
            flexShrink: 0,
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLButtonElement).style.opacity = '0.85';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLButtonElement).style.opacity = '1';
          }}
        >
          ↑
        </button>
      </div>
    </div>
  );
}
