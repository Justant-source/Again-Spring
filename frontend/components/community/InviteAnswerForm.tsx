'use client';

interface InviteAnswerFormProps {
  userTitle: string;
  bodyRaw: string;
  onBodyChange: (value: string) => void;
  onSubmit: () => void;
  submitting: boolean;
  error: string | null;
  /** 제출 버튼 라벨 */
  submitLabel?: string;
  /** textarea 위 라벨 */
  bodyLabel?: string;
  /** 다시 작성 CTA용 testid */
  submitTestId?: string;
}

export function InviteAnswerForm({
  userTitle,
  bodyRaw,
  onBodyChange,
  onSubmit,
  submitting,
  error,
  submitLabel = '덧붙이기',
  bodyLabel = '상대방으로 답하기',
  submitTestId,
}: InviteAnswerFormProps) {
  return (
    <>
      <div style={{ marginBottom: 20 }}>
        <label style={{ fontSize: 12, color: 'var(--P-sub)', display: 'block', marginBottom: 8 }}>
          제목
        </label>
        <div
          style={{
            width: '100%',
            padding: '10px 12px',
            border: '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 13,
            color: 'var(--P-sub)',
            background: 'var(--P-card)',
          }}
        >
          {userTitle}
        </div>
      </div>

      <div style={{ marginBottom: 20 }}>
        <label style={{ fontSize: 12, color: 'var(--P-sub)', display: 'block', marginBottom: 8 }}>
          {bodyLabel}
        </label>
        <textarea
          value={bodyRaw}
          onChange={(e) => onBodyChange(e.target.value)}
          placeholder="상대방의 입장에서 이야기해주세요"
          maxLength={600}
          style={{
            width: '100%',
            minHeight: 200,
            padding: '12px 14px',
            border: '1px solid var(--faction-partner)',
            borderRadius: 8,
            background: 'var(--faction-partner-bg)',
            fontSize: 13,
            fontFamily: 'var(--font-serif)',
            lineHeight: 1.6,
            color: 'var(--P-ink)',
            outline: 'none',
            resize: 'vertical',
          }}
        />
        <div
          style={{
            textAlign: 'right',
            fontSize: 11,
            color: 'var(--P-sub)',
            marginTop: 6,
          }}
        >
          {bodyRaw.length} / 600
        </div>
      </div>

      {error && (
        <div
          role="alert"
          style={{
            padding: '12px 14px',
            background: '#FEE',
            border: '1px solid #F99',
            borderRadius: 8,
            fontSize: 12,
            color: '#C33',
            marginBottom: 20,
          }}
        >
          {error}
        </div>
      )}

      <button
        type="button"
        onClick={onSubmit}
        disabled={submitting}
        data-testid={submitTestId}
        style={{
          width: '100%',
          padding: '14px 16px',
          background: 'var(--P-ink)',
          color: 'white',
          border: 'none',
          borderRadius: 8,
          fontSize: 14,
          fontWeight: 500,
          cursor: submitting ? 'default' : 'pointer',
          opacity: submitting ? 0.6 : 1,
          transition: 'all 0.2s',
        }}
      >
        {submitting ? '제출 중...' : submitLabel}
      </button>
    </>
  );
}
