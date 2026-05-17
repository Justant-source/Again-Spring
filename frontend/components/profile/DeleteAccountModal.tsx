'use client';

import { useState } from 'react';
import { api } from '@/lib/api/client';
import type { User } from '@/lib/types';

interface Props {
  open: boolean;
  user: User;
  onClose: () => void;
  onDeleted: () => void;
}

type Step = 'confirm' | 'reconfirm' | 'password';

export function DeleteAccountModal({ open, user, onClose, onDeleted }: Props) {
  const [step, setStep] = useState<Step>('confirm');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!open) return null;

  // Guest users and OAuth users have no password — skip to immediate deletion
  const needsPassword = !user.isGuest && !user.provider;

  const handleClose = () => {
    setStep('confirm');
    setPassword('');
    setError('');
    onClose();
  };

  const handleDelete = async () => {
    setLoading(true);
    setError('');
    try {
      await api.delete('/api/users/me', {
        data: needsPassword ? { password } : undefined,
      });
      onDeleted();
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) {
        setError('비밀번호가 일치하지 않습니다.');
      } else {
        setError('오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
  };

  const overlayStyle: React.CSSProperties = {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0,0,0,0.45)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    padding: '0 24px',
  };

  const cardStyle: React.CSSProperties = {
    background: '#fff',
    borderRadius: 12,
    padding: '28px 24px',
    width: '100%',
    maxWidth: 360,
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  };

  if (step === 'confirm') {
    return (
      <div style={overlayStyle} onClick={handleClose}>
        <div style={cardStyle} onClick={(e) => e.stopPropagation()}>
          <div className="serif" style={{ fontSize: 17, fontWeight: 600, color: '#B94040' }}>
            계정을 삭제하시겠어요?
          </div>
          <div style={{ fontSize: 13, color: '#6B6660', lineHeight: 1.65 }}>
            계정을 삭제하면 대화 내역과 리포트를 더 이상 볼 수 없습니다.
            이 작업은 되돌릴 수 없습니다.
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
            <button
              onClick={handleClose}
              style={{
                flex: 1,
                padding: '11px 0',
                border: '1px solid var(--L-rule)',
                borderRadius: 6,
                background: 'transparent',
                fontSize: 14,
                color: '#6B6660',
                cursor: 'pointer',
              }}
            >
              취소
            </button>
            <button
              onClick={() => {
                if (needsPassword) {
                  setStep('password');
                } else if (!user.isGuest && user.provider) {
                  setStep('reconfirm');
                } else {
                  handleDelete();
                }
              }}
              style={{
                flex: 1,
                padding: '11px 0',
                border: 'none',
                borderRadius: 6,
                background: '#B94040',
                color: '#fff',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              계속
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (step === 'reconfirm') {
    return (
      <div style={overlayStyle} onClick={handleClose}>
        <div style={cardStyle} onClick={(e) => e.stopPropagation()}>
          <div className="serif" style={{ fontSize: 17, fontWeight: 600, color: '#B94040' }}>
            정말 삭제하시겠어요?
          </div>
          <div style={{ fontSize: 13, color: '#6B6660', lineHeight: 1.65 }}>
            Google 계정으로 가입하셨어요.
            <br />
            계정을 삭제하면 모든 대화 내역과 리포트를 다시 볼 수 없으며,
            이 작업은 되돌릴 수 없습니다.
          </div>
          {error && (
            <div style={{ fontSize: 12, color: '#B94040' }}>{error}</div>
          )}
          <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
            <button
              onClick={handleClose}
              style={{
                flex: 1,
                padding: '11px 0',
                border: '1px solid var(--L-rule)',
                borderRadius: 6,
                background: 'transparent',
                fontSize: 14,
                color: '#6B6660',
                cursor: 'pointer',
              }}
            >
              취소
            </button>
            <button
              onClick={handleDelete}
              disabled={loading}
              style={{
                flex: 1,
                padding: '11px 0',
                border: 'none',
                borderRadius: 6,
                background: loading ? '#E8E0E0' : '#B94040',
                color: loading ? '#9B8888' : '#fff',
                fontSize: 14,
                fontWeight: 600,
                cursor: loading ? 'not-allowed' : 'pointer',
              }}
            >
              {loading ? '처리 중...' : '삭제 확인'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={overlayStyle}>
      <div style={cardStyle}>
        <div className="serif" style={{ fontSize: 17, fontWeight: 600, color: '#B94040' }}>
          비밀번호 확인
        </div>
        <div style={{ fontSize: 13, color: '#6B6660', lineHeight: 1.65 }}>
          계정 삭제를 위해 비밀번호를 입력해주세요.
        </div>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="현재 비밀번호"
          autoFocus
          onKeyDown={(e) => e.key === 'Enter' && !loading && password && handleDelete()}
          style={{
            width: '100%',
            padding: '11px 12px',
            border: `1px solid ${error ? '#B94040' : 'var(--L-rule)'}`,
            borderRadius: 6,
            fontSize: 14,
            outline: 'none',
            boxSizing: 'border-box',
          }}
        />
        {error && (
          <div style={{ fontSize: 12, color: '#B94040', marginTop: -8 }}>{error}</div>
        )}
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={handleClose}
            style={{
              flex: 1,
              padding: '11px 0',
              border: '1px solid var(--L-rule)',
              borderRadius: 6,
              background: 'transparent',
              fontSize: 14,
              color: '#6B6660',
              cursor: 'pointer',
            }}
          >
            취소
          </button>
          <button
            onClick={handleDelete}
            disabled={loading || !password}
            style={{
              flex: 1,
              padding: '11px 0',
              border: 'none',
              borderRadius: 6,
              background: loading || !password ? '#E8E0E0' : '#B94040',
              color: loading || !password ? '#9B8888' : '#fff',
              fontSize: 14,
              fontWeight: 600,
              cursor: loading || !password ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? '처리 중...' : '삭제 확인'}
          </button>
        </div>
      </div>
    </div>
  );
}
