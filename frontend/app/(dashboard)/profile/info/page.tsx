'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { DeleteAccountModal } from '@/components/profile/DeleteAccountModal';
import { ChangePasswordSection } from '@/components/profile/ChangePasswordSection';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { api } from '@/lib/api/client';
import { generateGuestNickname } from '@/lib/utils/guestNickname';

const PEACH = '#C9785A';
const SAGE = '#5F8F76';

export default function ProfileInfoPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const clearUser = useUserStore((s) => s.clear);
  const hasHydrated = useHasHydrated();

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [newNickname, setNewNickname] = useState('');
  const [nickSaving, setNickSaving] = useState(false);
  const [nickShuffling, setNickShuffling] = useState(false);
  const [nickError, setNickError] = useState('');
  const [nickSuccess, setNickSuccess] = useState(false);

  useEffect(() => {
    if (hasHydrated && (!user || user.isGuest)) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  useEffect(() => {
    if (user) {
      setNewNickname(user.nickname || '');
      setNickError('');
      setNickSuccess(false);
    }
  }, [user]);

  if (!hasHydrated || !user) return null;

  const handleLogout = () => {
    clearUser();
    router.push('/');
  };

  const handleSaveNickname = async () => {
    const trimmed = newNickname.trim();
    if (!trimmed || trimmed.length < 3 || trimmed.length > 12) {
      setNickError('닉네임은 3~12자여야 해요');
      return;
    }
    if (trimmed === user.nickname) {
      setNickError('현재와 동일한 닉네임이에요');
      return;
    }
    setNickSaving(true);
    setNickError('');
    try {
      const checkRes = await api.get(`/api/auth/check-nickname?nickname=${encodeURIComponent(trimmed)}`);
      if (!checkRes.data.available) {
        setNickError('이미 사용 중인 닉네임이에요');
        return;
      }
      const res = await api.patch('/api/users/me', { nickname: trimmed });
      setUser({ ...user, nickname: res.data.nickname || trimmed });
      setNickSuccess(true);
    } catch {
      setNickError('닉네임 변경에 실패했어요');
    } finally {
      setNickSaving(false);
    }
  };

  const handleShuffleNickname = async () => {
    setNickShuffling(true);
    setNickError('');
    try {
      for (let i = 0; i < 10; i++) {
        const candidate = generateGuestNickname();
        try {
          const res = await api.get(`/api/auth/check-nickname?nickname=${encodeURIComponent(candidate)}`);
          if (res.data.available) {
            setNewNickname(candidate);
            setNickSuccess(false);
            return;
          }
        } catch {
          setNewNickname(candidate);
          setNickSuccess(false);
          return;
        }
      }
      setNewNickname(generateGuestNickname());
      setNickSuccess(false);
    } finally {
      setNickShuffling(false);
    }
  };

  const avatarChar = (user.nickname || '?').charAt(0);

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 80 }}>
      <div style={{ maxWidth: 640, margin: '0 auto', padding: '18px 22px 0' }}>

        {/* 헤더 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 22 }}>
          <button
            onClick={() => router.back()}
            style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', fontSize: 20, color: 'var(--L-sub)', lineHeight: 1 }}
          >
            ‹
          </button>
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 17, fontWeight: 500, color: 'var(--L-ink)' }}>
            내 정보
          </span>
        </div>

        {/* 프로필 행 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
          <span style={{
            width: 46, height: 46, borderRadius: '50%',
            background: 'var(--P-a)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--font-serif)', fontSize: 18, color: '#fff',
            flexShrink: 0,
          }}>
            {avatarChar}
          </span>
          <div>
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--L-ink)' }}>{user.nickname}</div>
            {user.email && (
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 3 }}>{user.email}</div>
            )}
          </div>
        </div>

        {/* 닉네임 변경 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 8 }}>닉네임 변경</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              value={newNickname}
              onChange={(e) => { setNewNickname(e.target.value); setNickError(''); setNickSuccess(false); }}
              placeholder={user.nickname}
              maxLength={12}
              style={{
                flex: 1, border: 'none', borderBottom: '1px solid var(--L-border)',
                background: 'transparent', fontSize: 14, color: 'var(--L-ink)',
                padding: '4px 0 6px', outline: 'none',
              }}
            />
            <button
              onClick={handleShuffleNickname}
              disabled={nickShuffling}
              style={{
                border: '1px solid var(--L-border)', borderRadius: 4, padding: '4px 10px',
                background: 'none', fontSize: 12, color: 'var(--L-sub)', cursor: 'pointer',
                whiteSpace: 'nowrap', flexShrink: 0,
              }}
            >
              {nickShuffling ? '...' : '다른 이름'}
            </button>
            <button
              onClick={handleSaveNickname}
              disabled={nickSaving}
              style={{
                border: '1px solid var(--L-ink)', borderRadius: 4, padding: '4px 12px',
                background: 'none', fontSize: 12, color: 'var(--L-ink)', cursor: 'pointer',
                flexShrink: 0,
              }}
            >
              {nickSaving ? '...' : '저장'}
            </button>
          </div>
          {nickError && <div style={{ fontSize: 11, color: PEACH, marginTop: 4 }}>{nickError}</div>}
          {nickSuccess && <div style={{ fontSize: 11, color: SAGE, marginTop: 4 }}>닉네임이 변경됐어요</div>}
        </div>

        {/* 비밀번호 변경 */}
        <div style={{ marginBottom: 20 }}>
          <ChangePasswordSection />
        </div>

        {/* 관리자 */}
        {permissionsFor(user).ui.showAdminEntryButton && (
          <button
            onClick={() => router.push('/admin')}
            style={{
              background: 'none', border: '1px solid var(--L-border)', borderRadius: 4,
              padding: '10px 14px', fontSize: 13, color: 'var(--L-ink)', cursor: 'pointer',
              textAlign: 'left', width: '100%', marginBottom: 12,
            }}
          >
            관리자 대시보드 ›
          </button>
        )}
        {permissionsFor(user).admin.canAccessMarketing && (
          <button
            onClick={() => router.push('/admin/marketing')}
            style={{
              background: 'none', border: '1px solid var(--L-border)', borderRadius: 4,
              padding: '10px 14px', fontSize: 13, color: 'var(--L-ink)', cursor: 'pointer',
              textAlign: 'left', width: '100%', marginBottom: 20,
            }}
          >
            마케팅 관리 ›
          </button>
        )}

        {/* 로그아웃 / 계정 삭제 */}
        <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
          <button
            onClick={handleLogout}
            style={{
              flex: 1, border: '1px solid var(--L-border)', borderRadius: 4,
              padding: '11px 0', background: 'none',
              fontSize: 13, color: 'var(--L-sub)', cursor: 'pointer',
            }}
          >
            로그아웃
          </button>
          <button
            onClick={() => setShowDeleteModal(true)}
            style={{
              flex: 1, border: `1px solid ${PEACH}`, borderRadius: 4,
              padding: '11px 0', background: 'none',
              fontSize: 13, color: PEACH, cursor: 'pointer',
            }}
          >
            계정 삭제
          </button>
        </div>

      </div>

      <DeleteAccountModal
        open={showDeleteModal}
        user={user}
        onClose={() => setShowDeleteModal(false)}
        onDeleted={() => {
          clearUser();
          router.push('/');
        }}
      />
    </div>
  );
}
