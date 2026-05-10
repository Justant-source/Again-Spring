'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { permissionsFor } from '@/lib/constants/userPermissions';

type ExpandedDoc = 'terms' | 'privacy' | null;

const DOC_META: Record<Exclude<ExpandedDoc, null>, { title: string; src: string }> = {
  terms: { title: '이용약관 전문', src: '/legal/terms.md' },
  privacy: { title: '개인정보 처리방침 전문', src: '/legal/privacy.md' },
};

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
  const [expanded, setExpanded] = useState<ExpandedDoc>(null);
  const [docContent, setDocContent] = useState('');
  const [docLoading, setDocLoading] = useState(false);
  const [scrolledToBottom, setScrolledToBottom] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) return;
    if (!permissionsFor(user).ui.showConsentReconfirmModal) return;
    if (!user.termsAgreedAt || !user.privacyAgreedAt || !user.disclaimerAgreedAt) {
      setVisible(true);
    }
  }, [user]);

  useEffect(() => {
    if (!visible) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [visible]);

  // 약관 본문 로드
  useEffect(() => {
    if (!expanded) return;
    setDocLoading(true);
    setDocContent('');
    setScrolledToBottom(false);
    fetch(DOC_META[expanded].src)
      .then((r) => r.text())
      .then((t) => { setDocContent(t); setDocLoading(false); })
      .catch(() => { setDocContent('문서를 불러올 수 없어요. 잠시 후 다시 시도해주세요.'); setDocLoading(false); });
  }, [expanded]);

  // 본문 로드 후 짧아서 스크롤이 없으면 즉시 활성화
  useEffect(() => {
    if (docLoading || !expanded) return;
    const el = scrollRef.current;
    if (!el) return;
    requestAnimationFrame(() => {
      if (el.scrollHeight <= el.clientHeight + 4) {
        setScrolledToBottom(true);
      }
    });
  }, [docContent, docLoading, expanded]);

  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 8) {
      setScrolledToBottom(true);
    }
  }, []);

  if (!visible) return null;

  const canSubmit = termsAgreed && privacyAgreed && disclaimerAgreed && !submitting;

  function handleAgreeFromViewer() {
    if (!expanded || !scrolledToBottom) return;
    if (expanded === 'terms') setTermsAgreed(true);
    if (expanded === 'privacy') setPrivacyAgreed(true);
    setExpanded(null);
  }

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

  // 전문 보기 모드
  if (expanded) {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="consent-doc-title"
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
            maxWidth: 420, width: '100%',
            maxHeight: '85vh',
            display: 'flex', flexDirection: 'column',
            boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
          }}
        >
          {/* 헤더: 제목 + 우측 상단 X 버튼 */}
          <div
            style={{
              padding: '18px 20px 14px 22px',
              borderBottom: '1px solid #eee',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              flexShrink: 0,
            }}
          >
            <div id="consent-doc-title" style={{ fontSize: 15, fontWeight: 600, color: '#111' }}>
              {DOC_META[expanded].title}
            </div>
            <button
              onClick={() => setExpanded(null)}
              aria-label="닫기"
              style={{
                background: 'none', border: 'none', padding: 4,
                fontSize: 20, lineHeight: 1, color: '#888', cursor: 'pointer',
              }}
            >
              ×
            </button>
          </div>

          {/* 본문 (스크롤) */}
          <div
            ref={scrollRef}
            onScroll={handleScroll}
            style={{
              flex: 1, overflowY: 'auto',
              padding: '18px 22px',
              fontSize: 13, lineHeight: 1.7, color: '#333',
              overflowWrap: 'break-word',
            }}
          >
            {docLoading ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#888' }}>불러오는 중…</div>
            ) : (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  h1: ({ children }) => <h1 style={{ fontSize: 16, fontWeight: 600, marginTop: 22, marginBottom: 8 }}>{children}</h1>,
                  h2: ({ children }) => <h2 style={{ fontSize: 14, fontWeight: 600, marginTop: 18, marginBottom: 6 }}>{children}</h2>,
                  h3: ({ children }) => <h3 style={{ fontSize: 13, fontWeight: 600, marginTop: 14, marginBottom: 5 }}>{children}</h3>,
                  p: ({ children }) => <p style={{ marginBottom: 8 }}>{children}</p>,
                  ul: ({ children }) => <ul style={{ paddingLeft: 18, marginBottom: 8 }}>{children}</ul>,
                  ol: ({ children }) => <ol style={{ paddingLeft: 18, marginBottom: 8 }}>{children}</ol>,
                  li: ({ children }) => <li style={{ marginBottom: 3 }}>{children}</li>,
                  hr: () => <hr style={{ margin: '16px 0', border: 'none', borderTop: '1px solid #eee' }} />,
                  strong: ({ children }) => <strong style={{ fontWeight: 600 }}>{children}</strong>,
                  table: ({ children }) => (
                    <div style={{ overflowX: 'auto', marginBottom: 12 }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>{children}</table>
                    </div>
                  ),
                  th: ({ children }) => <th style={{ textAlign: 'left', padding: '5px 8px', background: '#f6f6f6', borderBottom: '1px solid #eee', fontWeight: 600 }}>{children}</th>,
                  td: ({ children }) => <td style={{ padding: '5px 8px', borderBottom: '1px solid #eee' }}>{children}</td>,
                }}
              >
                {docContent}
              </ReactMarkdown>
            )}
          </div>

          {/* 푸터: 동의 버튼 (스크롤 끝까지 도달해야 활성화) */}
          <div
            style={{
              padding: '14px 22px 18px',
              borderTop: '1px solid #eee',
              flexShrink: 0,
            }}
          >
            {!scrolledToBottom && !docLoading && (
              <p style={{ fontSize: 11, color: '#888', textAlign: 'center', margin: '0 0 8px' }}>
                끝까지 읽어주시면 동의 버튼이 활성화됩니다.
              </p>
            )}
            <button
              onClick={handleAgreeFromViewer}
              disabled={!scrolledToBottom || docLoading}
              style={{
                width: '100%', padding: 12, borderRadius: 10,
                background: scrolledToBottom && !docLoading ? '#1A1A2E' : '#ccc',
                color: 'white',
                fontSize: 14, fontWeight: 600, border: 'none',
                cursor: scrolledToBottom && !docLoading ? 'pointer' : 'not-allowed',
              }}
            >
              동의
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 기본 동의 화면
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
          {/* 약관 항목: 체크박스는 보기만, "전문"으로 본문 열어 끝까지 읽고 동의해야 체크됨 */}
          <DocReconfirmRow
            checked={termsAgreed}
            label="이용약관"
            onExpand={() => setExpanded('terms')}
          />
          <DocReconfirmRow
            checked={privacyAgreed}
            label="개인정보 처리방침"
            onExpand={() => setExpanded('privacy')}
          />
          {/* 본문 없는 항목: 체크박스 직접 클릭 가능 */}
          <SimpleReconfirmRow
            checked={disclaimerAgreed}
            onChange={setDisclaimerAgreed}
            label="전문 상담·치료를 대체하지 않음을 이해합니다"
            required
          />
          <SimpleReconfirmRow
            checked={marketingAgreed}
            onChange={setMarketingAgreed}
            label="마케팅 정보 수신 동의"
          />
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

