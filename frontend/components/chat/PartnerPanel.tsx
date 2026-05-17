'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';

interface PartnerMessageMeta {
  id: number;
  sender: string;
  charCount: number;
  createdAt: string;
}

interface Props {
  sessionId: string;
  myRole: 'USER_A' | 'USER_B';
}

export function PartnerPanel({ sessionId, myRole }: Props) {
  const [messages, setMessages] = useState<PartnerMessageMeta[]>([]);

  const fetch = async () => {
    try {
      const r = await api.get(`/api/sessions/${sessionId}/partner-messages`);
      setMessages(r.data);
    } catch (e) {
      console.debug('Partner poll error:', e);
    }
  };

  useEffect(() => {
    fetch();
  }, [sessionId]);

  usePolling(fetch, 5000);

  const partnerSender = myRole === 'USER_A' ? 'USER_B' : 'USER_A';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        background: 'var(--P-bg)',
      }}
    >
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid var(--P-border)',
          textAlign: 'center',
        }}
      >
        <div style={{ fontSize: 13, color: 'var(--P-ink)', fontWeight: 500 }}>
          상대가 정리 중이에요
        </div>
        <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 4 }}>
          내용은 두 분의 사생활 보호를 위해 가려져 있어요
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 12px' }}>
        {messages.length === 0 && (
          <div
            style={{
              textAlign: 'center',
              color: 'var(--P-sub)',
              fontSize: 13,
              paddingTop: 60,
            }}
          >
            상대분도 곧 시작하실 거예요.
          </div>
        )}
        {messages.map(meta => (
          <BlurredBubble
            key={meta.id}
            isPartner={meta.sender === partnerSender}
            charCount={meta.charCount}
            createdAt={meta.createdAt}
          />
        ))}
      </div>

      <div
        style={{
          padding: '12px 16px',
          borderTop: '1px solid var(--P-border)',
          textAlign: 'center',
          fontSize: 11,
          color: 'var(--P-sub)',
          lineHeight: 1.6,
        }}
      >
        ← 스와이프하면 본인 채팅으로 돌아갈 수 있어요
      </div>
    </div>
  );
}

function BlurredBubble({
  isPartner,
  charCount,
  createdAt,
}: {
  isPartner: boolean;
  charCount: number;
  createdAt: string;
}) {
  const width = Math.min(220, Math.max(60, charCount * 3.5));
  const height = Math.max(36, Math.ceil(charCount / 32) * 22);
  const time = new Date(createdAt).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  return (
    <div
      data-testid="blurred-bubble"
      style={{
        display: 'flex',
        justifyContent: isPartner ? 'flex-start' : 'flex-end',
        marginBottom: 10,
        alignItems: 'flex-end',
        gap: 6,
      }}
    >
      {!isPartner && (
        <div style={{ fontSize: 10, color: 'var(--P-sub)' }}>{time}</div>
      )}
      <div
        style={{
          width,
          height,
          borderRadius: 14,
          background: isPartner ? 'var(--P-card)' : 'var(--P-a)',
          opacity: 0.5,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* 블러 라인 효과 */}
        <div
          style={{
            position: 'absolute',
            inset: '8px 12px',
            background: `repeating-linear-gradient(
              transparent 0,
              transparent 4px,
              rgba(0,0,0,0.06) 4px,
              rgba(0,0,0,0.06) 12px
            )`,
            filter: 'blur(2px)',
          }}
        />
      </div>
      {isPartner && (
        <div style={{ fontSize: 10, color: 'var(--P-sub)' }}>{time}</div>
      )}
    </div>
  );
}
