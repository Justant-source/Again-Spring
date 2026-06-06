'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postApi, PostCreateRequest } from '@/lib/api/community/postApi';
import { GuestNoticeModal } from '@/components/auth/GuestNoticeModal';
import { UserChip } from '@/components/community/c3';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';
import { AUTHOR, AUTHOR_BG } from '@/lib/constants/factionColors';

// C3 대분류 카테고리 — id는 BE PostCategory enum 이름과 1:1 매핑
const C3_CATEGORIES = [
  { id: 'COUPLE',  label: '연인' },
  { id: 'MARRIED', label: '부부' },
  { id: 'FRIEND',  label: '친구' },
  { id: 'FAMILY',  label: '가족' },
  { id: 'WORK',    label: '직장' },
  { id: 'OTHER',   label: '기타' },
];

type Step = 'compose' | 'analyzing';

export default function CommunityNewPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  useGuestInit();
  const isGuest = user?.isGuest ?? true;

  const [step, setStep] = useState<Step>('compose');
  const [title, setTitle] = useState('');
  const [bodyRaw, setBodyRaw] = useState('');
  const [category, setCategory] = useState(C3_CATEGORIES[0].id);
  const jurorCount = 0; // AI 중재자 모드 숨김 처리 중
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showGuestNotice, setShowGuestNotice] = useState(false);


  const handleComposeSubmit = async () => {
    if (!title.trim()) {
      setError('제목을 입력해주세요');
      return;
    }
    if (!bodyRaw.trim()) {
      setError('본문을 입력해주세요');
      return;
    }
    if (bodyRaw.trim().length > 2000) {
      setError('본문은 2000자 이내여야 합니다');
      return;
    }
    if (!category) {
      setError('카테고리를 선택해주세요');
      return;
    }

    if (isGuest) {
      setShowGuestNotice(true);
      return;
    }
    await createAndNavigate();
  };

  const createAndNavigate = async () => {
    try {
      setLoading(true);
      setError(null);
      setStep('analyzing');

      const request: PostCreateRequest = {
        bodyRaw: bodyRaw.trim(),
        category,
        visibility: 'PUBLIC',
        userTitle: title.trim(),
        jurorCount,
      };

      const [result] = await Promise.all([
        postApi.create(request),
        new Promise(r => setTimeout(r, 1000)),
      ]);

      router.push(`/community/${result.id}`);
    } catch (err: unknown) {
      console.error('Failed to create post:', err);
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('CRISIS_DETECTED')) {
        setError('작성하신 내용이 정책에 위배됩니다. 다시 시도해주세요.');
      } else {
        setError('사연 등록에 실패했습니다. 다시 시도해주세요.');
      }
      setStep('compose');
    } finally {
      setLoading(false);
    }
  };

  // Step 1: 사연 작성
  if (step === 'compose') {
    return (
      <div style={{ background: 'var(--L-bg)', minHeight: '100vh' }}>
        {/* 헤더 바 — 3-part layout: ✕ (left) + "사연 올리기" (center) + UserChip (right) */}
        <div style={{
          padding: '14px 20px 0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <button
            onClick={() => router.back()}
            style={{
              background: 'none',
              border: 'none',
              fontSize: 17,
              color: 'var(--L-sub)',
              cursor: 'pointer',
              padding: 0,
            }}
          >
            ✕
          </button>
          <span style={{ fontSize: 13, fontWeight: 500 }}>사연 올리기</span>
          <UserChip user={user} />
        </div>

        <div style={{ padding: '20px', paddingBottom: 120 }}>
          {/* 카테고리 칩 */}
          <div style={{ marginBottom: 24 }}>
            <label style={{
              fontSize: 11,
              color: 'var(--L-sub)',
              marginBottom: 8,
              display: 'block',
              letterSpacing: '0.5px',
              fontWeight: 500,
            }}>
              카테고리
            </label>
            <div style={{
              display: 'flex',
              gap: 8,
              overflowX: 'auto',
              scrollbarWidth: 'none',
              paddingBottom: 4,
            }}>
              {C3_CATEGORIES.map((cat) => {
                const isSelected = category === cat.id;
                return (
                  <button
                    key={cat.id}
                    onClick={() => setCategory(cat.id)}
                    style={{
                      flexShrink: 0,
                      padding: '8px 14px',
                      borderRadius: 999,
                      border: `1.5px solid ${isSelected ? 'var(--L-ink)' : 'var(--L-border)'}`,
                      background: isSelected ? 'var(--L-ink)' : 'transparent',
                      color: isSelected ? 'var(--L-bg)' : 'var(--L-ink)',
                      fontSize: 13,
                      fontWeight: isSelected ? 500 : 400,
                      cursor: 'pointer',
                      transition: 'all 0.15s',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {cat.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 제목 입력 */}
          <div style={{ marginBottom: 24 }}>
            <label style={{
              fontSize: 11,
              color: 'var(--L-sub)',
              marginBottom: 8,
              display: 'block',
              letterSpacing: '0.5px',
              fontWeight: 500,
            }}>
              제목
            </label>
            <input
              data-testid="compose-title"
              type="text"
              value={title}
              onChange={(e) => {
                setTitle(e.target.value);
                setError(null);
              }}
              placeholder="제목을 입력하세요"
              style={{
                width: '100%',
                padding: '12px 0',
                borderBottom: '1px solid var(--L-ink)',
                background: 'transparent',
                fontSize: 18,
                fontFamily: 'var(--font-serif)',
                color: 'var(--L-ink)',
                outline: 'none',
                border: 'none',
                borderBottomWidth: 1,
                borderBottomStyle: 'solid',
                borderBottomColor: 'var(--L-ink)',
              }}
            />
          </div>

          {/* 본문 입력 */}
          <div style={{ marginBottom: 24 }}>
            <label style={{
              fontSize: 11,
              color: 'var(--L-sub)',
              marginBottom: 8,
              display: 'block',
              letterSpacing: '0.5px',
              fontWeight: 500,
            }}>
              본문
            </label>
            <textarea
              data-testid="compose-body"
              value={bodyRaw}
              onChange={(e) => {
                setBodyRaw(e.target.value);
                setError(null);
              }}
              placeholder="갈등 상황을 적어주세요"
              style={{
                width: '100%',
                minHeight: 160,
                padding: '12px 14px',
                border: '1px solid var(--L-border)',
                borderRadius: 6,
                fontSize: 14,
                fontFamily: 'var(--font-serif)',
                lineHeight: 1.6,
                outline: 'none',
                resize: 'vertical',
                color: 'var(--L-ink)',
                background: 'var(--L-card)',
              }}
            />
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: 8,
            }}>
              <span style={{ fontSize: 12, color: 'var(--L-sub)' }}>
                익명
              </span>
              <span data-testid="compose-char-count" style={{ fontSize: 12, color: 'var(--L-sub)' }}>
                {bodyRaw.length} / 2000
              </span>
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

          {/* 올리기 버튼 */}
          <button
            onClick={handleComposeSubmit}
            disabled={loading}
            style={{
              width: '100%',
              padding: '14px 16px',
              background: 'var(--L-ink)',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              opacity: loading ? 0.6 : 1,
              transition: 'all 0.2s',
            }}
          >
            올리기
          </button>
        </div>

        <GuestNoticeModal
          isOpen={showGuestNotice}
          onClose={() => setShowGuestNotice(false)}
          onSignup={() => router.push('/signup')}
          onContinueAsGuest={() => {
            setShowGuestNotice(false);
            createAndNavigate();
          }}
        />
      </div>
    );
  }

  // Step 2: 분석 중
  if (step === 'analyzing') {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        padding: '20px',
      }}>
        <div style={{
          width: 50,
          height: 50,
          borderTop: '3px solid var(--L-ink)',
          borderRight: '3px solid transparent',
          borderBottom: '3px solid transparent',
          borderLeft: '3px solid transparent',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
          marginBottom: 20,
        }} />
        <div style={{
          fontSize: 16,
          fontWeight: 500,
          fontFamily: 'var(--font-serif)',
          color: 'var(--L-ink)',
          textAlign: 'center',
        }}>
          시선을 모으고 있어요
        </div>
        <style>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  return null;
}
