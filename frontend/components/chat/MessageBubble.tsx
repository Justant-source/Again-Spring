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
  isMine: boolean;
}

export function MessageBubble({ message, isMine }: Props) {
  const time = new Date(message.createdAt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: isMine ? 'flex-end' : 'flex-start',
        marginBottom: 10,
        alignItems: 'flex-end',
        gap: 6,
      }}
    >
      {isMine && (
        <div style={{ fontSize: 10, color: 'var(--P-sub)' }}>{time}</div>
      )}
      <div
        style={{
          maxWidth: '72%',
          padding: '10px 14px',
          borderRadius: 14,
          background: isMine ? 'var(--P-a)' : 'var(--P-card)',
          color: 'var(--P-ink)',
          fontSize: 14,
          lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {message.content}
      </div>
      {!isMine && (
        <div style={{ fontSize: 10, color: 'var(--P-sub)' }}>{time}</div>
      )}
    </div>
  );
}
