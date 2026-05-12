'use client';

import { useRouter } from 'next/navigation';

interface SoloFailedStateProps {
  sessionId: string;
}

export function SoloFailedState({ sessionId }: SoloFailedStateProps) {
  const router = useRouter();

  return (
    <div
      style={{
        padding: '40px 22px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 16,
        textAlign: 'center',
      }}
    >
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: '50%',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 22,
        }}
      >
        ✦
      </div>
      <div>
        <div
          className="serif"
          style={{ fontSize: 17, color: 'var(--P-ink)', fontWeight: 500, marginBottom: 8 }}
        >
          리포트를 만들지 못했어요
        </div>
        <div style={{ fontSize: 13, color: 'var(--P-sub)', lineHeight: 1.7 }}>
          잠시 후 다시 시도해 주세요.
          <br />
          문제가 반복되면 운영팀에 문의해주세요.
        </div>
      </div>
      <button
        onClick={() => router.push(`/session/chat/${sessionId}`)}
        className="btn-P"
        style={{ marginTop: 8, width: '100%', maxWidth: 260 }}
      >
        채팅으로 돌아가기
      </button>
    </div>
  );
}
