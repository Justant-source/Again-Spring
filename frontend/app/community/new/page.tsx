'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { postApi, PostCreateRequest } from '@/lib/api/community/postApi';
import { GuestNoticeModal } from '@/components/auth/GuestNoticeModal';
import { JurorPicker, UserChip } from '@/components/community/c3';
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

type Step = 'compose' | 'mode' | 'analyzing';

export default function CommunityNewPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  useGuestInit();
  const isGuest = user?.isGuest ?? true;

  const [step, setStep] = useState<Step>('compose');
  const [title, setTitle] = useState('');
  const [bodyRaw, setBodyRaw] = useState('');
  const [category, setCategory] = useState(C3_CATEGORIES[0].id);
  const [jurorCount, setJurorCount] = useState(3);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showGuestNotice, setShowGuestNotice] = useState(false);
  const [selectedMode, setSelectedMode] = useState<'PUBLIC' | 'PRIVATE'>('PUBLIC');


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

      // PUBLIC인 경우만 분석 화면 표시
      if (visibility === 'PUBLIC') {
        setStep('analyzing');
      }

      const request: PostCreateRequest = {
        bodyRaw: bodyRaw.trim(),
        category,
        visibility,
        userTitle: title.trim(),
        jurorCount,
      };

      // 등록 요청과 최소 1초 대기를 병렬 실행
      const [result] = await Promise.all([
        postApi.create(request),
        new Promise(r => setTimeout(r, 1000)),
      ]);

      // PRIVATE: 초대 화면으로 이동, PUBLIC: 내 결과 화면으로 이동 (배심원 의견 폴링)
      if (visibility === 'PRIVATE') {
        router.push(`/community/${result.id}/invite`);
      } else {
        router.push(`/community/${result.id}`);
      }
    } catch (err: unknown) {
      console.error('Failed to create post:', err);
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('CRISIS_DETECTED')) {
        setError('작성하신 내용이 정책에 위배됩니다. 다시 시도해주세요.');
      } else {
        setError('사연 등록에 실패했습니다. 다시 시도해주세요.');
      }
      setStep('mode');
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

  // Step 2: 모드 선택 — 디자인 C3_Mode / C3_ModeGuest 그대로
  if (step === 'mode') {
    const sel = selectedMode ?? 'PUBLIC';
    const cardStyle = (on: boolean): React.CSSProperties => ({
      padding: '20px 18px',
      borderRadius: 12,
      cursor: 'pointer',
      border: `2px solid ${on ? 'var(--L-ink)' : 'var(--L-border)'}`,
      background: on ? 'var(--L-card)' : 'transparent',
      transition: 'border-color .15s, background .15s',
    });

    return (
      <div style={{ background: 'var(--L-bg)', minHeight: '100vh', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '0 26px' }}>

        {/* 게스트 칩 */}
        {isGuest && (
          <div style={{ marginBottom: 18 }}>
            <span style={{ fontSize: 11, color: 'var(--L-sub)', border: '1px solid var(--L-border)', borderRadius: 999, padding: '3px 10px' }}>게스트</span>
          </div>
        )}

        {/* 제목 */}
        <div data-testid="mode-step-heading" className="serif" style={{ fontSize: 22, lineHeight: 1.45, marginBottom: 24, color: 'var(--L-ink)', fontWeight: 500 }}>
          이 사연,<br />어떻게 올릴까요?
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
          {/* 옵션 1: 바로 광장에 올리기 */}
          <div
            data-testid="mode-public-card"
            onClick={() => setSelectedMode('PUBLIC')}
            style={cardStyle(sel === 'PUBLIC')}
          >
            <div style={{ fontSize: 17, fontWeight: 500, color: 'var(--L-ink)' }}>바로 광장에 올리기</div>
            <div style={{ fontSize: 12.5, color: 'var(--L-sub)', marginTop: 4 }}>익명 투표</div>
          </div>

          {/* 옵션 2: 상대를 초대하기 */}
          <div
            data-testid="mode-private-card"
            onClick={() => { if (!isGuest) setSelectedMode('PRIVATE'); }}
            style={{
              ...cardStyle(!isGuest && sel === 'PRIVATE'),
              opacity: isGuest ? 0.55 : 1,
              cursor: isGuest ? 'default' : 'pointer',
              border: isGuest ? '1px solid var(--L-border)' : cardStyle(sel === 'PRIVATE').border,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              {isGuest && <span style={{ fontSize: 13, color: 'var(--L-sub)' }}>🔒</span>}
              <span style={{ fontSize: 17, fontWeight: 500, color: isGuest ? 'var(--L-sub)' : 'var(--L-ink)' }}>상대를 초대하기</span>
            </div>
            <div style={{ fontSize: 12.5, color: 'var(--L-sub)', marginTop: 4 }}>두 입장을 나란히</div>
          </div>

          {/* 게스트 인라인 안내 — disabled 카드 바로 아래 */}
          {isGuest && (
            <div style={{ marginTop: -4, fontSize: 12.5, color: 'var(--L-sub)', lineHeight: 1.5, paddingLeft: 2 }}>
              회원가입 후 상대를 초대할 수 있습니다{' '}
              <span
                onClick={() => router.push('/signup')}
                style={{ color: 'var(--L-point)', fontWeight: 500, textDecoration: 'underline', textUnderlineOffset: 2, cursor: 'pointer' }}
              >
                가입하기
              </span>
            </div>
          )}

          {/* AI 배심원 선택기 */}
          <JurorPicker defaultValue={jurorCount} onChange={setJurorCount} />
        </div>

        {/* 하단 고정 버튼 */}
        <div style={{
          position: 'fixed',
          left: 0, right: 0, bottom: 0,
          padding: '24px 26px',
          maxWidth: 640, margin: '0 auto',
          background: 'linear-gradient(transparent, var(--L-bg) 30%)',
        }}>
          <button
            data-testid="mode-submit-btn"
            onClick={() => handleModeSelect(sel as 'PUBLIC' | 'PRIVATE')}
            disabled={loading}
            style={{
              width: '100%', padding: '15px 0', borderRadius: 4, border: 'none',
              background: loading ? 'var(--L-border)' : 'var(--L-ink)',
              color: loading ? 'var(--L-sub)' : 'var(--L-bg)',
              fontSize: 15, fontWeight: 500, fontFamily: 'var(--font-sans)',
              cursor: loading ? 'default' : 'pointer', transition: 'all .15s',
            }}
          >
            {loading ? '올리는 중...' : sel === 'PRIVATE' ? '상대 초대하기' : '바로 올리기'}
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
