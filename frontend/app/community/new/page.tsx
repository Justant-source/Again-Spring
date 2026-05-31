'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { postApi } from '@/lib/api/community/postApi';
import { CATEGORIES } from '@/lib/constants/categories';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';
import LegalNoticeBox from '@/components/shared/LegalNoticeBox';

type Step = 1 | 2 | 3;

export default function CommunityNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [step, setStep] = useState<Step>(1);
  const [bodyRaw, setBodyRaw] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0]?.id || '');
  const [visibility, setVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PRIVATE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [crisisOpen, setCrisisOpen] = useState(false);
  const [generatedPost, setGeneratedPost] = useState<{ id: string; title: string; bodyPublished: string } | null>(null);

  // 세션스토리지에서 복원 (결과화면 "다음 단계 선택"에서 올 때)
  useEffect(() => {
    // 결과화면에서 선택한 visibility 읽기 (버튼 클릭 시 저장)
    const presetVisibility = sessionStorage.getItem('community-draft-visibility') as 'PUBLIC' | 'PRIVATE' | null;
    if (presetVisibility) {
      setVisibility(presetVisibility);
      sessionStorage.removeItem('community-draft-visibility');
    }

    if (searchParams.get('from') === 'session') {
      try {
        const stored = sessionStorage.getItem('community-draft');
        if (stored) {
          const draft = JSON.parse(stored);
          setBodyRaw(draft.bodyRaw || '');
          setCategory(draft.category || CATEGORIES[0]?.id || '');
          // visibility는 위에서 이미 설정했으므로 draft 값은 fallback으로만 사용
          if (!presetVisibility) {
            setVisibility(draft.visibility || 'PRIVATE');
          }
          sessionStorage.removeItem('community-draft');
        }
      } catch (err) {
        console.error('Failed to restore draft:', err);
      }
    }
  }, [searchParams]);

  const handleCreatePost = async () => {
    if (!bodyRaw.trim()) {
      setError('사연을 입력해주세요');
      return;
    }

    if (!category) {
      setError('카테고리를 선택해주세요');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const result = await postApi.create({
        bodyRaw: bodyRaw.trim(),
        category,
        visibility,
      });

      setGeneratedPost({
        id: result.id,
        title: result.title,
        bodyPublished: result.bodyPublished,
      });
      setStep(2);
    } catch (err) {
      console.error('Failed to create post:', err);
      setError('사연을 생성할 수 없습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async () => {
    if (!generatedPost) return;
    try {
      setLoading(true);
      setError(null);
      setStep(3);
      setTimeout(() => {
        router.push(`/community/${generatedPost.id}`);
      }, 500);
    } finally {
      setLoading(false);
    }
  };

  const handleCrisisCheck = () => {
    const result = checkKeywords(bodyRaw);
    if (result.level === 1) {
      setCrisisOpen(true);
      return true;
    }
    return false;
  };

  const handleInputChange = (value: string) => {
    setBodyRaw(value);
    setError(null);
    // 위기감지 이중방어 — FE 즉시 차단 (BE도 재검사)
    const kw = checkKeywords(value);
    if (kw.level === 1) {
      setCrisisOpen(true);
    }
  };

  if (step === 1) {
    return (
      <div>
        <div style={{ marginBottom: 20 }}>
          <label style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8, display: 'block' }}>
            카테고리
          </label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 12px',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 13,
              background: 'white',
              color: 'var(--P-ink)',
              outline: 'none',
              cursor: 'pointer',
              marginBottom: 16,
            }}
          >
            {CATEGORIES.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.label}
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8, display: 'block' }}>
            공개 범위
          </label>
          <div style={{ display: 'flex', gap: 16 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <input
                type="radio"
                name="visibility"
                value="PUBLIC"
                checked={visibility === 'PUBLIC'}
                onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'PRIVATE')}
                style={{ cursor: 'pointer' }}
              />
              <span style={{ fontSize: 13 }}>공개 (투표)</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <input
                type="radio"
                name="visibility"
                value="PRIVATE"
                checked={visibility === 'PRIVATE'}
                onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'PRIVATE')}
                style={{ cursor: 'pointer' }}
              />
              <span style={{ fontSize: 13 }}>비공개 (배심원)</span>
            </label>
          </div>
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8, display: 'block' }}>
            사연
          </label>
          <textarea
            data-testid="post-body-input"
            value={bodyRaw}
            onChange={(e) => handleInputChange(e.target.value)}
            placeholder="갈등 상황을 적어주세요"
            style={{
              width: '100%',
              minHeight: 200,
              padding: '12px 14px',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 13,
              fontFamily: 'inherit',
              lineHeight: 1.6,
              outline: 'none',
              resize: 'vertical',
              color: 'var(--P-ink)',
              background: 'white',
            }}
          />
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

        <button
          onClick={handleCreatePost}
          disabled={loading}
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
            opacity: loading ? 0.6 : 1,
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            if (!loading) {
              e.currentTarget.style.opacity = '0.85';
            }
          }}
          onMouseLeave={(e) => {
            if (!loading) {
              e.currentTarget.style.opacity = '1';
            }
          }}
        >
          {loading ? '생성 중...' : 'AI가 중립화해줘요'}
        </button>

        <CrisisResourceModal
          open={crisisOpen}
          onClose={() => setCrisisOpen(false)}
          severity="advisory"
        />
      </div>
    );
  }

  if (step === 2) {
    return (
      <div data-testid="post-compose-preview">
        <div style={{ marginBottom: 20 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: 'var(--P-ink)', marginBottom: 12 }}>
            {generatedPost?.title}
          </h2>
          <div
            style={{
              padding: '16px',
              background: 'var(--P-card)',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 13,
              lineHeight: 1.8,
              color: 'var(--P-ink)',
              whiteSpace: 'pre-wrap',
            }}
          >
            {generatedPost?.bodyPublished}
          </div>
        </div>

        <LegalNoticeBox data-testid="ratio-legal-notice" />

        <div style={{ display: 'flex', gap: 12, marginTop: 20 }}>
          <button
            onClick={() => setStep(1)}
            style={{
              flex: 1,
              padding: '14px 16px',
              background: 'var(--P-card)',
              border: '1px solid var(--P-border)',
              color: 'var(--P-ink)',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'color-mix(in srgb, var(--P-sub) 6%, var(--P-card))';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'var(--P-card)';
            }}
          >
            다시 쓰기
          </button>
          <button
            onClick={handlePublish}
            disabled={loading}
            style={{
              flex: 1,
              padding: '14px 16px',
              background: 'var(--P-ink)',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              opacity: loading ? 0.6 : 1,
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              if (!loading) {
                e.currentTarget.style.opacity = '0.85';
              }
            }}
            onMouseLeave={(e) => {
              if (!loading) {
                e.currentTarget.style.opacity = '1';
              }
            }}
          >
            게시하기
          </button>
        </div>
      </div>
    );
  }

  if (step === 3) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 20px' }}>
        <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 12 }}>
          게시됐습니다
        </div>
        <div style={{ fontSize: 13, color: 'var(--P-sub)' }}>
          잠시 후 사연 상세 페이지로 이동합니다
        </div>
      </div>
    );
  }

  return null;
}
