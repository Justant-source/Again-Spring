'use client';

import { useState, useEffect } from 'react';
import { submitFeedback } from '@/lib/api/feedbacks';
import { useUiStore } from '@/lib/store/uiStore';

const CATEGORIES = [
  { value: 'ui_bug', label: '화면/버그 문제' },
  { value: 'feature', label: '기능 제안' },
  { value: 'content', label: '내용 관련' },
  { value: 'praise', label: '칭찬/좋았어요' },
  { value: 'crisis', label: '위기 관련 제보' },
  { value: 'other', label: '기타' },
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
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '16px',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'white',
          borderRadius: '16px',
          padding: '28px 24px',
          maxWidth: '380px',
          width: '100%',
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
          maxHeight: '90vh',
          overflowY: 'auto',
        }}
      >
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
    <div style={{ textAlign: 'center', padding: '8px 0' }}>
      <div style={{ fontSize: '32px', marginBottom: '12px' }}>감사해요</div>
      <p style={{ fontSize: '15px', color: '#333', marginBottom: '8px', fontWeight: 600 }}>
        의견이 전달되었어요
      </p>
      <p style={{ fontSize: '14px', color: '#666', lineHeight: 1.6, marginBottom: '24px' }}>
        소중한 의견으로 다시봄이 더 나아질게요.
      </p>
      <button
        onClick={onClose}
        style={{
          width: '100%',
          padding: '14px',
          borderRadius: '10px',
          background: '#1A1A2E',
          color: 'white',
          fontSize: '15px',
          fontWeight: 600,
          border: 'none',
          cursor: 'pointer',
        }}
      >
        닫기
      </button>
    </div>
  );
}

interface FormViewProps {
  category: string;
  content: string;
  contactConsent: boolean;
  contactEmail: string;
  contentTooShort: boolean;
  canSubmit: boolean;
  submitting: boolean;
  error: string;
  onCategoryChange: (v: string) => void;
  onContentChange: (v: string) => void;
  onContactConsentChange: (v: boolean) => void;
  onContactEmailChange: (v: string) => void;
  onSubmit: () => void;
  onClose: () => void;
}

function FormView({
  category, content, contactConsent, contactEmail,
  contentTooShort, canSubmit, submitting, error,
  onCategoryChange, onContentChange, onContactConsentChange, onContactEmailChange,
  onSubmit, onClose,
}: FormViewProps) {
  return (
    <>
      <div
        id="feedback-modal-title"
        style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', color: '#111' }}
      >
        의견 보내기
      </div>

      <div style={{ marginBottom: '16px' }}>
        <label style={{ fontSize: '13px', color: '#555', display: 'block', marginBottom: '6px' }}>
          유형
        </label>
        <select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          style={{
            width: '100%',
            padding: '10px 12px',
            borderRadius: '8px',
            border: '1px solid #ddd',
            fontSize: '14px',
            color: category ? '#111' : '#999',
            background: 'white',
            appearance: 'none',
          }}
        >
          <option value="">선택해주세요</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </select>
      </div>

      <div style={{ marginBottom: '16px' }}>
        <label style={{ fontSize: '13px', color: '#555', display: 'block', marginBottom: '6px' }}>
          내용 <span style={{ color: '#999', fontWeight: 400 }}>(10자 이상)</span>
        </label>
        <textarea
          value={content}
          onChange={(e) => onContentChange(e.target.value)}
          placeholder="자유롭게 작성해주세요"
          rows={4}
          style={{
            width: '100%',
            padding: '10px 12px',
            borderRadius: '8px',
            border: `1px solid ${contentTooShort ? '#e55' : '#ddd'}`,
            fontSize: '14px',
            resize: 'none',
            boxSizing: 'border-box',
          }}
        />
        {contentTooShort && (
          <p style={{ fontSize: '12px', color: '#e55', marginTop: '4px' }}>10자 이상 입력해주세요.</p>
        )}
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', cursor: 'pointer' }}>
        <input
          type="checkbox"
          checked={contactConsent}
          onChange={(e) => onContactConsentChange(e.target.checked)}
        />
        <span style={{ fontSize: '13px', color: '#555' }}>답변을 받고 싶어요 (선택)</span>
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
              padding: '10px 12px',
              borderRadius: '8px',
              border: '1px solid #ddd',
              fontSize: '14px',
              boxSizing: 'border-box',
            }}
          />
        </div>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#e55', marginBottom: '12px' }}>{error}</p>
      )}

      <button
        onClick={onSubmit}
        disabled={!canSubmit}
        style={{
          display: 'block',
          width: '100%',
          padding: '14px',
          borderRadius: '10px',
          background: canSubmit ? '#1A1A2E' : '#ccc',
          color: 'white',
          fontSize: '15px',
          fontWeight: 600,
          border: 'none',
          cursor: canSubmit ? 'pointer' : 'not-allowed',
          marginBottom: '10px',
        }}
      >
        {submitting ? '전송 중...' : '보내기'}
      </button>

      <button
        onClick={onClose}
        style={{
          display: 'block',
          width: '100%',
          padding: '12px',
          borderRadius: '10px',
          background: 'transparent',
          color: '#666',
          fontSize: '14px',
          border: '1px solid #ddd',
          cursor: 'pointer',
        }}
      >
        닫기
      </button>
    </>
  );
}
