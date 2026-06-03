'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';

const PEACH = '#C9785A';
const SAGE = '#5F8F76';

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

type Tab = 'mine' | 'voted' | 'saved';

function getPostStatus(post: Post): { text: string; color: string } {
  if (post.status === 'VOTING') {
    if (post.inviteToken && !post.partnerAnsweredAt) {
      return { text: '대기', color: SAGE };
    }
    return { text: '완료', color: PEACH };
  }
  return { text: '마감', color: 'var(--L-sub)' };
}

function getPostMeta(post: Post): string {
  const votes = post.voteCount || 0;
  if (post.status === 'CLOSED') return `마감됨 · ${votes}표`;
  if (post.inviteToken && !post.partnerAnsweredAt) return '상대 답변 대기 중';
  return `${votes}표`;
}

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();

  const [tab, setTab] = useState<Tab>('mine');
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);

  useEffect(() => {
    if (hasHydrated && (!user || user.isGuest)) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  useEffect(() => {
    if (tab === 'mine' && hasHydrated && user && !user.isGuest) {
      setPostsLoading(true);
      api.get('/api/users/me/posts')
        .then((res) => setMyPosts(res?.data || []))
        .catch(() => setMyPosts([]))
        .finally(() => setPostsLoading(false));
    }
  }, [tab, hasHydrated, user]);

  if (!hasHydrated || !user) return null;

  const avatarChar = (user.nickname || '?').charAt(0);
  const TABS: { key: Tab; label: string }[] = [
    { key: 'mine',  label: '내 사연' },
    { key: 'voted', label: '투표한 글' },
    { key: 'saved', label: '저장' },
  ];

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 80 }}>
      <div style={{ maxWidth: 640, margin: '0 auto', padding: '18px 22px 0' }}>

        {/* 헤더 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 17, fontWeight: 500, color: 'var(--L-ink)' }}>
            마이페이지
          </span>
        </div>

        {/* 프로필 행 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 22 }}>
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
            <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 3 }}>
              사연 {myPosts.length} · 투표 0 · 댓글 0
            </div>
          </div>
        </div>

        {/* 탭 */}
        <div style={{ display: 'flex', gap: 18, borderBottom: '1px solid var(--L-border)' }}>
          {TABS.map(({ key, label }) => {
            const on = tab === key;
            return (
              <span
                key={key}
                onClick={() => setTab(key)}
                style={{
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: on ? 500 : 400,
                  color: on ? 'var(--L-ink)' : 'var(--L-sub)',
                  paddingBottom: 10,
                  borderBottom: `2px solid ${on ? 'var(--L-ink)' : 'transparent'}`,
                  transition: 'color 0.15s',
                }}
              >
                {label}
              </span>
            );
          })}
        </div>

        {/* ── 내 사연 ── */}
        {tab === 'mine' && (
          <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {postsLoading ? (
              <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                로딩 중...
              </div>
            ) : myPosts.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                아직 사연이 없어요
              </div>
            ) : (
              myPosts.map((post) => {
                const st = getPostStatus(post);
                return (
                  <div
                    key={post.id}
                    onClick={() => router.push(`/community/${post.id}`)}
                    style={{
                      padding: '14px 15px',
                      background: 'var(--L-card)',
                      border: '1px solid var(--L-border)',
                      borderRadius: 8,
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 7 }}>
                      <span style={{
                        fontSize: 11, color: 'var(--L-bg)', background: 'var(--L-ink)',
                        borderRadius: 999, padding: '2px 9px',
                      }}>
                        {post.categoryName}
                      </span>
                      <span style={{
                        fontSize: 11, color: st.color,
                        border: `1px solid ${st.color}`,
                        borderRadius: 999, padding: '1px 8px',
                      }}>
                        {st.text}
                      </span>
                    </div>
                    <div style={{
                      fontSize: 14.5, fontWeight: 500, color: 'var(--L-ink)',
                      fontFamily: 'var(--font-serif)', lineHeight: 1.5,
                    }}>
                      {post.title}
                    </div>
                    <div style={{ fontSize: 11.5, color: 'var(--L-sub)', marginTop: 6 }}>
                      {getPostMeta(post)}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* ── 투표한 글 ── */}
        {tab === 'voted' && (
          <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

        {/* ── 저장 ── */}
        {tab === 'saved' && (
          <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

      </div>
    </div>
  );
}