/** 체크박스 비활성 (읽기 전용) — 전문 보기 후 동의 시에만 체크됨 */
function DocReconfirmRow({
  checked, label, onExpand,
}: {
  checked: boolean;
  label: string;
  onExpand: () => void;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#333' }}>
      <input
        type="checkbox"
        checked={checked}
        readOnly
        disabled
        aria-readonly="true"
        style={{ width: 15, height: 15, flexShrink: 0, cursor: 'not-allowed', opacity: checked ? 1 : 0.6 }}
      />
      <span style={{ flex: 1, color: '#333' }}>
        <span style={{ color: '#e55', marginRight: 3 }}>*</span>
        {label}
      </span>
      <button
        type="button"
        onClick={onExpand}
        style={{
          fontSize: 11, color: checked ? '#888' : '#1A1A2E',
          fontWeight: checked ? 400 : 600,
          textDecoration: 'underline',
          flexShrink: 0, background: 'none', border: 'none',
          padding: 0, cursor: 'pointer',
        }}
      >
        {checked ? '전문' : '전문 읽고 동의'}
      </button>
    </div>
  );
}

/** 일반 체크박스 (직접 클릭 가능) */
function SimpleReconfirmRow({
  checked, onChange, label, required,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  required?: boolean;
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
    </label>
  );
}
