'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import { postInviteApi } from '@/lib/api/community/postInviteApi';
import { SideStory } from '@/components/community/c3/SideStory';
import { UserChip } from '@/components/community/c3/UserChip';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';

interface PostPreview {
  postId: string;
  userTitle: string;
  authorBodyPublished: string;
  category: string;
}

export default function PartnerAnswerPage() {
  const router = useRouter();
  const params = useParams();
  const token = params?.token as string;
  const user = useUserStore((s) => s.user);
  useGuestInit();

  const [post, setPost] = useState<PostPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  // 제목은 읽기 전용 — 작성자 제목 그대로 사용
  const [bodyRaw, setBodyRaw] = useState('');

  // Load post preview on mount
  useEffect(() => {
    if (!token) return;

    const loadPreview = async () => {
      try {
        setLoading(true);
        const data = await postInviteApi.getByToken(token);
        setPost(data);
      } catch (err) {
        console.error('Failed to load preview:', err);
        setError('초대 링크가 유효하지 않습니다');
      } finally {
        setLoading(false);
      }
    };

    loadPreview();
  }, [token]);

  const handleSubmit = async () => {
    if (!bodyRaw.trim()) {
      setError('답변을 입력해주세요');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await postInviteApi.submitAnswer(token, {
        userTitle: post?.userTitle || '상대방',
        bodyRaw: bodyRaw.trim(),
      });
      router.push(post?.postId ? `/community/${post.postId}` : '/community');
    } catch (err: unknown) {
      console.error('Failed to submit answer:', err);
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setError('이미 답변이 등록된 초대 링크입니다.');
      } else {
        setError('답변을 제출할 수 없습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontSize: 14, color: 'var(--P-sub)' }}>로드 중...</div>
      </div>
    );
  }

  if (!post) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        <div style={{ fontSize: 14, color: 'var(--faction-partner)' }}>{error || '초대 링크를 찾을 수 없습니다'}</div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
      {/* Top bar: Close + Title + Anonymous indicator */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 24,
        }}
      >
        <button
          onClick={() => router.back()}
          style={{
            background: 'none',
            border: 'none',
            fontSize: 16,
            color: 'var(--P-ink)',
            cursor: 'pointer',
            padding: 0,
          }}
        >
          ✕
        </button>
        <h1
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: 'var(--faction-partner)',
            margin: 0,
            flex: 1,
            textAlign: 'center',
          }}
        >
          상대방으로 답하기
        </h1>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          {user ? (
            <UserChip user={user} />
          ) : (
            <Link
              href={`/login?next=${encodeURIComponent(`/s/${token}`)}`}
              style={{
                fontSize: 12,
                color: 'var(--P-sub)',
                textDecoration: 'none',
                padding: '4px 10px',
                border: '1px solid var(--P-border)',
                borderRadius: 999,
                whiteSpace: 'nowrap',
              }}
            >
              로그인 / 회원가입
            </Link>
          )}
        </div>
      </div>

      {/* Author's story (read-only) */}
      <div style={{ marginBottom: 28 }}>
        <SideStory
          side="g"
          label="상대방의 이야기"
          body={post.authorBodyPublished}
          clamp={false}
          selected={false}
        />
      </div>

      {/* Title — 읽기 전용 (작성자 제목 그대로) */}
      <div style={{ marginBottom: 20 }}>
        <label style={{ fontSize: 12, color: 'var(--P-sub)', display: 'block', marginBottom: 8 }}>
          제목
        </label>
        <div
          style={{
            width: '100%',
            padding: '10px 12px',
            border: '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 13,
            color: 'var(--P-sub)',
            background: 'var(--P-card)',
          }}
        >
          {post.userTitle}
        </div>
      </div>

      {/* Body textarea */}
      <div style={{ marginBottom: 20 }}>
        <label style={{ fontSize: 12, color: 'var(--P-sub)', display: 'block', marginBottom: 8 }}>
          상대방으로 답하기
        </label>
        <textarea
          value={bodyRaw}
          onChange={(e) => setBodyRaw(e.target.value)}
          placeholder="상대방의 입장에서 이야기해주세요"
          maxLength={600}
          style={{
            width: '100%',
            minHeight: 200,
            padding: '12px 14px',
            border: '1px solid var(--faction-partner)',
            borderRadius: 8,
            background: 'var(--faction-partner-bg)',
            fontSize: 13,
            fontFamily: 'var(--font-serif)',
            lineHeight: 1.6,
            color: 'var(--P-ink)',
            outline: 'none',
            resize: 'vertical',
          }}
        />
        <div
          style={{
            textAlign: 'right',
            fontSize: 11,
            color: 'var(--P-sub)',
            marginTop: 6,
          }}
        >
          {bodyRaw.length} / 600
        </div>
      </div>

      {error && (
        <div
          style={{
            padding: '12px 14px',
            background: '#FEE',
            border: '1px solid #F99',
            borderRadius: 8,
            fontSize: 12,
            color: '#C33',
            marginBottom: 20,
          }}
        >
          {error}
        </div>
      )}

      {/* Submit button */}
      <button
        onClick={handleSubmit}
        disabled={submitting}
        style={{
          width: '100%',
          padding: '14px 16px',
          background: 'var(--P-ink)',
          color: 'white',
          border: 'none',
          borderRadius: 8,
          fontSize: 14,
          fontWeight: 500,
          cursor: 'pointer',
          opacity: submitting ? 0.6 : 1,
          transition: 'all 0.2s',
        }}
      >
        {submitting ? '제출 중...' : '덧붙이기'}
      </button>
    </div>
  );
}
