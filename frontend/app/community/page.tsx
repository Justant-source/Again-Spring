'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { FeedCard, BrandBar, SearchPanel } from '@/components/community/c3';
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

const FEED_PAGE_SIZE = 20;

function mergePosts(prev: PostSummary[], next: PostSummary[]): PostSummary[] {
  if (prev.length === 0) return next;
  const seen = new Set(prev.map((post) => post.id));
  return [...prev, ...next.filter((post) => !seen.has(post.id))];
}

export default function CommunityFeedPage() {
  const user = useUserStore((s) => s.user);
  useGuestInit();
  const voteStoreVotes = useVoteStore((s) => s.votes);
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [sort, setSort] = useState<'latest' | 'recommended'>('latest');
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [, setTimeTick] = useState(0);
  const [searchPanelOpen, setSearchPanelOpen] = useState(false);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const feedRequestKeyRef = useRef(0);

  const categoryOptions = [{ id: '', label: '전체' }, ...C3_CATS];

  useEffect(() => {
    let cancelled = false;
    const loadPosts = async () => {
      try {
        const requestKey = ++feedRequestKeyRef.current;
        setLoading(true);
        setLoadingMore(false);
        setPosts([]);
        setPage(0);
        setHasMore(false);
        setError(null);
        const result = await postApi.list({
          category: selectedCategory || undefined,
          sort,
          page: 0,
          size: FEED_PAGE_SIZE,
        });
        if (cancelled || requestKey !== feedRequestKeyRef.current) return;
        setPosts(result.content);
        setPage(1);
        setHasMore(result.totalPages > 1);
      } catch (err) {
        console.error('Failed to load posts:', err);
        if (cancelled) return;
        setError('사연을 불러올 수 없습니다. 다시 시도해주세요.');
        setPosts([]);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    loadPosts();
    return () => {
      cancelled = true;
    };
  }, [selectedCategory, sort]);

  useEffect(() => {
    const id = setInterval(() => setTimeTick(t => t + 1), 60_000);
    return () => clearInterval(id);
  }, []);

  const loadMorePosts = useCallback(async () => {
    if (loading || loadingMore || !hasMore) return;
    const requestKey = feedRequestKeyRef.current;
    const nextPage = page;
    setLoadingMore(true);
    try {
      const result = await postApi.list({
        category: selectedCategory || undefined,
        sort,
        page: nextPage,
        size: FEED_PAGE_SIZE,
      });
      if (requestKey !== feedRequestKeyRef.current) return;
      setPosts((prev) => mergePosts(prev, result.content));
      setPage(nextPage + 1);
      setHasMore(nextPage + 1 < result.totalPages);
    } catch (err) {
      console.error('Failed to load more posts:', err);
    } finally {
      if (requestKey === feedRequestKeyRef.current) {
        setLoadingMore(false);
      }
    }
  }, [hasMore, loading, loadingMore, page, selectedCategory, sort]);

  useEffect(() => {
    const el = loadMoreRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          void loadMorePosts();
        }
      },
      { rootMargin: '320px 0px', threshold: 0.01 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [loadMorePosts]);


  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh' }}>
      <div style={{ padding: '18px 22px 90px' }}>
        {/* 상단 헤더: 다시봄 광장 + 검색 아이콘 + 유저 칩 */}
        <BrandBar title="다시봄 광장" user={user} onSearchOpen={() => setSearchPanelOpen(true)} />

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

        {/* 정렬 토글 */}
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
            <div ref={loadMoreRef} data-testid="feed-load-more-sentinel" style={{ height: 1 }} />
            {loadingMore && (
              <div style={{ textAlign: 'center', padding: '10px 0 2px', color: 'var(--L-sub)', fontSize: 12.5 }}>
                더 불러오는 중...
              </div>
            )}
          </div>
        )}

        {/* 빈 상태 */}
        {!loading && posts.length === 0 && !error && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)' }}>
            <div style={{ fontSize: 14, marginBottom: 12 }}>
              아직 사연이 없습니다
            </div>
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

      {searchPanelOpen && (
        <SearchPanel
          currentCategory={selectedCategory}
          onCategorySelect={(id) => setSelectedCategory(id)}
          onClose={() => setSearchPanelOpen(false)}
        />
      )}
    </div>
  );
}
