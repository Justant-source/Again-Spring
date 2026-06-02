'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { postApi, PostCreateRequest } from '@/lib/api/community/postApi';
import { CATEGORIES } from '@/lib/constants/categories';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';
import { JurorPicker } from '@/components/community/c3/JurorPicker';
import { useUserStore } from '@/lib/store/userStore';
import { GRN, GRN_BG, RED, RED_BG } from '@/lib/constants/factionColors';

type Step = 'compose' | 'mode' | 'analyzing';

export default function CommunityNewPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const isGuest = user?.isGuest ?? true;

  const [step, setStep] = useState<Step>('compose');
  const [title, setTitle] = useState('');
  const [bodyRaw, setBodyRaw] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0]?.id || '');
  const [jurorCount, setJurorCount] = useState(3);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [crisisOpen, setCrisisOpen] = useState(false);
  const [generatedPost, setGeneratedPost] = useState<{
    id: string;
    title: string;
    bodyPublished: string;
  } | null>(null);

  const handleInputChange = (value: string) => {
    setBodyRaw(value);
    setError(null);
    // 위기감지 이중방어
    const kw = checkKeywords(value);
    if (kw.level === 1) {
      setCrisisOpen(true);
    }
  };

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

    setStep('mode');
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
          background: 'white',
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
              {CATEGORIES.map((cat) => {
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
                      color: isSelected ? 'white' : 'var(--L-ink)',
                      fontSize: 13,
                      fontWeight: isSelected ? 600 : 400,
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
              value={bodyRaw}
              onChange={(e) => handleInputChange(e.target.value)}
              placeholder="갈등 상황을 적어주세요"
              style={{
                width: '100%',
                minHeight: 160,
                padding: '12px 14px',
                border: `6px solid var(--L-border)`,
                borderRadius: 10,
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
              <span style={{ fontSize: 12, color: 'var(--L-sub)' }}>
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

        <CrisisResourceModal
          open={crisisOpen}
          onClose={() => setCrisisOpen(false)}
          severity="advisory"
        />
      </div>
    );
  }

  // Step 2: 모드 선택 (공개 vs 비공개)
  if (step === 'mode') {
    return (
      <div style={{ background: 'var(--L-bg)', minHeight: '100vh', padding: '20px' }}>
        <div style={{
          textAlign: 'center',
          marginBottom: 32,
          marginTop: 24,
        }}>
          <h2 style={{
            fontSize: 18,
            fontWeight: 600,
            fontFamily: 'var(--font-serif)',
            color: 'var(--L-ink)',
          }}>
            이 사연, 어떻게 올릴까요?
          </h2>
        </div>

        {/* 옵션 1: 바로 광장에 올리기 */}
        <button
          onClick={() => handleModeSelect('PUBLIC')}
          disabled={loading}
          style={{
            width: '100%',
            marginBottom: 12,
            padding: '16px',
            border: `2px solid ${GRN}`,
            background: GRN_BG,
            borderRadius: 10,
            cursor: 'pointer',
            opacity: loading ? 0.6 : 1,
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            if (!loading) {
              (e.currentTarget as HTMLElement).style.background =
                'color-mix(in srgb, ' + GRN + ' 10%, ' + GRN_BG + ')';
            }
          }}
          onMouseLeave={(e) => {
            if (!loading) {
              (e.currentTarget as HTMLElement).style.background = GRN_BG;
            }
          }}
        >
          <div style={{
            textAlign: 'left',
          }}>
            <div style={{
              fontSize: 14,
              fontWeight: 600,
              color: GRN,
              marginBottom: 4,
            }}>
              바로 광장에 올리기
            </div>
            <div style={{
              fontSize: 12,
              color: 'color-mix(in srgb, ' + GRN + ' 80%, black)',
            }}>
              익명 투표
            </div>
          </div>
        </button>

        {/* 옵션 2: 상대를 초대하기 */}
        <button
          onClick={() => handleModeSelect('PRIVATE')}
          disabled={loading || isGuest}
          style={{
            width: '100%',
            marginBottom: 24,
            padding: '16px',
            border: `2px solid var(--L-border)`,
            background: isGuest ? 'color-mix(in srgb, var(--L-sub) 5%, white)' : 'white',
            borderRadius: 10,
            cursor: isGuest ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.6 : isGuest ? 0.5 : 1,
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            if (!loading && !isGuest) {
              (e.currentTarget as HTMLElement).style.background =
                'color-mix(in srgb, var(--L-sub) 3%, white)';
            }
          }}
          onMouseLeave={(e) => {
            if (!loading && !isGuest) {
              (e.currentTarget as HTMLElement).style.background = 'white';
            }
          }}
        >
          <div style={{
            textAlign: 'left',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}>
            {isGuest && (
              <span style={{ fontSize: 16 }}>
                🔒
              </span>
            )}
            <div>
              <div style={{
                fontSize: 14,
                fontWeight: 600,
                color: 'var(--L-ink)',
                marginBottom: 4,
              }}>
                상대를 초대하기
              </div>
              <div style={{
                fontSize: 12,
                color: 'var(--L-sub)',
              }}>
                두 입장을 나란히
              </div>
            </div>
          </div>
        </button>

        {/* JurorPicker */}
        <div style={{
          padding: '16px',
          background: 'white',
          borderRadius: 10,
          border: '1px solid var(--L-border)',
          marginBottom: 20,
        }}>
          <JurorPicker
            defaultValue={jurorCount}
            onChange={setJurorCount}
          />
        </div>

        {/* 게스트 안내 */}
        {isGuest && (
          <div style={{
            padding: '12px 14px',
            background: '#F0F0F0',
            borderRadius: 8,
            fontSize: 12,
            color: 'var(--L-sub)',
            textAlign: 'center',
          }}>
            회원가입 후 상대를 초대할 수 있어요{' '}
            <a
              href="/auth/register"
              style={{
                color: 'var(--L-ink)',
                fontWeight: 600,
                textDecoration: 'none',
              }}
            >
              가입하기
            </a>
          </div>
        )}
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
