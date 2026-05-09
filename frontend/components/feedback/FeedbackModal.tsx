'use client';

import { useState, useEffect } from 'react';
import { submitFeedback } from '@/lib/api/feedbacks';
import { useUiStore } from '@/lib/store/uiStore';

const CATEGORIES = [
  { value: 'ui_bug',  label: '화면 / 버그 문제' },
  { value: 'feature', label: '기능 제안' },
  { value: 'content', label: '내용 관련' },
  { value: 'praise',  label: '칭찬 / 좋았어요' },
  { value: 'crisis',  label: '위기 관련 제보' },
  { value: 'other',   label: '기타' },
];

type Step = 'form' | 'done';

export function FeedbackModal() {
  const { feedbackModal, hideFeedbackModal } = useUiStore();
  const [step, setStep] = useState<Step>('form');
  const [category, setCategory] = useState('');
  const [content, setContent] = useState('');
  const [contactConsent, setContactConsent] = useState(false);
  const [contactEmail, setContactEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!feedbackModal) return;
    setStep('form');
    setCategory('');
    setContent('');
    setContactConsent(false);
    setContactEmail('');
    setError('');
  }, [feedbackModal]);

  useEffect(() => {
    if (!feedbackModal) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [feedbackModal]);

  if (!feedbackModal) return null;

  const contentTooShort = content.trim().length > 0 && content.trim().length < 10;
  const canSubmit = !!category && content.trim().length >= 10 && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError('');
    try {
      await submitFeedback({
        category,
        content: content.trim(),
        contactConsent,
        contactEmail: contactConsent ? contactEmail : undefined,
        sessionId: feedbackModal?.sessionId ?? null,
      });
      setStep('done');
    } catch {
      setError('의견 제출에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="feedback-modal-title"
      onClick={hideFeedbackModal}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(43,43,43,0.45)',
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '0',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--L-card)',
          borderRadius: '20px 20px 0 0',
          padding: '28px 24px 36px',
          width: '100%',
          maxWidth: '420px',
          boxShadow: '0 -4px 24px rgba(43,43,43,0.12)',
          maxHeight: '90vh',
          overflowY: 'auto',
        }}
      >
        {/* 드래그 핸들 */}
        <div style={{
          width: 36, height: 4, borderRadius: 2,
          background: 'var(--L-border)', margin: '0 auto 24px',
        }} />

        {step === 'done' ? (
          <DoneView onClose={hideFeedbackModal} />
        ) : (
          <FormView
            category={category}
            content={content}
            contactConsent={contactConsent}
            contactEmail={contactEmail}
            contentTooShort={contentTooShort}
            canSubmit={canSubmit}
            submitting={submitting}
            error={error}
            onCategoryChange={setCategory}
            onContentChange={setContent}
            onContactConsentChange={setContactConsent}
            onContactEmailChange={setContactEmail}
            onSubmit={handleSubmit}
            onClose={hideFeedbackModal}
          />
        )}
      </div>
    </div>
  );
}

function DoneView({ onClose }: { onClose: () => void }) {
  return (
    <div style={{ textAlign: 'center', padding: '16px 0 8px' }}>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--L-ink)', marginBottom: '8px' }}>
        의견이 전달되었어요
      </p>
      <p style={{ fontSize: '13px', color: 'var(--L-sub)', lineHeight: 1.7, marginBottom: '28px' }}>
        소중한 의견으로 다시봄이 더 나아질게요.
      </p>
      <button onClick={onClose} style={primaryBtn}>확인</button>
    </div>
  );
}

interface FormViewProps {
  category: string; content: string; contactConsent: boolean; contactEmail: string;
  contentTooShort: boolean; canSubmit: boolean; submitting: boolean; error: string;
  onCategoryChange: (v: string) => void; onContentChange: (v: string) => void;
  onContactConsentChange: (v: boolean) => void; onContactEmailChange: (v: string) => void;
  onSubmit: () => void; onClose: () => void;
}

