'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { FeedCard } from '@/components/community/c3/FeedCard';
import { timeAgo } from '@/lib/utils/timeAgo';

type Tab = 'mine' | 'voted' | 'saved';

const CATS: Record<string, string> = {
  COUPLE: '연인', MARRIED: '부부', FRIEND: '친구',
  FAMILY: '가족', WORK: '직장', OTHER: '기타',
};

function catLabel(id?: string) {
  return CATS[(id || '').toUpperCase()] || '기타';
}

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();
  const userId = user?.id;
  const isGuest = user?.isGuest;

  const [tab, setTab] = useState<Tab>('mine');
  const [myPosts, setMyPosts] = useState<PostSummary[]>([]);
  const [votedPosts, setVotedPosts] = useState<PostSummary[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (hasHydrated && (!userId || isGuest)) {
      router.push('/login');
    }
  }, [hasHydrated, userId, isGuest, router]);

  useEffect(() => {
    if (tab !== 'mine' || !userId || isGuest) return;
    setLoading(true);
    postApi.mine()
      .then(setMyPosts)
      .catch(() => setMyPosts([]))
      .finally(() => setLoading(false));
  }, [tab, userId, isGuest]);

  useEffect(() => {
    if (tab !== 'voted' || !userId || isGuest) return;
    setLoading(true);
    postApi.voted()
      .then(setVotedPosts)
      .catch(() => setVotedPosts([]))
      .finally(() => setLoading(false));
  }, [tab, userId, isGuest]);

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
              {user.nickname}
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
          <div style={{ marginTop: 16 }}>
            {loading && (
              <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                불러오는 중…
              </div>
            )}
            {!loading && myPosts.length === 0 && (
              <div style={{ textAlign: 'center', padding: '40px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                아직 작성한 사연이 없습니다
              </div>
            )}
            {!loading && myPosts.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {myPosts.map((post) => (
                  <div key={post.id} style={{ position: 'relative' }}>
                    {post.visibility === 'PRIVATE' && (
                      <span style={{
                        position: 'absolute', top: 10, right: 44,
                        fontSize: 10, color: 'var(--L-sub)',
                        background: 'var(--L-border)', borderRadius: 4,
                        padding: '1px 6px', zIndex: 1,
                      }}>비공개</span>
                    )}
                    <FeedCard
                      href={`/community/${post.id}`}
                      cat={catLabel(post.category)}
                      id={user.nickname || '나'}
                      time={timeAgo(post.createdAt)}
                      title={post.title}
                      body={post.bodyPublished}
                      g={post.authorPct ?? 50}
                      votes={post.voteCount || 0}
                      c={post.commentCount || 0}
                      views={post.viewCount || 0}
                      paired={post.paired}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── 투표한 글 ── */}
        {tab === 'voted' && (
          <div style={{ marginTop: 16 }}>
            {loading && (
              <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                불러오는 중…
              </div>
            )}
            {!loading && votedPosts.length === 0 && (
              <div style={{ textAlign: 'center', padding: '40px 0', fontSize: 13, color: 'var(--L-sub)' }}>
                아직 투표한 글이 없습니다
              </div>
            )}
            {!loading && votedPosts.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {votedPosts.map((post) => (
                  <FeedCard
                    key={post.id}
                    href={`/community/${post.id}`}
                    cat={catLabel(post.category)}
                    id={post.authorNickname || '익명'}
                    time={timeAgo(post.createdAt)}
                    title={post.title}
                    body={post.bodyPublished}
                    g={post.authorPct ?? 50}
                    votes={post.voteCount || 0}
                    c={post.commentCount || 0}
                    views={post.viewCount || 0}
                    paired={post.paired}
                  />
                ))}
              </div>
            )}
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
