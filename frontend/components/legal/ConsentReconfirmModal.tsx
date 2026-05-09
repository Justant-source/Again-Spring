'use client';

import { useState, useEffect } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';

export function ConsentReconfirmModal() {
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const [visible, setVisible] = useState(false);
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [privacyAgreed, setPrivacyAgreed] = useState(false);
  const [disclaimerAgreed, setDisclaimerAgreed] = useState(false);
  const [marketingAgreed, setMarketingAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user || user.isGuest) return;
    // 필수 동의 중 하나라도 null이면 재동의 모달 표시
    if (!user.termsAgreedAt || !user.privacyAgreedAt || !user.disclaimerAgreedAt) {
      setVisible(true);
    }
  }, [user]);

  useEffect(() => {
    if (!visible) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [visible]);

  if (!visible) return null;

  const canSubmit = termsAgreed && privacyAgreed && disclaimerAgreed && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError('');
    try {
      await api.post('/api/auth/agree', {
        termsAgreed,
        privacyAgreed,
        disclaimerAgreed,
        marketingAgreed,
      });
      // 로컬 user 상태에도 반영
      if (user) {
        const now = new Date().toISOString();
        setUser({
          ...user,
          termsAgreedAt: now,
          privacyAgreedAt: now,
          disclaimerAgreedAt: now,
          marketingAgreedAt: marketingAgreed ? now : user.marketingAgreedAt,
        });
      }
      setVisible(false);
    } catch {
      setError('동의 처리에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="consent-reconfirm-title"
      style={{
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 10000, padding: 16,
      }}
    >
      <div
        style={{
          background: 'white', borderRadius: 16,
          padding: '28px 24px', maxWidth: 360, width: '100%',
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        <div id="consent-reconfirm-title" style={{ fontSize: 18, fontWeight: 700, marginBottom: 8, color: '#111' }}>
          서비스 이용 동의
        </div>
        <p style={{ fontSize: 13, color: '#555', lineHeight: 1.6, marginBottom: 20 }}>
          다시봄을 더 안전하게 이용하기 위해 아래 항목에 동의해주세요.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 20 }}>
          <ReconfirmRow checked={termsAgreed} onChange={setTermsAgreed} label="이용약관" required href="/terms" />
          <ReconfirmRow checked={privacyAgreed} onChange={setPrivacyAgreed} label="개인정보 처리방침" required href="/privacy" />
          <ReconfirmRow checked={disclaimerAgreed} onChange={setDisclaimerAgreed} label="전문 상담·치료를 대체하지 않음을 이해합니다" required />
          <ReconfirmRow checked={marketingAgreed} onChange={setMarketingAgreed} label="마케팅 정보 수신 동의" />
        </div>

        {error && <p style={{ fontSize: 13, color: '#e55', marginBottom: 12 }}>{error}</p>}

        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          style={{
            width: '100%', padding: 14, borderRadius: 10,
            background: canSubmit ? '#1A1A2E' : '#ccc',
            color: 'white', fontSize: 15, fontWeight: 600,
            border: 'none', cursor: canSubmit ? 'pointer' : 'not-allowed',
          }}
        >
          {submitting ? '처리 중...' : '동의하고 계속하기'}
        </button>
      </div>
    </div>
  );
}

function ReconfirmRow({
  checked, onChange, label, required, href,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  required?: boolean;
  href?: string;
}) {
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, color: '#333' }}>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        style={{ width: 15, height: 15, flexShrink: 0 }}
      />
      <span style={{ flex: 1 }}>
        {required && <span style={{ color: '#e55', marginRight: 3 }}>*</span>}
        {label}
      </span>
      {href && (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          style={{ fontSize: 11, color: '#888', textDecoration: 'underline', flexShrink: 0 }}
          onClick={(e) => e.stopPropagation()}
        >
          전문
        </a>
      )}
    </label>
  );
}
