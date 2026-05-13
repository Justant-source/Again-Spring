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

async function copyToClipboardSafe(text: string): Promise<boolean> {
  // 1) 표준 API (HTTPS + secureContext + 권한 OK일 때만 동작)
  if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // fallthrough — 인앱 브라우저(카톡/인스타 등)에서 막힐 수 있음
    }
  }
  // 2) execCommand fallback (구형 브라우저·인앱 브라우저 호환)
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.top = '0';
    ta.style.left = '-9999px';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

export function InviteModal({ sessionId, onClose }: Props) {
  const [token, setToken] = useState<string | null>(null);
  const [tokenError, setTokenError] = useState<string | null>(null);
  const [toneIdx, setToneIdx] = useState(0);
  const [message, setMessage] = useState(TONES[0].message);
  const [copied, setCopied] = useState(false);
  const [urlCopied, setUrlCopied] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get(`/api/sessions/${sessionId}/invite`)
      .then(r => { if (!cancelled) setToken(r.data.inviteToken); })
      .catch(() =>
        api
          .post(`/api/sessions/${sessionId}/invite`)
          .then(r => { if (!cancelled) setToken(r.data.inviteToken); })
          .catch(e => {
            console.error('Invite failed:', e);
            if (!cancelled) {
              const msg = e?.response?.data?.error?.message
                || e?.response?.data?.message
                || '초대 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.';
              setTokenError(msg);
            }
          })
      );
    return () => { cancelled = true; };
  }, [sessionId]);

  const baseUrl =
    typeof window !== 'undefined' ? window.location.origin : '';
  const shareUrl = token ? `${baseUrl}/session/join/${token}` : '';
  const fullText = `${message}\n\n${shareUrl}`;

  const handleNativeShare = async () => {
    setShareError(null);
    if (!shareUrl) return;
    // 1) 네이티브 공유 시트 (모바일 Safari/Chrome/카카오)
    if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
      try {
        await navigator.share({
          title: '다시봄',
          text: message,
          url: shareUrl,
        });
        return;
      } catch (e: any) {
        // 사용자가 취소한 경우는 무시
        if (e?.name === 'AbortError') return;
        // 그 외에는 클립보드 폴백으로
      }
    }
    // 2) 클립보드 복사 폴백
    const ok = await copyToClipboardSafe(fullText);
    if (ok) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } else {
      setShareError('공유가 안 돼요. 아래 URL을 길게 눌러 직접 복사해주세요.');
    }
  };

  const handleCopyUrlOnly = async () => {
    setShareError(null);
    if (!shareUrl) return;
    const ok = await copyToClipboardSafe(shareUrl);
    if (ok) {
      setUrlCopied(true);
      setTimeout(() => setUrlCopied(false), 2000);
    } else {
      setShareError('복사가 안 돼요. URL을 길게 눌러 직접 복사해주세요.');
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
            display: 'flex',
            alignItems: 'stretch',
            gap: 8,
            marginBottom: 16,
          }}
        >
          <div
            onClick={(e) => {
              // 사용자가 직접 길게 눌러 복사할 수 있도록 자동 선택
              const range = document.createRange();
              range.selectNodeContents(e.currentTarget);
              const sel = window.getSelection();
              sel?.removeAllRanges();
              sel?.addRange(range);
            }}
            style={{
              flex: 1,
              padding: '10px 12px',
              background: 'var(--P-card)',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 11,
              color: 'var(--P-sub)',
              wordBreak: 'break-all',
              display: 'flex',
              alignItems: 'center',
              userSelect: 'all',
              WebkitUserSelect: 'all',
              cursor: 'text',
            }}
          >
            {shareUrl || (tokenError ? '링크 생성 실패' : '링크 생성 중...')}
          </div>
          <button
            onClick={handleCopyUrlOnly}
            disabled={!token}
            aria-label="초대 링크 복사"
            style={{
              flexShrink: 0,
              padding: '0 14px',
              background: urlCopied ? 'var(--P-ink)' : 'var(--P-card)',
              color: urlCopied ? 'var(--P-bg)' : 'var(--P-ink)',
              border: `1px solid ${urlCopied ? 'var(--P-ink)' : 'var(--P-border)'}`,
              borderRadius: 8,
              fontSize: 12,
              fontWeight: 500,
              cursor: token ? 'pointer' : 'not-allowed',
              opacity: token ? 1 : 0.4,
              whiteSpace: 'nowrap',
              transition: 'all 0.15s',
            }}
          >
            {urlCopied ? '복사됨 ✓' : 'URL 복사'}
          </button>
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
            cursor: token ? 'pointer' : 'not-allowed',
            opacity: token ? 1 : 0.4,
          }}
        >
          {copied ? '메시지+링크 복사됐어요' : '카톡으로 공유하기'}
        </button>

        {/* 토큰 발급 실패 / 공유 실패 안내 */}
        {(tokenError || shareError) && (
          <div
            style={{
              marginTop: 10,
              padding: '10px 12px',
              background: '#FFF3F0',
              border: '1px solid #F5C0B0',
              borderRadius: 8,
              fontSize: 12,
              color: '#8A2A10',
              lineHeight: 1.5,
            }}
          >
            {tokenError || shareError}
          </div>
        )}

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
