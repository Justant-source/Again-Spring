
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { DeleteAccountModal } from '@/components/profile/DeleteAccountModal';
import { ChangePasswordSection } from '@/components/profile/ChangePasswordSection';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { api } from '@/lib/api/client';
import { generateGuestNickname } from '@/lib/utils/guestNickname';
import type { User } from '@/lib/types';

interface Post {
  id: string;
  title: string;
  categoryId: string;
  categoryName: string;
  status: 'VOTING' | 'CLOSED';
  voteCount?: number;
  inviteToken?: string;
  partnerAnsweredAt?: string;
}

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const clearUser = useUserStore((s) => s.clear);
  const hasHydrated = useHasHydrated();
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [activeTab, setActiveTab] = useState<'myPosts' | 'voted' | 'saved' | 'myInfo'>('myPosts');
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);

  // 닉네임 변경
  const [newNickname, setNewNickname] = useState('');
  const [nickShuffling, setNickShuffling] = useState(false);
  const [nickError, setNickError] = useState('');
  const [nickSaving, setNickSaving] = useState(false);
  const [nickSuccess, setNickSuccess] = useState(false);

  useEffect(() => {
    if (hasHydrated && (!user || user.isGuest)) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  useEffect(() => {
    if (activeTab === 'myPosts' && hasHydrated && user && !user.isGuest) {
      const fetchMyPosts = async () => {
        setPostsLoading(true);
        try {
          const response = await api.get('/api/users/me/posts');
          setMyPosts(response?.data || []);
        } catch (err) {
          console.error('Failed to fetch my posts:', err);
          setMyPosts([]);
        } finally {
          setPostsLoading(false);
        }
      };
      fetchMyPosts();
    }
  }, [activeTab, hasHydrated, user]);

  useEffect(() => {
    if (activeTab === 'myInfo' && user) {
      setNewNickname(user.nickname || '');
      setNickError('');
      setNickSuccess(false);
    }
  }, [activeTab, user]);

  if (!hasHydrated || !user) {
    return null;
  }

  const handleLogout = async () => {
    clearUser();
    router.push('/');
  };

  const getInitials = (nickname: string) => {
    return nickname
      .split(' ')
      .slice(0, 2)
      .map(n => n.charAt(0))
      .join('')
      .toUpperCase();
  };

  const getPostStatus = (post: Post) => {
    if (post.status === 'VOTING') {
      if (!post.partnerAnsweredAt) {
        if (post.inviteToken) {
          return { text: '대기', color: '#E74C3C' };
        }
        return { text: '완료', color: '#27AE60' };
      }
    }
    if (post.status === 'CLOSED') {
      return { text: '마감', color: 'var(--L-sub)' };
    }
    return null;
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
            return;
          }
        } catch {
          setNewNickname(candidate);
          return;
        }
      }
      setNewNickname(generateGuestNickname());
    } finally {
      setNickShuffling(false);
    }
  };

  const handleSaveNickname = async () => {
    const trimmed = newNickname.trim();
    if (!trimmed) {
      setNickError('닉네임을 입력해주세요');
      return;
    }
    if (trimmed.length < 3 || trimmed.length > 12) {
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

  const TABS = [
    { key: 'myPosts', label: '내 사연' },
    { key: 'voted',   label: '투표한 글' },
    { key: 'saved',   label: '저장' },
    { key: 'myInfo',  label: '내 정보' },
  ] as const;

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="내정보"
        tone="L"
        back={false}
      />

      <div style={{ padding: '8px 28px 40px', display: 'flex', flexDirection: 'column' }}>
        {/* Avatar + Nickname */}
        {activeTab !== 'myInfo' && (
          <div style={{ marginTop: 12, marginBottom: 24, textAlign: 'center' }}>
            <div
              style={{
                width: 46,
                height: 46,
                borderRadius: '50%',
                background: 'var(--P-a)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontSize: 18,
                fontWeight: 600,
                margin: '0 auto 12px',
              }}
            >
              {getInitials(user.nickname)}
            </div>
            <div
              className="serif"
              style={{ fontSize: 15, fontWeight: 500, color: 'var(--L-ink)', marginBottom: 8 }}
            >
              {user.nickname}
            </div>
            <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>
              사연 {myPosts.length} · 투표 0 · 댓글 0
            </div>
          </div>
        )}

        {/* Tabs */}
        <div
          style={{
            display: 'flex',
            gap: 24,
            marginBottom: 16,
            borderBottom: '1px solid var(--L-border)',
          }}
        >
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              style={{
                background: 'none',
                border: 'none',
                padding: '0 0 12px',
                fontSize: 13,
                fontWeight: 500,
                color: activeTab === key ? 'var(--L-ink)' : 'var(--L-sub)',
                cursor: 'pointer',
                borderBottom: activeTab === key ? '2px solid var(--L-ink)' : 'none',
                whiteSpace: 'nowrap',
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {/* 내 사연 */}
        {activeTab === 'myPosts' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {postsLoading ? (
              <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                로딩 중...
              </div>
            ) : myPosts.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                아직 사연이 없어요
              </div>
            ) : (
              myPosts.map((post) => {
                const status = getPostStatus(post);
                return (
                  <div
                    key={post.id}
                    style={{
                      padding: '14px 15px',
                      background: 'var(--L-card)',
                      border: '1px solid var(--L-border)',
                      borderRadius: 8,
                      cursor: 'pointer',
                    }}
                    onClick={() => router.push(`/community/${post.id}`)}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                      <div
                        style={{
                          display: 'inline-block',
                          padding: '3px 8px',
                          background: 'var(--L-ink)',
                          color: 'var(--L-bg)',
                          borderRadius: 3,
                          fontSize: 11,
                          fontWeight: 500,
                        }}
                      >
                        {post.categoryName}
                      </div>
                      {status && (
                        <div
                          style={{
                            display: 'inline-block',
                            padding: '2px 6px',
                            background: status.color,
                            color: 'white',
                            borderRadius: 2,
                            fontSize: 10,
                            fontWeight: 500,
                          }}
                        >
                          {status.text}
                        </div>
                      )}
                    </div>
                    <div
                      className="serif"
                      style={{
                        fontSize: 14.5,
                        fontWeight: 500,
                        color: 'var(--L-ink)',
                        marginBottom: 8,
                        lineHeight: 1.5,
                      }}
                    >
                      {post.title}
                    </div>
                    <div style={{ fontSize: 11.5, color: 'var(--L-sub)' }}>
                      투표 {post.voteCount || 0} · 댓글 0
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* 투표한 글 */}
        {activeTab === 'voted' && (
          <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

        {/* 저장 */}
        {activeTab === 'saved' && (
          <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

        {/* 내 정보 */}
        {activeTab === 'myInfo' && (
          <>
            {/* 닉네임 변경 */}
            <div className="letter-card" style={{ padding: '18px 16px', marginBottom: 16 }}>
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>닉네임 변경</div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6 }}>
                <input
                  value={newNickname}
                  onChange={(e) => { setNewNickname(e.target.value); setNickError(''); setNickSuccess(false); }}
                  placeholder="닉네임"
                  maxLength={12}
                  style={{
                    flex: 1,
                    border: 'none',
                    borderBottom: '1px solid var(--L-border)',
                    background: 'transparent',
                    fontSize: 15,
                    color: 'var(--L-ink)',
                    padding: '4px 0 8px',
                    outline: 'none',
                  }}
                />
                <button
                  onClick={handleShuffleNickname}
                  disabled={nickShuffling}
                  style={{
                    background: 'none',
                    border: '1px solid var(--L-border)',
                    borderRadius: 3,
                    padding: '6px 10px',
                    fontSize: 12,
                    color: 'var(--L-sub)',
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                    flexShrink: 0,
                  }}
                >
                  {nickShuffling ? '...' : '다른 이름'}
                </button>
              </div>
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 14 }}>
                3~12자 · 현재: {user.nickname}
              </div>
              {nickError && (
                <div style={{ fontSize: 12, color: 'var(--L-point)', marginBottom: 10 }}>{nickError}</div>
              )}
              {nickSuccess && (
                <div style={{ fontSize: 12, color: '#27AE60', marginBottom: 10 }}>닉네임이 변경됐어요</div>
              )}
              <button
                onClick={handleSaveNickname}
                disabled={nickSaving}
                className="btn-L"
                style={{ width: '100%' }}
              >
                {nickSaving ? '저장 중...' : '저장'}
              </button>
            </div>

            {/* 이메일 */}
            {user.email && (
              <div className="letter-card" style={{ padding: '18px 16px', marginBottom: 16 }}>
                <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>이메일</div>
                <div style={{ fontSize: 13, color: 'var(--L-ink)', wordBreak: 'break-all' }}>
                  {user.email}
                </div>
              </div>
            )}

            {/* 게스트 모드 표시 */}
            {user.isGuest && (
              <div
                style={{
                  padding: '10px 12px',
                  background: 'var(--L-bg)',
                  border: '1px solid var(--L-border)',
                  borderRadius: 3,
                  fontSize: 12,
                  color: 'var(--L-sub)',
                  marginBottom: 16,
                }}
              >
                게스트 모드
              </div>
            )}


            {/* 비밀번호 변경 */}
            <div style={{ marginBottom: 16 }}>
              <ChangePasswordSection />
            </div>

            {/* 관리자 진입 카드 */}
            {permissionsFor(user).ui.showAdminEntryButton && (
              <div className="letter-card" style={{ padding: '4px 0', marginBottom: 16 }}>
                <button
                  onClick={() => router.push('/admin')}
                  style={{ width: '100%', background: 'none', border: 'none', padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', textAlign: 'left' }}
                >
                  <div style={{ fontSize: 14, color: 'var(--L-ink)', fontWeight: 500 }}>관리자 대시보드</div>
                  <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
                </button>
                {permissionsFor(user).admin.canAccessMarketing && (
                  <button
                    onClick={() => router.push('/admin/marketing')}
                    style={{ width: '100%', background: 'none', border: 'none', borderTop: '1px solid var(--L-border)', padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', textAlign: 'left' }}
                  >
                    <div style={{ fontSize: 14, color: 'var(--L-ink)', fontWeight: 500 }}>마케팅 관리</div>
                    <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
                  </button>
                )}
              </div>
            )}

            {/* 로그아웃 / 계정 삭제 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}>
              <button onClick={handleLogout} className="btn-L" style={{ width: '100%' }}>
                로그아웃
              </button>
              <button
                onClick={() => setShowDeleteModal(true)}
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  background: 'transparent',
                  color: '#B94040',
                  border: '1px solid #B94040',
                  borderRadius: 3,
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                계정 삭제
              </button>
            </div>
          </>
        )}
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
    </PhoneFrame>
  );
}
