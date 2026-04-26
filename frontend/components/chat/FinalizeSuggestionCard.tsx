'use client';

interface Message {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

interface Props {
  message: Message;
  onAgree: () => void;
  onDecline: () => void;
  pending?: boolean;
}

export function FinalizeSuggestionCard({ message, onAgree, onDecline, pending }: Props) {
  return (
    <div
      style={{
        margin: '14px 8px',
        padding: '14px 16px',
        background: 'var(--P-card)',
        border: '1px solid var(--P-ink)',
        borderRadius: 12,
        fontSize: 13,
        color: 'var(--P-ink)',
        lineHeight: 1.7,
      }}
    >
      <div style={{ marginBottom: 12 }}>{message.content}</div>
      {pending ? (
        <div
          style={{
            padding: '10px',
            textAlign: 'center',
            fontSize: 12,
            color: 'var(--P-sub)',
            border: '1px solid var(--P-border)',
            borderRadius: 8,
          }}
        >
          상대방의 동의를 기다리고 있어요…
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={onAgree}
            style={{
              flex: 1,
              padding: '10px',
              background: 'var(--P-ink)',
              color: 'var(--P-bg)',
              border: 'none',
              borderRadius: 8,
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            정리하기
          </button>
          <button
            onClick={onDecline}
            style={{
              flex: 1,
              padding: '10px',
              background: 'transparent',
              color: 'var(--P-ink)',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            더 이야기할래요
          </button>
        </div>
      )}
    </div>
  );
}
