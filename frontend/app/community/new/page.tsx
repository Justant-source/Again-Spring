'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postApi, PostCreateRequest } from '@/lib/api/community/postApi';
import { GuestNoticeModal } from '@/components/auth/GuestNoticeModal';
import { JurorPicker } from '@/components/community/c3/JurorPicker';
import { useUserStore } from '@/lib/store/userStore';
import { GRN, GRN_BG } from '@/lib/constants/factionColors';

// C3 대분류 카테고리 — id는 BE PostCategory enum 이름과 1:1 매핑
const C3_CATEGORIES = [
  { id: 'COUPLE',  label: '연인' },
  { id: 'MARRIED', label: '부부' },
  { id: 'FRIEND',  label: '친구' },
  { id: 'FAMILY',  label: '가족' },
  { id: 'WORK',    label: '직장' },
  { id: 'OTHER',   label: '기타' },
];

type Step = 'compose' | 'mode' | 'analyzing';

export default function CommunityNewPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const isGuest = user?.isGuest ?? true;

  const [step, setStep] = useState<Step>('compose');
  const [title, setTitle] = useState('');
  const [bodyRaw, setBodyRaw] = useState('');
  const [category, setCategory] = useState(C3_CATEGORIES[0].id);
  const [jurorCount, setJurorCount] = useState(3);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showGuestNotice, setShowGuestNotice] = useState(false);
  const [selectedMode, setSelectedMode] = useState<'PUBLIC' | 'PRIVATE' | null>(null);
  const [generatedPost, setGeneratedPost] = useState<{
    id: string;
    title: string;
    bodyPublished: string;
  } | null>(null);


  const handleComposeSubmit = async () => {
    if (!title.trim()) {
      setError('제목을 입력해주세요');
      return;
    }
    if (!bodyRaw.trim()) {
      setError('본문을 입력해주세요');
      return;
    }
    if (bodyRaw.trim().length > 600) {
      setError('본문은 600자 이내여야 합니다');
      return;
    }
    if (!category) {
      setError('카테고리를 선택해주세요');
      return;
    }

    if (isGuest) {
      setShowGuestNotice(true);
    } else {
      setStep('mode');
    }
  };

  const handleModeSelect = async (visibility: 'PUBLIC' | 'PRIVATE') => {
    try {
      setLoading(true);
      setError(null);
      setStep('analyzing');

      const request: PostCreateRequest = {
        bodyRaw: bodyRaw.trim(),
        category,
        visibility,
        userTitle: title.trim(),
        jurorCount,
      };

      const result = await postApi.create(request);
      setGeneratedPost({
        id: result.id,
        title: result.title,
        bodyPublished: result.bodyPublished,
      });

      // 약간의 지연 후 상세 페이지로 이동
      setTimeout(() => {
        router.push(`/community/${result.id}`);
      }, 800);
    } catch (err) {
      console.error('Failed to create post:', err);
      setError('사연 생성에 실패했습니다. 다시 시도해주세요.');
      setStep('mode');
    } finally {
      setLoading(false);
    }
  };

  // Step 1: 사연 작성
  if (step === 'compose') {
    return (
      <div style={{ background: 'var(--L-bg)', minHeight: '100vh' }}>
        {/* 헤더 바 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 20px',
          borderBottom: '1px solid var(--L-border)',
          background: 'var(--L-bg)',
        }}>
          <button
            onClick={() => router.back()}
            style={{
              background: 'none',
              border: 'none',
              fontSize: 18,
              cursor: 'pointer',
              color: 'var(--L-ink)',
            }}
          >
            ✕
          </button>
          <h2 style={{
            fontSize: 16,
            fontWeight: 600,
            color: 'var(--L-ink)',
            margin: 0,
          }}>
            사연 올리기
          </h2>
          <div style={{ width: 28 }} />
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
                {bodyRaw.length} / 600
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
            setStep('mode');
          }}
        />
      </div>
    );
  }

  // Step 2: 모드 선택 — 카드를 눌러 선택, 하단 버튼으로 진행
  if (step === 'mode') {
    const isPub = selectedMode === 'PUBLIC';
    const isPrv = selectedMode === 'PRIVATE';
    return (
      <div style={{ background: 'var(--L-bg)', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        <div style={{ flex: 1, padding: '24px 26px 120px' }}>
          {/* 제목 */}
          <h2 data-testid="mode-step-heading" className="serif" style={{ fontSize: 22, lineHeight: 1.45, marginBottom: 24, color: 'var(--L-ink)', fontWeight: 500 }}>
            이 사연,<br />어떻게 올릴까요?
          </h2>

          {/* 옵션 1: 바로 광장에 올리기 */}
          <div
            data-testid="mode-public-card"
            onClick={() => setSelectedMode('PUBLIC')}
            style={{
              padding: '20px 18px',
              border: `2px solid ${isPub ? GRN : 'var(--L-border)'}`,
              background: isPub ? GRN_BG : 'transparent',
              borderRadius: 12,
              cursor: 'pointer',
              marginBottom: 11,
              transition: 'all .15s',
            }}
          >
            <div style={{ fontSize: 17, fontWeight: 500, color: 'var(--L-ink)', marginBottom: 4 }}>바로 광장에 올리기</div>
            <div style={{ fontSize: 12.5, color: 'var(--L-sub)' }}>익명 투표</div>
          </div>

          {/* 옵션 2: 상대를 초대하기 */}
          <div
            data-testid="mode-private-card"
            onClick={() => { if (!isGuest) setSelectedMode('PRIVATE'); }}
            style={{
              padding: '20px 18px',
              border: `2px solid ${isPrv ? 'var(--L-ink)' : 'var(--L-border)'}`,
              background: isPrv ? 'var(--L-card)' : 'transparent',
              borderRadius: 12,
              cursor: isGuest ? 'not-allowed' : 'pointer',
              opacity: isGuest ? 0.55 : 1,
              marginBottom: 11,
              transition: 'all .15s',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              {isGuest && <span style={{ fontSize: 14 }}>🔒</span>}
              <div>
                <div style={{ fontSize: 17, fontWeight: 500, color: isGuest ? 'var(--L-sub)' : 'var(--L-ink)', marginBottom: 4 }}>상대를 초대하기</div>
                <div style={{ fontSize: 12.5, color: 'var(--L-sub)' }}>두 입장을 나란히</div>
              </div>
            </div>
          </div>

          {/* JurorPicker — 두 옵션 아래 공통 */}
          <div style={{ padding: '14px 16px', border: '1px solid var(--L-border)', borderRadius: 12, background: 'var(--L-card)', marginBottom: 11 }}>
            <JurorPicker defaultValue={jurorCount} onChange={setJurorCount} />
          </div>

          {/* 게스트 안내 */}
          {isGuest && (
            <div style={{ padding: '13px 15px', border: '1px solid var(--L-border)', borderRadius: 10, background: 'var(--L-card)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
              <span style={{ fontSize: 12.5, color: 'var(--L-ink)', lineHeight: 1.5 }}>회원가입 후 상대를 초대할 수 있어요</span>
              <span
                onClick={() => router.push('/signup')}
                style={{ fontSize: 12.5, color: 'var(--L-point)', fontWeight: 500, whiteSpace: 'nowrap', cursor: 'pointer' }}
              >
                가입하기
              </span>
            </div>
          )}
        </div>

        {/* 하단 고정 버튼 */}
        <div style={{
          position: 'fixed',
          left: 0, right: 0, bottom: 0,
          padding: '24px 26px 24px',
          background: `linear-gradient(transparent, var(--L-bg) 30%)`,
        }}>
          <button
            onClick={() => {
              if (!selectedMode) return;
              handleModeSelect(selectedMode);
            }}
            data-testid="mode-submit-btn"
            disabled={!selectedMode || loading}
            style={{
              width: '100%',
              padding: '15px 0',
              borderRadius: 4,
              border: 'none',
              background: selectedMode && !loading ? 'var(--L-ink)' : 'var(--L-border)',
              color: selectedMode && !loading ? 'var(--L-bg)' : 'var(--L-sub)',
              fontSize: 15,
              fontWeight: 500,
              fontFamily: 'var(--font-sans)',
              cursor: selectedMode && !loading ? 'pointer' : 'default',
              transition: 'all .15s',
            }}
          >
            {loading ? '올리는 중...' : selectedMode === 'PRIVATE' ? '상대 초대하기' : '바로 올리기'}
          </button>
        </div>
      </div>
    );
  }

  // Step 3: 분석 중
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
