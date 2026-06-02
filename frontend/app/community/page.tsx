'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { FeedCard } from '@/components/community/c3';
import { useUserStore } from '@/lib/store/userStore';

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
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [sort, setSort] = useState<'latest' | 'recommended'>('latest');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const categoryOptions = [
    { id: '', label: '전체' },
    ...C3_CATS,
  ];

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
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingTop: 16 }}>
      {/* 상단 헤더 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingLeft: 20,
        paddingRight: 20,
        marginBottom: 24,
      }}>
        <h1 style={{
          fontSize: 28,
          fontFamily: 'var(--font-serif)',
          color: 'var(--L-ink)',
          fontWeight: 500,
          margin: 0,
        }}>
          다시봄 광장
        </h1>
        {/* 우상단 로그인/닉네임 */}
        <button
          onClick={() => router.push(user ? '/profile' : '/login')}
          style={{
            background: 'none',
            border: '1px solid var(--L-border)',
            borderRadius: 999,
            padding: '6px 12px',
            fontSize: 13,
            color: user ? 'var(--L-ink)' : 'var(--L-sub)',
            cursor: 'pointer',
            fontFamily: 'var(--font-sans)',
            whiteSpace: 'nowrap',
          }}
        >
          {user ? (user.nickname || '내 정보') : '로그인'}
        </button>
      </div>

      {/* 카테고리 필터 — 가로 스크롤 칩 */}
      <div style={{ marginBottom: 20, paddingLeft: 20, paddingRight: 20, overflow: 'hidden' }}>
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
                  border: `1.5px solid ${isSelected ? 'var(--L-ink)' : 'var(--L-border)'}`,
                  background: isSelected ? 'var(--L-ink)' : 'transparent',
                  color: isSelected ? 'white' : 'var(--L-ink)',
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

      {/* 정렬 토글 */}
      <div style={{
        display: 'flex',
        gap: 12,
        paddingLeft: 20,
        paddingRight: 20,
        marginBottom: 20,
        justifyContent: 'flex-end',
      }}>
        <button
          onClick={() => setSort('latest')}
          style={{
            background: 'none',
            border: 'none',
            fontSize: 13,
            fontWeight: sort === 'latest' ? 600 : 400,
            color: sort === 'latest' ? 'var(--L-ink)' : 'var(--L-sub)',
            cursor: 'pointer',
            transition: 'all 0.15s',
          }}
        >
          최신순
        </button>
        <button
          onClick={() => setSort('recommended')}
          style={{
            background: 'none',
            border: 'none',
            fontSize: 13,
            fontWeight: sort === 'recommended' ? 600 : 400,
            color: sort === 'recommended' ? 'var(--L-ink)' : 'var(--L-sub)',
            cursor: 'pointer',
            transition: 'all 0.15s',
          }}
        >
          추천순
        </button>
      </div>

      {/* 로딩 상태 */}
      {loading && (
        <div style={{
          textAlign: 'center',
          padding: '40px 20px',
          color: 'var(--L-sub)',
        }}>
          불러오는 중...
        </div>
      )}

      {/* 에러 상태 */}
      {error && (
        <div
          style={{
            paddingLeft: 20,
            paddingRight: 20,
            marginBottom: 20,
          }}
        >
          <div
            style={{
              padding: '16px',
              background: '#FEE',
              border: '1px solid #F99',
              borderRadius: 8,
              fontSize: 13,
              color: '#C33',
            }}
          >
            {error}
          </div>
        </div>
      )}

      {/* 사연 목록 */}
      {!loading && posts.length > 0 && (
        <div data-testid="feed-post-list" style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          paddingLeft: 20,
          paddingRight: 20,
        }}>
          {posts.map((post) => (
            <FeedCard
              key={post.id}
              href={`/community/${post.id}`}
              cat={getCategoryLabel(post.category)}
              title={post.title}
              authorPct={post.authorPct || 50}
              voteCount={post.voteCount || 0}
              paired={post.paired || false}
            />
          ))}
        </div>
      )}

      {/* 빈 상태 */}
      {!loading && posts.length === 0 && !error && (
        <div style={{
          textAlign: 'center',
          padding: '40px 20px',
          color: 'var(--L-sub)',
        }}>
          <div style={{ fontSize: 14, marginBottom: 12 }}>아직 사연이 없습니다</div>
          <Link
            href="/community/new"
            style={{
              display: 'inline-block',
              padding: '10px 16px',
              background: 'var(--L-ink)',
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

      {/* 고정 하단 버튼 */}
      <div style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        padding: '16px 20px 24px',
        background: 'linear-gradient(transparent, var(--L-bg) 30%)',
        zIndex: 10,
      }}>
        <Link
          href="/community/new"
          style={{
            display: 'block',
            width: '100%',
            padding: '14px 16px',
            background: 'var(--L-ink)',
            color: 'white',
            textAlign: 'center',
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 500,
            textDecoration: 'none',
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.opacity = '0.85';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.opacity = '1';
          }}
        >
          내 사연 올리기
        </Link>
      </div>
    </div>
  );
}
