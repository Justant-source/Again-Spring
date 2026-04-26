'use client';

interface Props {
  message: { id: number; content: string; createdAt: string };
}

export function PartnerJoinNoticeCard({ message }: Props) {
  return (
    <div
      style={{
        margin: '12px 8px',
        padding: '12px 16px',
        background: 'var(--P-card)',
        border: '1px dashed var(--P-border)',
        borderRadius: 10,
        textAlign: 'center',
        fontSize: 12,
        color: 'var(--P-sub)',
        lineHeight: 1.7,
      }}
    >
      {message.content}
    </div>
  );
}
