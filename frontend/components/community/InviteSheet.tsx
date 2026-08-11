'use client';

import { useState, useEffect } from 'react';
import { postInviteApi } from '@/lib/api/community/postInviteApi';

interface InviteSheetProps {
  postId: string;
  initialToken?: string | null;
  onClose: () => void;
  onSent: (token: string) => void;
}

export function InviteSheet({ postId, initialToken, onClose, onSent }: InviteSheetProps) {
  const [token, setToken] = useState<string | null>(initialToken || null);
  const [loading, setLoading] = useState(!initialToken);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (initialToken) {
      setToken(initialToken);
      setLoading(false);
      return;
    }

    const createToken = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await postInviteApi.createInvite(postId);
        setToken(response.inviteToken);
        setLoading(false);
      } catch (err) {
        setError('링크 생성에 실패했습니다. 다시 시도해주세요.');
        setLoading(false);
      }
    };

    createToken();
  }, [postId, initialToken]);

  const inviteUrl = token ? `${typeof window !== 'undefined' ? window.location.origin : ''}/s/${token}` : '';

  const copyToClipboard = async () => {
    if (!token) return;
    try {
      await navigator.clipboard.writeText(inviteUrl);
    } catch {
      // fallback for mobile browsers that need user gesture
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    onSent(token);
  };

  const handleCopyUrl = () => copyToClipboard();

  const handleMoreShare = async () => {
    if (!token) return;
    if (navigator.share) {
      try {
        await navigator.share({ url: inviteUrl });
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          await navigator.clipboard.writeText(inviteUrl);
        }
      }
    } else {
      await navigator.clipboard.writeText(inviteUrl);
    }
    onSent(token);
  };

  const handleRetry = () => {
    setLoading(true);
    setError(null);
    const createToken = async () => {
      try {
        const response = await postInviteApi.createInvite(postId);
        setToken(response.inviteToken);
        setLoading(false);
      } catch (err) {
        setError('링크 생성에 실패했습니다. 다시 시도해주세요.');
        setLoading(false);
      }
    };
    createToken();
  };

  return (
    <>
      {/* 오버레이 */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: 400,
          background: 'rgba(0,0,0,0.3)',
        }}
      />
      {/* 바텀시트 */}
      <div
        data-testid="invite-sheet"
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          zIndex: 401,
          maxWidth: 640,
          marginLeft: 'auto',
          marginRight: 'auto',
          background: 'var(--L-bg)',
          borderRadius: '20px 20px 0 0',
          boxShadow: '0 -8px 30px rgba(60,40,20,.12)',
          padding: '20px 24px max(28px, env(safe-area-inset-bottom, 28px))',
        }}
      >
        {/* 드래그 핸들 */}
        <div
          style={{
            width: 36,
            height: 4,
            borderRadius: 2,
            background: 'var(--L-border)',
            margin: '0 auto 20px',
          }}
        />

        {loading ? (
          // 로딩 상태
          <div
            style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              minHeight: 200,
            }}
          >
            <div
              style={{
                width: 40,
                height: 40,
                border: '3px solid var(--L-border)',
                borderTopColor: 'var(--L-ink)',
                borderRadius: '50%',
                animation: 'spin 0.8s linear infinite',
              }}
            />
            <style>{`
              @keyframes spin {
                to { transform: rotate(360deg); }
              }
            `}</style>
          </div>
        ) : error ? (
          // 에러 상태
          <div style={{ textAlign: 'center', paddingTop: 20, paddingBottom: 20 }}>
            <div
              style={{
                fontSize: 15,
                color: '#CC0000',
                fontWeight: 500,
                marginBottom: 20,
              }}
            >
              {error}
            </div>
            <button
              onClick={handleRetry}
              style={{
                padding: '13px 24px',
                borderRadius: 8,
                border: 'none',
                background: 'var(--L-point)',
                color: 'var(--L-bg)',
                fontSize: 14,
                fontWeight: 500,
                cursor: 'pointer',
                fontFamily: 'inherit',
              }}
            >
              다시 시도
            </button>
          </div>
        ) : (
          // 토큰 표시 상태
          <>
            {/* 제목 + 공유 아이콘 버튼 — 재발송(initialToken) vs 신규 초대 */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
              <div
                className="serif"
                style={{ fontSize: 20, lineHeight: 1.45, color: 'var(--L-ink)' }}
              >
                {initialToken ? '같은 링크로 다시 보내세요' : '링크로 상대를 초대하세요'}
              </div>
              <button
                onClick={handleMoreShare}
                aria-label="공유하기"
                style={{
                  flexShrink: 0,
                  width: 40,
                  height: 40,
                  borderRadius: '50%',
                  border: '1px solid var(--L-border)',
                  background: 'var(--L-card)',
                  color: 'var(--L-ink)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginLeft: 12,
                }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8" />
                  <polyline points="16 6 12 2 8 6" />
                  <line x1="12" y1="2" x2="12" y2="15" />
                </svg>
              </button>
            </div>

            {/* URL 박스 */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '13px 14px',
                border: '1px solid var(--L-border)',
                borderRadius: 8,
                background: 'var(--L-card)',
              }}
            >
              <div
                data-testid="invite-url-text"
                style={{
                  flex: 1,
                  fontSize: 13,
                  fontFamily: 'ui-monospace, monospace',
                  overflow: 'hidden',
                  whiteSpace: 'nowrap',
                  textOverflow: 'ellipsis',
                  color: 'var(--L-ink)',
                }}
              >
                {inviteUrl}
              </div>
              <button
                onClick={handleCopyUrl}
                style={{
                  fontSize: 12,
                  color: copied ? 'var(--L-sub)' : 'var(--L-point)',
                  fontWeight: 500,
                  cursor: 'pointer',
                  background: 'none',
                  border: 'none',
                  padding: 0,
                  fontFamily: 'inherit',
                  whiteSpace: 'nowrap',
                  transition: 'color 0.2s',
                }}
              >
                {copied ? '복사됨 ✓' : '복사'}
              </button>
            </div>

            {/* 닫기 버튼 */}
            <button
              onClick={onClose}
              style={{
                marginTop: 14,
                textAlign: 'center',
                fontSize: 13,
                color: 'var(--L-sub)',
                cursor: 'pointer',
                background: 'none',
                border: 'none',
                width: '100%',
                padding: '8px 0',
                fontFamily: 'inherit',
              }}
            >
              닫기
            </button>
          </>
        )}
      </div>
    </>
  );
}
