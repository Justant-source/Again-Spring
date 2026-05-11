'use client';

import { useState, useEffect } from 'react';
import { updateFeedbackStatus } from '@/lib/api/admin';

export interface AdminFeedback {
  id: number;
  userId?: string;
  category: string;
  content: string;
  status: string;
  adminNote?: string;
  contactConsent?: boolean;
  contactEmail?: string;
  pageUrl?: string;
  createdAt?: string;
}

interface Props {
  feedback: AdminFeedback | null;
  onClose: () => void;
  onUpdated: (updated: AdminFeedback) => void;
}

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'pending', label: '대기' },
  { value: 'reviewed', label: '검토 완료' },
  { value: 'resolved', label: '해결됨' },
];

export const CATEGORY_BADGE: Record<string, { label: string; bg: string; fg: string }> = {
  ui_bug: { label: 'UI 버그', bg: '#fde2e2', fg: '#a02020' },
  feature: { label: '기능 제안', bg: '#dde9ff', fg: '#1a3aaa' },
  content: { label: '내용/카피', bg: '#e7f1d8', fg: '#446620' },
  crisis: { label: '위기', bg: '#1a1a2e', fg: '#fff' },
  praise: { label: '칭찬', bg: '#fff2c8', fg: '#7a5a00' },
  other: { label: '기타', bg: '#eee', fg: '#444' },
};

export function FeedbackDetailModal({ feedback, onClose, onUpdated }: Props) {
  const [status, setStatus] = useState('pending');
  const [adminNote, setAdminNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!feedback) return;
    setStatus(feedback.status || 'pending');
    setAdminNote(feedback.adminNote || '');
    setError('');
  }, [feedback]);

  if (!feedback) return null;

  const badge = CATEGORY_BADGE[feedback.category] || CATEGORY_BADGE.other;

  async function handleSave() {
    if (!feedback) return;
    setSubmitting(true);
    setError('');
    try {
      const updated = await updateFeedbackStatus(feedback.id, status, adminNote);
      onUpdated(updated);
      onClose();
    } catch {
      setError('저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 10000, padding: 16,
      }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'white', borderRadius: 12,
          maxWidth: 520, width: '100%',
          maxHeight: '85vh',
          display: 'flex', flexDirection: 'column',
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        {/* 헤더 */}
        <div style={{ padding: '16px 20px', borderBottom: '1px solid #eee', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span
              style={{
                fontSize: 11, fontWeight: 600, padding: '3px 8px', borderRadius: 4,
                background: badge.bg, color: badge.fg,
              }}
            >
              {badge.label}
            </span>
            <span style={{ fontSize: 13, color: '#888' }}>#{feedback.id}</span>
          </div>
          <button
            onClick={onClose}
            aria-label="닫기"
            style={{ background: 'none', border: 'none', fontSize: 20, color: '#888', cursor: 'pointer', padding: 4 }}
          >
            ×
          </button>
        </div>

        {/* 본문 */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', fontSize: 13 }}>
          <Meta label="사용자 ID" value={feedback.userId || '익명'} />
          <Meta label="작성 일시" value={feedback.createdAt ? new Date(feedback.createdAt).toLocaleString('ko-KR') : '-'} />

          {/* 회신 동의 + 이메일 (회신 동의한 경우만 강조 표시) */}
          {feedback.contactConsent && feedback.contactEmail ? (
            <div style={{
              marginTop: 12, marginBottom: 6,
              padding: '10px 12px',
              background: '#fff7e6', border: '1px solid #f3d59a', borderRadius: 6,
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
            }}>
              <div>
                <div style={{ fontSize: 11, color: '#9a6b00', fontWeight: 600, marginBottom: 2 }}>
                  📧 회신 동의 — 답변 회신 요청
                </div>
                <a
                  href={`mailto:${feedback.contactEmail}?subject=${encodeURIComponent(`[다시봄] 의견 #${feedback.id} 답변`)}`}
                  style={{ fontSize: 13, color: '#1a3aaa', fontFamily: 'ui-monospace, monospace', textDecoration: 'underline', wordBreak: 'break-all' }}
                >
                  {feedback.contactEmail}
                </a>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (feedback.contactEmail) navigator.clipboard?.writeText(feedback.contactEmail);
                }}
                style={{ flexShrink: 0, padding: '5px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}
              >
                복사
              </button>
            </div>
          ) : feedback.contactConsent ? (
            <Meta label="회신 동의" value="동의 (이메일 미입력)" />
          ) : (
            <Meta label="회신 동의" value="비동의" />
          )}
          {feedback.pageUrl && <Meta label="작성 페이지" value={feedback.pageUrl} />}


          <div style={{ marginTop: 16, marginBottom: 6, fontSize: 12, color: '#888', fontWeight: 600 }}>의견 본문</div>
          <div
            style={{
              padding: 14, background: '#f9f9f9', borderRadius: 8,
              fontSize: 13, lineHeight: 1.7, color: '#333',
              whiteSpace: 'pre-wrap', overflowWrap: 'break-word',
            }}
          >
            {feedback.content}
          </div>

          <div style={{ marginTop: 18, marginBottom: 6, fontSize: 12, color: '#888', fontWeight: 600 }}>처리 상태</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {STATUS_OPTIONS.map((opt) => (
              <label
                key={opt.value}
                style={{
                  padding: '6px 12px', borderRadius: 16, fontSize: 12,
                  border: status === opt.value ? '1px solid #1A1A2E' : '1px solid #ddd',
                  background: status === opt.value ? '#1A1A2E' : 'white',
                  color: status === opt.value ? 'white' : '#555',
                  cursor: 'pointer',
                }}
              >
                <input
                  type="radio"
                  name="feedback-status"
                  checked={status === opt.value}
                  onChange={() => setStatus(opt.value)}
                  style={{ display: 'none' }}
                />
                {opt.label}
              </label>
            ))}
          </div>

          <div style={{ marginTop: 18, marginBottom: 6, fontSize: 12, color: '#888', fontWeight: 600 }}>관리자 메모</div>
          <textarea
            value={adminNote}
            onChange={(e) => setAdminNote(e.target.value)}
            placeholder="처리 내용·후속 조치 등을 메모로 남겨주세요"
            rows={4}
            style={{
              width: '100%', padding: 10, border: '1px solid #ddd', borderRadius: 6,
              fontSize: 13, lineHeight: 1.6, fontFamily: 'inherit', resize: 'vertical',
            }}
          />

          {error && <p style={{ color: '#e55', fontSize: 12, marginTop: 10 }}>{error}</p>}
        </div>

        {/* 푸터 */}
        <div style={{ padding: '14px 20px', borderTop: '1px solid #eee', display: 'flex', gap: 8 }}>
          <button
            onClick={onClose}
            disabled={submitting}
            style={{
              flex: 1, padding: 11, borderRadius: 8, fontSize: 13,
              background: 'white', border: '1px solid #ddd', color: '#555',
              cursor: 'pointer',
            }}
          >
            취소
          </button>
          <button
            onClick={handleSave}
            disabled={submitting}
            style={{
              flex: 2, padding: 11, borderRadius: 8, fontSize: 13, fontWeight: 600,
              background: '#1A1A2E', color: 'white', border: 'none',
              cursor: submitting ? 'not-allowed' : 'pointer',
              opacity: submitting ? 0.6 : 1,
            }}
          >
            {submitting ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', gap: 12, fontSize: 12, marginBottom: 4 }}>
      <span style={{ color: '#888', minWidth: 80 }}>{label}</span>
      <span style={{ color: '#333' }}>{value}</span>
    </div>
  );
}