function FormView({
  category, content, contactConsent, contactEmail,
  contentTooShort, canSubmit, submitting, error,
  onCategoryChange, onContentChange, onContactConsentChange, onContactEmailChange,
  onSubmit, onClose,
}: FormViewProps) {
  return (
    <>
      <p id="feedback-modal-title" style={{ fontSize: '16px', fontWeight: 700, color: 'var(--L-ink)', marginBottom: '20px' }}>
        의견 보내기
      </p>

      {/* 유형 칩 선택 */}
      <div style={{ marginBottom: '16px' }}>
        <p style={labelStyle}>유형</p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
          {CATEGORIES.map((c) => (
            <button
              key={c.value}
              onClick={() => onCategoryChange(c.value)}
              style={{
                padding: '6px 14px',
                borderRadius: '20px',
                fontSize: '13px',
                border: `1.5px solid ${category === c.value ? 'var(--L-point)' : 'var(--L-border)'}`,
                background: category === c.value ? 'var(--L-point)' : 'transparent',
                color: category === c.value ? '#fff' : 'var(--L-sub)',
                cursor: 'pointer',
                fontWeight: category === c.value ? 600 : 400,
                transition: 'all 0.15s',
              }}
            >
              {c.label}
            </button>
          ))}
        </div>
      </div>

      {/* 내용 */}
      <div style={{ marginBottom: '16px' }}>
        <p style={labelStyle}>
          내용 <span style={{ color: 'var(--L-sub)', fontWeight: 400, fontSize: '12px' }}>10자 이상</span>
        </p>
        <textarea
          value={content}
          onChange={(e) => onContentChange(e.target.value)}
          placeholder="자유롭게 작성해주세요"
          rows={4}
          style={{
            width: '100%',
            padding: '12px',
            borderRadius: '10px',
            border: `1.5px solid ${contentTooShort ? 'var(--L-point)' : 'var(--L-border)'}`,
            background: 'var(--L-bg)',
            fontSize: '14px',
            color: 'var(--L-ink)',
            resize: 'none',
            boxSizing: 'border-box',
            outline: 'none',
            lineHeight: 1.6,
          }}
        />
        {contentTooShort && (
          <p style={{ fontSize: '12px', color: 'var(--L-point)', marginTop: '4px' }}>10자 이상 입력해주세요.</p>
        )}
      </div>

      {/* 답변 동의 */}
      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', cursor: 'pointer' }}>
        <input
          type="checkbox"
          checked={contactConsent}
          onChange={(e) => onContactConsentChange(e.target.checked)}
          style={{ accentColor: 'var(--L-point)', width: 16, height: 16 }}
        />
        <span style={{ fontSize: '13px', color: 'var(--L-sub)' }}>답변을 받고 싶어요 (선택)</span>
      </label>

      {contactConsent && (
        <div style={{ marginBottom: '16px' }}>
          <input
            type="email"
            value={contactEmail}
            onChange={(e) => onContactEmailChange(e.target.value)}
            placeholder="이메일 주소"
            style={{
              width: '100%',
              padding: '12px',
              borderRadius: '10px',
              border: '1.5px solid var(--L-border)',
              background: 'var(--L-bg)',
              fontSize: '14px',
              color: 'var(--L-ink)',
              boxSizing: 'border-box',
              outline: 'none',
            }}
          />
        </div>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: 'var(--L-point)', marginBottom: '12px' }}>{error}</p>
      )}

      <button onClick={onSubmit} disabled={!canSubmit} style={{ ...primaryBtn, opacity: canSubmit ? 1 : 0.4, cursor: canSubmit ? 'pointer' : 'not-allowed', marginBottom: '10px' }}>
        {submitting ? '전송 중...' : '보내기'}
      </button>
      <button onClick={onClose} style={ghostBtn}>닫기</button>
    </>
  );
}

const primaryBtn: React.CSSProperties = {
  display: 'block', width: '100%', padding: '14px',
  borderRadius: '12px', background: 'var(--L-ink)', color: '#fff',
  fontSize: '15px', fontWeight: 600, border: 'none', cursor: 'pointer',
};

const ghostBtn: React.CSSProperties = {
  display: 'block', width: '100%', padding: '13px',
  borderRadius: '12px', background: 'transparent', color: 'var(--L-sub)',
  fontSize: '14px', border: '1.5px solid var(--L-border)', cursor: 'pointer',
};

const labelStyle: React.CSSProperties = {
  fontSize: '13px', color: 'var(--L-sub)', fontWeight: 600,
  marginBottom: '8px',
};
