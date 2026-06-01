'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { CATEGORIES } from '@/lib/constants/categories';

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays === 0) {
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours === 0) {
      const diffMins = Math.floor(diffMs / (1000 * 60));
      return diffMins > 0 ? `${diffMins}분 전` : '방금';
    }
    return `${diffHours}시간 전`;
  }
  if (diffDays === 1) return '어제';
  if (diffDays < 7) return `${diffDays}일 전`;
  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
}

function getCategoryLabel(categoryId: string): string {
  for (const major of CATEGORIES) {
    if (major.id === categoryId) return major.label;
  }
  return categoryId;
}

export default function CommunityFeedPage() {
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const categoryOptions = [
    { id: '', label: '전체' },
    ...CATEGORIES.map(cat => ({ id: cat.id, label: cat.label })),
  ];

  useEffect(() => {
    const loadPosts = async () => {
      try {
        setLoading(true);
        setError(null);
        const result = await postApi.list({ category: selectedCategory || undefined });
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
  }, [selectedCategory]);

  return (
    <div>
      {/* 카테고리 필터 — 가로 스크롤 칩 */}
      <div style={{ marginBottom: 20, overflow: 'hidden' }}>
        <div
          style={{
            display: 'flex',
            gap: 8,
            overflowX: 'auto',
            scrollbarWidth: 'none',
            WebkitOverflowScrolling: 'touch',
            paddingBottom: 4,
          }}
        >
          {categoryOptions.map((opt) => {
            const isSelected = selectedCategory === opt.id;
            return (
              <button
                key={opt.id}
                onClick={() => setSelectedCategory(opt.id)}
                style={{
                  flexShrink: 0,
                  padding: '8px 14px',
                  borderRadius: 999,
                  border: `1.5px solid ${isSelected ? 'var(--P-ink)' : 'var(--P-border)'}`,
                  background: isSelected ? 'var(--P-ink)' : 'white',
                  color: isSelected ? 'white' : 'var(--P-ink)',
                  fontSize: 13,
                  fontWeight: isSelected ? 600 : 400,
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  letterSpacing: '-0.2px',
                  whiteSpace: 'nowrap',
                }}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 로딩 상태 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--P-sub)' }}>
          불러오는 중...
        </div>
      )}

      {/* 에러 상태 */}
      {error && (
        <div
          style={{
            padding: '16px',
            background: '#FEE',
            border: '1px solid #F99',
            borderRadius: 8,
            fontSize: 13,
            color: '#C33',
            marginBottom: 20,
          }}
        >
          {error}
        </div>
      )}

      {/* 사연 목록 */}
      {!loading && posts.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {posts.map((post) => (
            <Link
              key={post.id}
              href={`/community/${post.id}`}
              data-testid="community-post-link"
              style={{
                padding: '16px',
                background: 'white',
                border: '1px solid var(--P-border)',
                borderRadius: 10,
                textDecoration: 'none',
                color: 'inherit',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'color-mix(in srgb, var(--P-sub) 3%, white)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'white';
              }}
            >
              <div style={{ marginBottom: 8 }}>
                <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 6, lineHeight: 1.4 }}>
                  {post.title}
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 12, color: 'var(--P-sub)' }}>
                    {getCategoryLabel(post.category)}
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--P-sub)', opacity: 0.5 }}>·</span>
                  {/* 공개/비공개 뱃지 */}
                  <span style={{
                    fontSize: 11,
                    padding: '2px 7px',
                    borderRadius: 999,
                    background: post.visibility === 'PUBLIC' ? 'var(--P-border)' : '#F0EBE5',
                    color: 'var(--P-ink)',
                    fontWeight: 500,
                  }}>
                    {post.visibility === 'PUBLIC' ? '투표' : 'AI 배심원'}
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--P-sub)' }}>{post.voteCount}명</span>
                </div>
              </div>
              <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>
                {formatDate(post.createdAt)}
              </div>
            </Link>
          ))}
        </div>
      )}

      {/* 빈 상태 */}
      {!loading && posts.length === 0 && !error && (
        <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--P-sub)' }}>
          <div style={{ fontSize: 14, marginBottom: 12 }}>아직 사연이 없습니다</div>
          <Link
            href="/community/new"
            style={{
              display: 'inline-block',
              padding: '10px 16px',
              background: 'var(--P-ink)',
              color: 'white',
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
  );
}
