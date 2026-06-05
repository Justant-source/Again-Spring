'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { FeedCard, BrandBar } from '@/components/community/c3';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';
import { useVoteStore } from '@/lib/store/voteStore';
import { timeAgo } from '@/lib/utils/timeAgo';

// id = BE PostCategory enum, label = 표시 한글
const C3_CATS = [
  { id: 'COUPLE',  label: '연인' },
  { id: 'MARRIED', label: '부부' },
  { id: 'FRIEND',  label: '친구' },
  { id: 'FAMILY',  label: '가족' },
  { id: 'WORK',    label: '직장' },
  { id: 'OTHER',   label: '기타' },
];

function getCategoryLabel(categoryId: string): string {
  const found = C3_CATS.find(c => c.id === (categoryId || '').toUpperCase());
  return found?.label || '기타';
}

export default function CommunityFeedPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  useGuestInit();
  const voteStoreVotes = useVoteStore((s) => s.votes);
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [sort, setSort] = useState<'latest' | 'recommended'>('latest');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const categoryOptions = [{ id: '', label: '전체' }, ...C3_CATS];

  useEffect(() => {
    const loadPosts = async () => {
      try {
        setLoading(true);
        setError(null);
        const result = await postApi.list({
          category: selectedCategory || undefined,
          sort,
        });
        setPosts(result.content);
      } catch (err) {
        console.error('Failed to load posts:', err);
        setError('사연을 불러올 수 없습니다. 다시 시도해주세요.');
        setPosts([]);
      } finally {
        setLoading(false);
      }
    };

    loadPosts();
  }, [selectedCategory, sort]);

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh' }}>
      <div style={{ padding: '18px 22px 90px' }}>
        {/* 상단 헤더: 다시봄 광장 + 우측 유저 칩 */}
        <BrandBar title="다시봄 광장" user={user} />

        {/* 카테고리 필터 — 가로 스크롤 칩 */}
        <div style={{ display: 'flex', gap: 7, marginTop: 16, overflowX: 'auto', scrollbarWidth: 'none' }}>
          {categoryOptions.map((opt) => {
            const isSelected = selectedCategory === opt.id;
            return (
              <button
                key={opt.id}
                onClick={() => setSelectedCategory(opt.id)}
                style={{
                  flexShrink: 0,
                  padding: '6px 13px',
                  borderRadius: 999,
                  fontSize: 13,
                  whiteSpace: 'nowrap',
                  border: `1px solid ${isSelected ? 'var(--L-ink)' : 'var(--L-border)'}`,
                  background: isSelected ? 'var(--L-ink)' : 'transparent',
                  color: isSelected ? 'var(--L-bg)' : 'var(--L-ink)',
                  cursor: 'pointer',
                  fontFamily: 'var(--font-sans)',
                }}
              >
                {opt.label}
              </button>
            );
          })}
        </div>

        {/* 정렬 토글 — 우측 정렬 */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 14, marginTop: 14 }}>
          <button
            data-testid="feed-sort-latest"
            onClick={() => setSort('latest')}
            style={{
              background: 'none', border: 'none', padding: 0, cursor: 'pointer',
              fontSize: 12.5, fontWeight: 500,
              color: sort === 'latest' ? 'var(--L-ink)' : 'var(--L-sub)',
            }}
          >
            최신순
          </button>
          <button
            data-testid="feed-sort-recommended"
            onClick={() => setSort('recommended')}
            style={{
              background: 'none', border: 'none', padding: 0, cursor: 'pointer',
              fontSize: 12.5, fontWeight: sort === 'recommended' ? 500 : 400,
              color: sort === 'recommended' ? 'var(--L-ink)' : 'var(--L-sub)',
            }}
          >
            추천순
          </button>
        </div>

        {/* 로딩 상태 */}
        {loading && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)' }}>
            불러오는 중...
          </div>
        )}

        {/* 에러 상태 */}
        {error && (
          <div style={{
            marginTop: 16,
            padding: '16px',
            background: '#FEE',
            border: '1px solid #F99',
            borderRadius: 8,
            fontSize: 13,
            color: '#C33',
          }}>
            {error}
          </div>
        )}

        {/* 사연 목록 */}
        {!loading && posts.length > 0 && (
          <div data-testid="feed-post-list" style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {posts.map((post) => (
              <FeedCard
                key={post.id}
                href={`/community/${post.id}`}
                cat={getCategoryLabel(post.category)}
                id={post.authorNickname || '익명'}
                time={timeAgo(post.createdAt)}
                title={post.title}
                body={post.bodyPublished}
                g={post.authorPct ?? 50}
                votes={post.voteCount || 0}
                c={post.commentCount || 0}
                views={post.viewCount || 0}
                paired={post.paired}
                voted={!!post.myVoteSide || !!voteStoreVotes[post.id]}
              />
            ))}
          </div>
        )}

        {/* 빈 상태 */}
        {!loading && posts.length === 0 && !error && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)' }}>
            <div style={{ fontSize: 14, marginBottom: 12 }}>아직 사연이 없습니다</div>
            <Link
              href="/community/new"
              style={{
                display: 'inline-block',
                padding: '10px 16px',
                background: 'var(--L-ink)',
                color: 'var(--L-bg)',
                borderRadius: 6,
                fontSize: 13,
                textDecoration: 'none',
              }}
            >
              첫 사연 올리기
            </Link>
          </div>
        )}
      </div>

    </div>
  );
}
