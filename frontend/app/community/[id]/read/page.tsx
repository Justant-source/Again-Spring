'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { postApi, PostDetail } from '@/lib/api/community/postApi';
import { AUTHOR, PARTNER, AUTHOR_BG, PARTNER_BG } from '@/lib/constants/factionColors';

interface PageProps {
  params: { id: string };
}

export default function C3StoryRead({ params }: PageProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialSide = (searchParams.get('side') as 'g' | 'r') || 'g';

  const [post, setPost] = useState<PostDetail | null>(null);
  const [side, setSide] = useState<'g' | 'r'>(initialSide);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadPost = async () => {
      try {
        setLoading(true);
        const postData = await postApi.get(params.id);
        setPost(postData);
      } catch (err) {
        console.error('Failed to load post:', err);
        setError('사연을 불러올 수 없습니다');
      } finally {
        setLoading(false);
      }
    };

    loadPost();
  }, [params.id]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--P-sub)' }}>
        불러오는 중...
      </div>
    );
  }

  if (error || !post) {
    return (
      <div
        style={{
          padding: '16px',
          background: '#FEE',
          border: '1px solid #F99',
          borderRadius: 8,
          fontSize: 13,
          color: '#C33',
          textAlign: 'center',
        }}
      >
        {error || '사연을 찾을 수 없습니다'}
      </div>
    );
  }

  const bgColor = side === 'g' ? AUTHOR_BG : PARTNER_BG;
  const textColor = side === 'g' ? AUTHOR : PARTNER;
  const body = side === 'g' ? post.bodyPublished : post.partnerBodyPublished;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '16px' }}>
      {/* 상단 */}
      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button
          onClick={() => router.back()}
          style={{
            background: 'none',
            border: 'none',
            fontSize: 14,
            fontWeight: 500,
            color: 'var(--P-ink)',
            cursor: 'pointer',
            padding: 0,
          }}
        >
          ‹
        </button>
        <h1
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: 'var(--P-ink)',
            margin: 0,
            flex: 1,
            textAlign: 'center',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            paddingLeft: 12,
            paddingRight: 12,
          }}
        >
          {post.title}
        </h1>
        <div style={{ width: 24 }} />
      </div>

      {/* 진영 탭 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <button
          onClick={() => setSide('g')}
          style={{
            flex: 1,
            padding: '10px 12px',
            background: side === 'g' ? 'transparent' : 'var(--P-card)',
            border: side === 'g' ? `2px solid ${AUTHOR}` : '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 12,
            fontWeight: 600,
            color: side === 'g' ? AUTHOR : 'var(--P-ink)',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            justifyContent: 'center',
          }}
        >
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: AUTHOR }} />
          작성자의 이야기
        </button>
        <button
          onClick={() => setSide('r')}
          style={{
            flex: 1,
            padding: '10px 12px',
            background: side === 'r' ? 'transparent' : 'var(--P-card)',
            border: side === 'r' ? `2px solid ${PARTNER}` : '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 12,
            fontWeight: 600,
            color: side === 'r' ? PARTNER : 'var(--P-ink)',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            justifyContent: 'center',
          }}
        >
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: PARTNER }} />
          상대방의 이야기
        </button>
      </div>

      {/* 전문 카드 */}
      <div
        style={{
          background: bgColor,
          borderRadius: 12,
          padding: '16px 14px',
          marginBottom: 20,
        }}
      >
        <p
          style={{
            margin: 0,
            fontSize: 15,
            fontFamily: 'var(--font-serif)',
            lineHeight: 1.85,
            color: 'var(--P-ink)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {body}
        </p>
      </div>

      {/* 액션 칩: 공감 + 신고 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
        <button
          style={{
            padding: '6px 12px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 6,
            fontSize: 12,
            color: 'var(--P-ink)',
            cursor: 'pointer',
            fontWeight: 500,
          }}
        >
          공감 N
        </button>
        <button
          style={{
            padding: '6px 12px',
            background: 'transparent',
            border: '1px solid var(--P-border)',
            borderRadius: 6,
            fontSize: 12,
            color: 'var(--P-sub)',
            cursor: 'pointer',
            fontWeight: 500,
          }}
        >
          신고
        </button>
      </div>

      {/* 반대 진영 전환 */}
      <button
        onClick={() => setSide(side === 'g' ? 'r' : 'g')}
        style={{
          width: '100%',
          padding: '12px 16px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          borderRadius: 8,
          fontSize: 12,
          fontWeight: 600,
          color: 'var(--P-ink)',
          cursor: 'pointer',
        }}
      >
        {side === 'g' ? '상대방' : '작성자'} 이야기 읽기 ›
      </button>
    </div>
  );
}
