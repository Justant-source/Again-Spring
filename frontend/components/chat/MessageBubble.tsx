'use client';

function renderBold(text: string): React.ReactNode {
  const parts = text.split(/(\*\*[^*\n]+\*\*)/g);
  if (parts.length === 1) return text;
  return parts.map((part, i) =>
    part.startsWith('**') && part.endsWith('**')
      ? <strong key={i} style={{ fontWeight: 700 }}>{part.slice(2, -2)}</strong>
      : part
  );
}

interface Message {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
  status?: 'streaming' | 'complete';
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
  const isStreaming = message.status === 'streaming';

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
        {renderBold(message.content)}
        {isStreaming && (
          <span className="streaming-cursor" aria-hidden="true">▍</span>
        )}
      </div>
      {!isMine && (
        <div style={{ fontSize: 10, color: 'var(--P-sub)' }}>{time}</div>
      )}
    </div>
  );
}
