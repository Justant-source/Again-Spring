'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api/client';

const TONES = [
  {
    key: '부드럽게',
    message: `우리 얘기 좀 정리해보고 싶어서\n다시봄에 내 마음을 적고 있어.\n너도 같이 한 번 해볼래?`,
  },
  {
    key: '가볍게',
    message: `요즘 마음 정리하는 도구 써보고 있어.\n혼자서도 쓰는데, 너도 같이 하면\n서로 마음 알아갈 수 있대.`,
  },
  {
    key: '진지하게',
    message: `우리 사이에 쌓인 마음을\n각자 차분히 정리해보고 싶어.\n같이 해줄 수 있을까?`,
  },
];

interface Props {
  sessionId: string;
  onClose: () => void;
}

export function InviteModal({ sessionId, onClose }: Props) {
  const [token, setToken] = useState<string | null>(null);
  const [toneIdx, setToneIdx] = useState(0);
  const [message, setMessage] = useState(TONES[0].message);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api
      .post(`/api/sessions/${sessionId}/invite`)
      .then(r => setToken(r.data.inviteToken))
      .catch(e => console.error('Invite failed:', e));
  }, [sessionId]);

  const baseUrl =
    typeof window !== 'undefined' ? window.location.origin : '';
  const shareUrl = token ? `${baseUrl}/session/join/${token}` : '';
  const fullText = `${message}\n\n${shareUrl}`;

  const handleNativeShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: '다시봄',
          text: message,
          url: shareUrl,
        });
      } catch (e) {
        // User cancelled or share failed
      }
    } else {
      await navigator.clipboard.writeText(fullText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex',
        alignItems: 'flex-end',
        zIndex: 999,
      }}
      onClick={onClose}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          width: '100%',
          background: 'var(--P-bg)',
          borderTopLeftRadius: 18,
          borderTopRightRadius: 18,
          padding: '20px 22px 28px',
          maxHeight: '85vh',
          overflowY: 'auto',
        }}
      >
        <div
          style={{
            width: 36,
            height: 4,
            background: 'var(--P-border)',
            borderRadius: 2,
            margin: '0 auto 16px',
          }}
        />

        <div
          className="serif"
          style={{
            fontSize: 18,
            marginBottom: 8,
            color: 'var(--P-ink)',
          }}
        >
          상대도 함께 정리하면
        </div>
        <div
          style={{
            fontSize: 13,
            color: 'var(--P-sub)',
            marginBottom: 20,
            lineHeight: 1.7,
          }}
        >
          상대분이 합류해도 두 분의 대화는 서로 보이지 않아요.
          <br />
          제가 양쪽 마음을 따로 듣고, 균형있게 정리해드려요.
        </div>

        <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
          {TONES.map((t, i) => (
            <button
              key={i}
              onClick={() => {
                setToneIdx(i);
                setMessage(t.message);
              }}
              style={{
                flex: 1,
                padding: '6px 0',
                fontSize: 11,
                background: i === toneIdx ? 'var(--P-ink)' : 'transparent',
                color:
                  i === toneIdx ? 'var(--P-bg)' : 'var(--P-sub)',
                border: `1px solid ${
                  i === toneIdx ? 'var(--P-ink)' : 'var(--P-border)'
                }`,
                borderRadius: 6,
                cursor: 'pointer',
              }}
            >
              {t.key}
            </button>
          ))}
        </div>

        <textarea
          value={message}
          onChange={e => setMessage(e.target.value)}
          rows={4}
          style={{
            width: '100%',
            padding: 14,
            fontSize: 13,
            lineHeight: 1.7,
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 10,
            outline: 'none',
            resize: 'none',
            fontFamily: 'inherit',
            color: 'var(--P-ink)',
            marginBottom: 12,
          }}
        />

        <div
          style={{
            padding: '10px 12px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 11,
            color: 'var(--P-sub)',
            marginBottom: 16,
            wordBreak: 'break-all',
          }}
        >
          {shareUrl || '링크 생성 중...'}
        </div>

        <button
          onClick={handleNativeShare}
          disabled={!token}
          style={{
            width: '100%',
            padding: '14px',
            background: 'var(--P-ink)',
            color: 'var(--P-bg)',
            border: 'none',
            borderRadius: 10,
            fontSize: 14,
            cursor: 'pointer',
            opacity: token ? 1 : 0.4,
          }}
        >
          {copied ? '복사됐어요' : '카톡으로 공유하기'}
        </button>

        <button
          onClick={onClose}
          style={{
            width: '100%',
            padding: '12px',
            marginTop: 8,
            background: 'transparent',
            color: 'var(--P-sub)',
            border: 'none',
            fontSize: 13,
            cursor: 'pointer',
          }}
        >
          나중에 할게요
        </button>
      </div>
    </div>
  );
}
