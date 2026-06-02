
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { DeleteAccountModal } from '@/components/profile/DeleteAccountModal';
import { ChangePasswordSection } from '@/components/profile/ChangePasswordSection';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import { api } from '@/lib/api/client';
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
  const [showSettings, setShowSettings] = useState(false);
  const [activeTab, setActiveTab] = useState<'myPosts' | 'voted' | 'saved'>('myPosts');
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);

  useEffect(() => {
    if (hasHydrated && !user) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  // Fetch my posts
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

  if (!hasHydrated || !user) {
    return null;
  }

  const showStyleSection = permissionsFor(user).ui.showCommunicationStyleSection;

  const MotifComponent = user.communicationStyle
    ? STYLE_MOTIF[user.communicationStyle]
    : null;

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

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="내정보"
        tone="L"
        back={false}
        right={
          <button
            onClick={() => setShowSettings(!showSettings)}
            style={{
              background: 'none',
              border: 'none',
              fontSize: 18,
              cursor: 'pointer',
              color: 'var(--L-ink)',
              padding: 0,
            }}
          >
            ⚙️
          </button>
        }
      />

      <div style={{ padding: '8px 28px 40px', display: 'flex', flexDirection: 'column' }}>
        {/* Profile Section */}
        {!showSettings && (
          <>
            <div style={{ marginTop: 12, marginBottom: 24, textAlign: 'center' }}>
              {/* Avatar */}
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

              {/* Nickname */}
              <div
                className="serif"
                style={{
                  fontSize: 15,
                  fontWeight: 500,
                  color: 'var(--L-ink)',
                  marginBottom: 8,
                }}
              >
                {user.nickname}
              </div>

              {/* Meta */}
              <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>
                사연 {myPosts.length} · 투표 0 · 댓글 0
              </div>
            </div>

            {/* Tabs */}
            <div
              style={{
                display: 'flex',
                gap: 24,
                marginBottom: 16,
                borderBottom: '1px solid var(--L-border)',
              }}
            >
              {['myPosts', 'voted', 'saved'].map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab as typeof activeTab)}
                  style={{
                    background: 'none',
                    border: 'none',
                    padding: '0 0 12px',
                    fontSize: 13,
                    fontWeight: 500,
                    color: activeTab === tab ? 'var(--L-ink)' : 'var(--L-sub)',
                    cursor: 'pointer',
                    borderBottom: activeTab === tab ? '2px solid var(--L-ink)' : 'none',
                  }}
                >
                  {tab === 'myPosts' ? '내 사연' : tab === 'voted' ? '투표한 글' : '저장'}
                </button>
              ))}
            </div>

            {/* Tab Content */}
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
                        {/* Category & Status */}
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            marginBottom: 8,
                          }}
                        >
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

                        {/* Title */}
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

                        {/* Meta */}
                        <div style={{ fontSize: 11.5, color: 'var(--L-sub)' }}>
                          투표 {post.voteCount || 0} · 댓글 0
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            )}

            {activeTab === 'voted' && (
              <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                준비 중입니다
              </div>
            )}

            {activeTab === 'saved' && (
              <div style={{ textAlign: 'center', padding: '20px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                준비 중입니다
              </div>
            )}
          </>
        )}

        {/* Settings Section */}
        {showSettings && (
          <>
            {/* User Info */}
            <div
              className="letter-card"
              style={{
                padding: '18px 16px',
                marginBottom: 16,
              }}
            >
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
                닉네임
              </div>
              <div
                className="serif"
                style={{
                  fontSize: 16,
                  color: 'var(--L-ink)',
                  fontWeight: 500,
                  marginBottom: 12,
                }}
              >
                {user.nickname}
              </div>

              {user.email && (
                <>
                  <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
                    이메일
                  </div>
                  <div
                    style={{
                      fontSize: 13,
                      color: 'var(--L-ink)',
                      marginBottom: 12,
                      wordBreak: 'break-all',
                    }}
                  >
                    {user.email}
                  </div>
                </>
              )}

              {user.isGuest && (
                <div
                  style={{
                    padding: '10px 12px',
                    background: 'var(--L-bg)',
                    border: '1px solid var(--L-border)',
                    borderRadius: '3px',
                    fontSize: '12px',
                    color: 'var(--L-sub)',
                  }}
                >
                  게스트 모드
                </div>
              )}
            </div>

            {/* Communication style card — admin은 정책으로 노출 안 함 */}
            {showStyleSection && MotifComponent && (
              <div
                className="letter-card"
                style={{
                  padding: '18px 16px',
                  marginBottom: 16,
                }}
              >
                <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
                  당신의 대화 스타일
                </div>

                {MotifComponent ? (
                  <>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 12,
                        marginBottom: 14,
                      }}
                    >
                      <div
                        style={{
                          width: 56,
                          height: 56,
                          borderRadius: '50%',
                          background: 'var(--P-a)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'white',
                        }}
                      >
                        <MotifComponent size={28} color="white" />
                      </div>
                      <div>
                        <div
                          className="serif"
                          style={{
                            fontSize: 16,
                            color: 'var(--L-ink)',
                            fontWeight: 500,
                          }}
                        >
                          {user.communicationStyle}
                        </div>
                      </div>
                    </div>

                    <div
                      style={{
                        border: '1px solid var(--L-rule)',
                        borderRadius: 8,
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{
                          padding: '10px 14px 8px',
                          fontSize: 11,
                          color: 'var(--L-sub)',
                          borderBottom: '1px solid var(--L-rule)',
                        }}
                      >
                        스타일 다시 등록하기
                      </div>
                      {[
                        {
                          label: '10문항 다시 하기',
                          desc: '갈등 상황 기반 검사 · 약 2분',
                          href: '/onboarding?next=/profile',
                        },
                        {
                          label: 'MBTI 수정하기',
                          desc: '직접 입력으로 변경',
                          href: '/onboarding/mbti-input?next=/profile',
                        },
                      ].map((opt, i) => (
                        <button
                          key={opt.label}
                          onClick={() => router.push(opt.href)}
                          style={{
                            width: '100%',
                            background: 'transparent',
                            border: 'none',
                            borderTop: i === 0 ? 'none' : '1px solid var(--L-rule)',
                            padding: '12px 14px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            cursor: 'pointer',
                            textAlign: 'left',
                          }}
                        >
                          <div>
                            <div style={{ fontSize: 13, color: 'var(--L-ink)', marginBottom: 2 }}>
                              {opt.label}
                            </div>
                            <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>{opt.desc}</div>
                          </div>
                          <span style={{ color: 'var(--L-sub)', fontSize: 16, marginLeft: 12, flexShrink: 0 }}>›</span>
                        </button>
                      ))}
                    </div>
                  </>
                ) : (
                  <>
                    <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6, marginBottom: 14 }}>
                      아직 대화 스타일이 등록되지 않았어요.
                      <br />10문항으로 내 스타일을 파악해보세요.
                    </div>
                    <button
                      className="btn-L"
                      style={{ width: '100%' }}
                      onClick={() => router.push('/onboarding/intro?next=/profile')}
                    >
                      10문항 시작하기
                    </button>
                  </>
                )}
              </div>
            )}

            {/* 비밀번호 변경 (이메일 가입자만) */}
            <div style={{ marginBottom: 16 }}>
              <ChangePasswordSection />
            </div>

            {/* 관리자 진입 카드 — showAdminEntryButton 조건 */}
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

            {/* Actions */}
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
                  borderRadius: '3px',
                  fontSize: '14px',
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                계정 삭제
              </button>
            </div>

            {/* 법적 링크 */}
            <div style={{ marginTop: 24, paddingTop: 16, borderTop: '1px solid var(--L-border)', display: 'flex', justifyContent: 'center', gap: 16, flexWrap: 'wrap' }}>
              {[
                { href: '/terms', label: '이용약관' },
                { href: '/privacy', label: '개인정보처리방침' },
              ].map((link) => (
                <a key={link.href} href={link.href} style={{ fontSize: 12, color: 'var(--L-sub)', textDecoration: 'none' }}>
                  {link.label}
                </a>
              ))}
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
