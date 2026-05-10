'use client';

import { useState, useEffect } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';

/**
 * 임시 비밀번호로 로그인한 사용자에게 강제 비밀번호 변경을 유도하는 전역 모달.
 * - user.mustChangePassword === true 일 때만 표시
 * - ESC·바깥 클릭으로 닫히지 않음 (반드시 변경 필요)
 * - 변경 성공 시 user.mustChangePassword = false 로 갱신 + 모달 닫힘
 */
export function ForcePasswordChangeModal() {
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const [tempPassword, setTempPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const visible = !!user && !user.isGuest && !!user.mustChangePassword;

  useEffect(() => {
    if (!visible) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [visible]);

  if (!visible) return null;

  const canSubmit = tempPassword.length >= 4
      && newPassword.length >= 8
      && newPassword === confirmPassword
      && newPassword !== tempPassword
      && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError('');
    try {
      const r = await api.post('/api/users/me/password', {
        currentPassword: tempPassword,
        newPassword,
      });
      // 응답 user 객체로 store 갱신 → mustChangePassword=false 자동 반영
      if (r.data) setUser(r.data);
    } catch (e: any) {
      const code = e.response?.data?.error?.code;
      if (code === 'PASSWORD_MISMATCH') setError('임시 비밀번호가 일치하지 않아요. 메일을 다시 확인해주세요.');
      else if (code === 'SAME_PASSWORD') setError('새 비밀번호는 임시 비밀번호와 달라야 해요.');
      else setError(e.response?.data?.error?.message || '비밀번호 변경에 실패했어요. 잠시 후 다시 시도해주세요.');
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
        background: 'rgba(0,0,0,0.65)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 10500, padding: 16,
      }}
    >
      <div
        style={{
          background: 'white', borderRadius: 14,
          padding: '26px 24px', maxWidth: 380, width: '100%',
          boxShadow: '0 4px 24px rgba(0,0,0,0.25)',
        }}
      >
        <div style={{ fontSize: 17, fontWeight: 700, color: '#111', marginBottom: 6 }}>
          새 비밀번호 설정
        </div>
        <p style={{ fontSize: 13, color: '#555', lineHeight: 1.6, marginBottom: 18 }}>
          임시 비밀번호로 로그인하셨어요. 보안을 위해 새 비밀번호로 바꿔주세요.
        </p>

        <Field label="임시 비밀번호 (메일 확인)">
          <input
            type="password"
            value={tempPassword}
            onChange={(e) => setTempPassword(e.target.value)}
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
            <p style={{ fontSize: 11, color: '#e55', marginTop: 4 }}>새 비밀번호가 일치하지 않아요</p>
          )}
        </Field>

        {error && <p style={{ fontSize: 13, color: '#e55', margin: '4px 0 12px' }}>{error}</p>}

        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          style={{
            width: '100%', padding: 13, borderRadius: 10,
            background: canSubmit ? '#1A1A2E' : '#ccc',
            color: 'white', fontSize: 14, fontWeight: 600,
            border: 'none', cursor: canSubmit ? 'pointer' : 'not-allowed',
          }}
        >
          {submitting ? '변경 중...' : '비밀번호 변경'}
        </button>
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 12px',
  border: '1px solid #ddd', borderRadius: 8,
  fontSize: 14, outline: 'none',
  fontFamily: 'inherit',
  boxSizing: 'border-box',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <label style={{ display: 'block', fontSize: 11, color: '#888', marginBottom: 6 }}>{label}</label>
      {children}
    </div>
  );
}
