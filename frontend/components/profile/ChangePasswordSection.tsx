'use client';

import { useState } from 'react';
import { api } from '@/lib/api/client';
import { useUserStore } from '@/lib/store/userStore';

/**
 * 프로필 화면용 비밀번호 변경 섹션.
 * - 이메일 가입자만 표시 (게스트·OAuth 비표시)
 * - 현재 비밀번호 + 새 비밀번호 + 확인
 * - 변경 성공 시 user store 갱신 + 폼 초기화
 */
export function ChangePasswordSection() {
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const [open, setOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  if (!user || user.isGuest) return null;
  if (user.provider) return null; // OAuth 사용자는 비밀번호 미보유

  function reset() {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError('');
    setSuccess(false);
  }

  const canSubmit = currentPassword.length >= 4
      && newPassword.length >= 8
      && newPassword === confirmPassword
      && newPassword !== currentPassword
      && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError('');
    try {
      const r = await api.post('/api/users/me/password', {
        currentPassword,
        newPassword,
      });
      if (r.data) setUser(r.data);
      setSuccess(true);
      reset();
      setTimeout(() => { setSuccess(false); setOpen(false); }, 2000);
    } catch (e: any) {
      const code = e.response?.data?.error?.code;
      if (code === 'PASSWORD_MISMATCH') setError('현재 비밀번호가 일치하지 않아요.');
      else if (code === 'SAME_PASSWORD') setError('새 비밀번호는 현재 비밀번호와 달라야 해요.');
      else setError(e.response?.data?.error?.message || '비밀번호 변경에 실패했어요.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="letter-card"
        style={{
          width: '100%',
          padding: '14px 16px',
          background: 'transparent',
          border: '1px solid var(--L-rule)',
          borderRadius: 8,
          textAlign: 'left',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div>
          <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500 }}>비밀번호 변경</div>
          <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>
            현재 비밀번호 확인 후 새로 설정
          </div>
        </div>
        <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
      </button>
    );
  }

  return (
    <div
      className="letter-card"
      style={{
        padding: '16px 16px',
        background: 'var(--L-card)',
        border: '1px solid var(--L-rule)',
        borderRadius: 8,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500 }}>비밀번호 변경</div>
        <button
          onClick={() => { reset(); setOpen(false); }}
          aria-label="닫기"
          style={{ background: 'none', border: 'none', fontSize: 18, color: 'var(--L-sub)', cursor: 'pointer', padding: 0, lineHeight: 1 }}
        >
          ×
        </button>
      </div>

      <Field label="현재 비밀번호">
        <input
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          autoComplete="current-password"
          style={inputStyle}
        />
      </Field>
      <Field label="새 비밀번호 (8자 이상)">
        <input
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          autoComplete="new-password"
          style={inputStyle}
        />
      </Field>
      <Field label="새 비밀번호 확인">
        <input
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
          onKeyDown={(e) => e.key === 'Enter' && canSubmit && handleSubmit()}
          style={inputStyle}
        />
        {confirmPassword && newPassword !== confirmPassword && (
          <p style={{ fontSize: 11, color: '#e55', marginTop: 4 }}>비밀번호가 일치하지 않아요</p>
        )}
      </Field>

      {error && <p style={{ fontSize: 12, color: '#e55', margin: '4px 0 10px' }}>{error}</p>}
      {success && <p style={{ fontSize: 12, color: '#0e6e3f', margin: '4px 0 10px' }}>비밀번호가 변경되었어요</p>}

      <button
        onClick={handleSubmit}
        disabled={!canSubmit}
        className="btn-L"
        style={{ width: '100%', opacity: canSubmit ? 1 : 0.5, cursor: canSubmit ? 'pointer' : 'not-allowed' }}
      >
        {submitting ? '변경 중...' : '변경하기'}
      </button>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '9px 11px',
  border: '1px solid var(--L-border)', borderRadius: 6,
  fontSize: 13, outline: 'none',
  fontFamily: 'inherit',
  boxSizing: 'border-box',
  background: 'white',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <label style={{ display: 'block', fontSize: 11, color: 'var(--L-sub)', marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  );
}
