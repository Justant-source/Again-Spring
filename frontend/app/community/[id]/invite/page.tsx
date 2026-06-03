'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { postApi } from '@/lib/api/community/postApi';
import { postInviteApi } from '@/lib/api/community/postInviteApi';
import { SideStory } from '@/components/community/c3/SideStory';

type Step = 'invite' | 'choice' | 'closing' | 'waiting' | 'arrived';

interface PostData {
  id: string;
  userTitle: string;
  bodyPublished: string;
  category: string;
  partnerBodyPublished?: string;
}

export default function PostInvitePage() {
  const router = useRouter();
  const params = useParams();
  const postId = params?.id as string;

  const [step, setStep] = useState<Step>('invite');
  const [post, setPost] = useState<PostData | null>(null);
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // "choice" 단계 상태
  const [publishChoice, setPublishChoice] = useState<'now' | 'wait'>('wait');

  // "closing" 단계 상태
  const [voteDuration, setVoteDuration] = useState<24 | 72 | 168 | null>(72);

  // "waiting" 단계 상태
  const [partnerArrived, setPartnerArrived] = useState(false);

  // 페이지 로드: 포스트 정보 가져오기
  useEffect(() => {
    if (!postId) return;

    const loadPost = async () => {
      try {
        setLoading(true);
        const data = await postApi.get(postId);
        setPost({
          id: data.id,
          userTitle: data.title,
          bodyPublished: data.bodyPublished,
          category: data.category,
        });
      } catch (err) {
        console.error('Failed to load post:', err);
        setError('포스트를 불러올 수 없습니다');
      } finally {
        setLoading(false);
      }
    };

    loadPost();
  }, [postId]);

  // Polling for partner arrival
  useEffect(() => {
    if (step !== 'waiting') return;
    const timer = setInterval(async () => {
      try {
        const data = await postApi.get(postId);
        if (data.paired || data.partnerBodyPublished) {
          clearInterval(timer);
          setPost(prev => prev ? { ...prev, bodyPublished: data.bodyPublished, partnerBodyPublished: data.partnerBodyPublished } : prev);
          setStep('arrived');
        }
      } catch {}
    }, 4000);
    return () => clearInterval(timer);
  }, [step, postId]);

  const handleCreateInvite = async () => {
    if (!postId) return;
    try {
      setLoading(true);
      setError(null);
      const response = await postInviteApi.createInvite(postId);
      setInviteToken(response.inviteToken);
      setStep('choice');
    } catch (err) {
      console.error('Failed to create invite:', err);
      setError('초대 링크를 생성할 수 없습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleChoiceNext = async () => {
    if (publishChoice === 'now') {
      setStep('closing');
    } else {
      try {
        setLoading(true);
        await postInviteApi.setPublishMode(postId, 'WAIT_FOR_PARTNER', 72);
      } catch (err) {
        console.error('Failed to set publish mode:', err);
      } finally {
        setLoading(false);
      }
      setStep('waiting');
    }
  };

  const handleClosingPublish = async () => {
    if (!postId) return;
    try {
      setLoading(true);
      setError(null);
      // 발행 모드 설정
      await postInviteApi.setPublishMode(
        postId,
        'PUBLISH_NOW',
        voteDuration || 72
      );
      // 즉시 발행
      await postInviteApi.publishNow(postId);
      setStep('waiting');
    } catch (err) {
      console.error('Failed to publish:', err);
      setError('발행에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handlePublishNow = async () => {
    if (!postId) return;
    try {
      setLoading(true);
      setError(null);
      await postInviteApi.publishNow(postId);
      // 커뮤니티 페이지로 이동
      router.push(`/community/${postId}`);
    } catch (err) {
      console.error('Failed to publish now:', err);
      setError('발행에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleCopyLink = async () => {
    if (!inviteToken) return;
    const url = `${typeof window !== 'undefined' ? window.location.origin : ''}/s/${inviteToken}`;
    try {
      await navigator.clipboard.writeText(url);
      alert('링크가 복사되었습니다');
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  if (loading && !post) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontSize: 14, color: 'var(--P-sub)' }}>로드 중...</div>
      </div>
    );
  }

  if (!post) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        <div style={{ fontSize: 14, color: 'var(--faction-partner)' }}>{error || '포스트를 찾을 수 없습니다'}</div>
      </div>
    );
  }

  // Step 1: Invite
  if (step === 'invite') {
    const inviteUrl = inviteToken ? `${typeof window !== 'undefined' ? window.location.origin : ''}/s/${inviteToken}` : '';

    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        {/* Back button */}
        <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 24 }}>
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
            ‹
          </button>
        </div>

        {/* Title */}
        <div style={{ marginBottom: 32 }}>
          <h1
            style={{
              fontSize: 20,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600,
              color: 'var(--P-ink)',
              margin: 0,
            }}
          >
            링크로 상대를 초대하세요
          </h1>
        </div>

        {/* Story preview */}
        <div style={{ marginBottom: 28 }}>
          <SideStory
            side="g"
            label="내 이야기"
            body={post.bodyPublished}
            clamp={false}
            selected={false}
            onSelect={() => {}}
            onMore={() => {}}
          />
        </div>

        {/* URL box */}
        {inviteToken ? (
          <div style={{ marginBottom: 28 }}>
            <div style={{ marginBottom: 12 }}>
              <label style={{ fontSize: 12, color: 'var(--P-sub)', display: 'block', marginBottom: 8 }}>
                초대 링크
              </label>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '12px 14px',
                  background: 'var(--P-card)',
                  border: '1px solid var(--P-border)',
                  borderRadius: 8,
                }}
              >
                <input
                  type="text"
                  value={inviteUrl}
                  readOnly
                  style={{
                    flex: 1,
                    border: 'none',
                    background: 'transparent',
                    fontSize: 12,
                    color: 'var(--P-ink)',
                    outline: 'none',
                  }}
                />
                <button
                  onClick={handleCopyLink}
                  style={{
                    padding: '6px 12px',
                    background: 'var(--P-ink)',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    fontSize: 12,
                    cursor: 'pointer',
                    flexShrink: 0,
                    whiteSpace: 'nowrap',
                  }}
                >
                  복사
                </button>
              </div>
            </div>
          </div>
        ) : null}


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

        {/* Next button */}
        <button
          onClick={inviteToken ? handleChoiceNext : handleCreateInvite}
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
          }}
        >
          {loading ? '생성 중...' : inviteToken ? '다음' : '링크 생성'}
        </button>
      </div>
    );
  }

  // Step 2: Choice
  if (step === 'choice') {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        <div style={{ marginBottom: 32 }}>
          <h1
            style={{
              fontSize: 20,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600,
              color: 'var(--P-ink)',
              margin: 0,
              marginBottom: 12,
            }}
          >
            언제 올릴까요?
          </h1>
          <p style={{ fontSize: 13, color: 'var(--P-sub)', margin: 0 }}>
            상대 답변이 오면 오른쪽에 자동으로 붙어요.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 28 }}>
          <label
            style={{
              padding: '16px 14px',
              background: publishChoice === 'now' ? 'var(--P-card)' : 'white',
              border: `2px solid ${publishChoice === 'now' ? 'var(--P-ink)' : 'var(--P-border)'}`,
              borderRadius: 8,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              transition: 'all 0.2s',
            }}
          >
            <input
              type="radio"
              name="choice"
              value="now"
              checked={publishChoice === 'now'}
              onChange={(e) => setPublishChoice(e.target.value as 'now' | 'wait')}
              style={{ cursor: 'pointer' }}
            />
            <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--P-ink)' }}>
              먼저 올리기
            </span>
          </label>
          <label
            style={{
              padding: '16px 14px',
              background: publishChoice === 'wait' ? 'var(--P-card)' : 'white',
              border: `2px solid ${publishChoice === 'wait' ? 'var(--P-ink)' : 'var(--P-border)'}`,
              borderRadius: 8,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              transition: 'all 0.2s',
            }}
          >
            <input
              type="radio"
              name="choice"
              value="wait"
              checked={publishChoice === 'wait'}
              onChange={(e) => setPublishChoice(e.target.value as 'now' | 'wait')}
              style={{ cursor: 'pointer' }}
            />
            <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--P-ink)' }}>
              답변을 기다리기
            </span>
          </label>
        </div>

        <button
          onClick={handleChoiceNext}
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
          }}
        >
          다음
        </button>
      </div>
    );
  }

  // Step 3: Closing (only if publishChoice === 'now')
  if (step === 'closing') {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        <div style={{ marginBottom: 32 }}>
          <h1
            style={{
              fontSize: 20,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600,
              color: 'var(--P-ink)',
              margin: 0,
            }}
          >
            언제까지 투표를 받을까요?
          </h1>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 28 }}>
          {[
            { value: 24, label: '24시간' },
            { value: 72, label: '3일' },
            { value: 168, label: '7일' },
            { value: null, label: '직접 마감할 때까지' },
          ].map((option) => (
            <label
              key={option.value || 'custom'}
              style={{
                padding: '16px 14px',
                background: voteDuration === option.value ? 'var(--P-card)' : 'white',
                border: `2px solid ${voteDuration === option.value ? 'var(--P-ink)' : 'var(--P-border)'}`,
                borderRadius: 8,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                transition: 'all 0.2s',
              }}
            >
              <input
                type="radio"
                name="duration"
                checked={voteDuration === option.value}
                onChange={() => setVoteDuration(option.value as 24 | 72 | 168 | null)}
                style={{ cursor: 'pointer' }}
              />
              <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--P-ink)' }}>
                {option.label}
              </span>
            </label>
          ))}
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
          onClick={handleClosingPublish}
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
          }}
        >
          {loading ? '발행 중...' : '이대로 올리기'}
        </button>
      </div>
    );
  }

  // Step 4: Waiting
  if (step === 'waiting') {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
        {/* Grid: 2 columns */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 12,
            marginBottom: 32,
          }}
        >
          {/* My story */}
          <div style={{ minHeight: 200 }}>
            <SideStory
              side="g"
              label="내 이야기"
              body={post.bodyPublished}
              clamp={false}
              selected={false}
              onSelect={() => {}}
              onMore={() => {}}
            />
          </div>

          {/* Partner waiting */}
          <div
            style={{
              minHeight: 200,
              padding: '13px 14px',
              border: '2.5px dashed var(--faction-partner)',
              borderRadius: 12,
              background: 'var(--faction-partner-bg)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'center',
            }}
          >
            <div>
              <div
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: 'var(--faction-partner)',
                  margin: '0 auto 8px',
                }}
              />
              <p
                style={{
                  fontSize: 12,
                  fontWeight: 500,
                  color: 'var(--P-ink)',
                  margin: 0,
                  marginBottom: 4,
                }}
              >
                상대 답변 대기
              </p>
              <p
                style={{
                  fontSize: 11,
                  color: 'var(--P-sub)',
                  margin: 0,
                }}
              >
                아직 답변이 없어요
              </p>
            </div>
          </div>
        </div>

        {/* Waiting message */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 8 }}>
            상대의 답변을 기다리고 있어요
          </div>
          <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>
            답변이 오면 자동으로 표시됩니다
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

        {/* Action buttons */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <button
            onClick={handlePublishNow}
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
            }}
          >
            {loading ? '발행 중...' : '지금 혼자 올리기'}
          </button>
          <button
            onClick={handleCopyLink}
            style={{
              width: '100%',
              padding: '14px 16px',
              background: 'transparent',
              color: 'var(--P-ink)',
              border: '1px solid var(--P-border)',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            링크 보내기
          </button>
        </div>
      </div>
    );
  }

  // Step 4.5: Arrived
  if (step === 'arrived') {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--L-bg)', padding: '20px' }}>
        <div
          style={{
            fontSize: 18,
            fontFamily: 'var(--font-serif)',
            fontWeight: 600,
            color: 'var(--L-ink)',
            marginBottom: 8,
          }}
        >
          답변이 도착했어요
        </div>
        <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 28 }}>
          상대방의 이야기가 붙었습니다
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 32 }}>
          <SideStory
            side="g"
            label="내 이야기"
            body={post.bodyPublished}
            clamp={false}
            selected={false}
            onSelect={() => {}}
            onMore={() => {}}
          />
          <SideStory
            side="r"
            label="상대방 이야기"
            body={post.partnerBodyPublished || ''}
            clamp={false}
            selected={false}
            onSelect={() => {}}
            onMore={() => {}}
          />
        </div>

        <button
          data-testid="arrived-result-btn"
          onClick={() => router.push(`/community/${postId}`)}
          style={{
            width: '100%',
            padding: '15px 0',
            borderRadius: 4,
            border: 'none',
            background: 'var(--L-ink)',
            color: 'var(--L-bg)',
            fontSize: 15,
            fontWeight: 500,
            fontFamily: 'var(--font-sans)',
            cursor: 'pointer',
          }}
        >
          함께 결과 보기
        </button>
      </div>
    );
  }

  return null;
}
