'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';

interface PartnerStatus {
  joined: boolean;
  isActive: boolean;
  inviteSent: boolean;
  messageCount: number;
  lastActivityAt: string | null;
}

interface Props {
  sessionId: string;
  myRole: 'USER_A' | 'USER_B';
}

export function PartnerStatusBar({ sessionId, myRole }: Props) {
  const [status, setStatus] = useState<PartnerStatus>({
    joined: false,
    isActive: false,
    inviteSent: false,
    messageCount: 0,
    lastActivityAt: null,
  });

  const fetch = async () => {
    try {
      const r = await api.get(`/api/sessions/${sessionId}/partner-status`);
      setStatus(r.data);
    } catch (e) {
      console.debug('Partner status poll error:', e);
    }
  };

  useEffect(() => {
    fetch();
  }, [sessionId]);

  usePolling(fetch, 4000);

  if (!status.joined) return null;

  const elapsed = status.lastActivityAt
    ? formatElapsed(new Date(status.lastActivityAt))
    : null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        padding: '6px 16px',
        background: 'var(--P-card)',
        borderBottom: '1px solid var(--P-border)',
        fontSize: 11,
        color: 'var(--P-sub)',
        textAlign: 'center',
        zIndex: 100,
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        gap: 6,
      }}
    >
      <div
        style={{
          width: 6,
          height: 6,
          borderRadius: 3,
          background: status.isActive ? 'var(--P-b)' : 'var(--P-sub)',
          opacity: status.isActive ? 1 : 0.4,
        }}
      />
      <span>
        상대 · {status.messageCount}번째 메시지
        {elapsed && ` · ${elapsed}`}
      </span>
    </div>
  );
}

function formatElapsed(time: Date): string {
  const seconds = Math.floor((Date.now() - time.getTime()) / 1000);
  if (seconds < 60) return '방금 전';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전`;
  return `${Math.floor(seconds / 3600)}시간 전`;
}
