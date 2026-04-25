// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (BSummary)

'use client';

import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { generateGuestNickname } from '@/lib/utils/guestNickname';
import { PhoneFrame, PhoneHeader } from '@/components/shared';

interface SessionData {
  id: string;
  inviterName?: string;
  relationType?: string;
  category?: { majorId: string; middleId: string; minorId: string };
}

const GUEST_MAP_KEY = 'again-spring-guest-map';

/** inviteToken별로 동일한 Guest ID 반환 (재접속 일관성 보장) */
async function getOrCreateGuestId(
  inviteToken: string,
  nickname: string,
): Promise<{ guestId: string; token: string }> {
  const map: Record<string, { guestId: string; jwtToken: string }> = JSON.parse(
    localStorage.getItem(GUEST_MAP_KEY) ?? '{}',
  );

  if (map[inviteToken]) {
    return { guestId: map[inviteToken].guestId, token: map[inviteToken].jwtToken };
  }

  const res = await api.post('/api/auth/guest', { inviteToken, nickname });
  const guestId: string = res.data.user.id;
  const jwtToken: string = res.data.token.accessToken;

  map[inviteToken] = { guestId, jwtToken };
  localStorage.setItem(GUEST_MAP_KEY, JSON.stringify(map));

  return { guestId, token: jwtToken };
}

type Step = 'landing' | 'choose-mode' | 'guest-onboarding-prompt' | 'login-onboarding-prompt' | 'nickname-input';

export default function JoinPage({ params }: { params: { token: string } }) {
  const router = useRouter();
  const { setSession, setRole, setPartnerNickname } = useSessionStore();
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);

  const [step, setStep] = useState<Step>('landing');
  const [nickname, setNickname] = useState('');
  const [sessionData, setSessionData] = useState<SessionData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const token = params.token;

  useEffect(() => {
    api
      .get(`/api/sessions/by-token/${token}`)
      .then((res) => {
        setSessionData(res.data);
        if (res.data.id) {
          setSession({ id: res.data.id, inviteToken: token });
        }
      })
      .catch(() => {
        // MSW fallback: create temp session from token
        const tempId = `session-${token}`;
        setSession({ id: tempId, inviteToken: token });
        setSessionData({ id: tempId, inviterName: '초대자' });
      })
      .finally(() => {
        setIsLoading(false);
        // 이미 로그인된 경우 바로 참여 흐름으로
        if (user && !user.isGuest) {
          setStep(user.onboardingCompletedAt ? 'nickname-input' : 'login-onboarding-prompt');
        } else {
          setStep('choose-mode');
        }
      });
  }, [token]);

  const handleLoginMode = () => {
    // 로그인 페이지로 이동 후 돌아옴
    router.push(`/login?redirect=/session/join/${token}`);
  };

  const handleGuestMode = () => {
    setStep('guest-onboarding-prompt');
  };

  const handleGuestSkipOnboarding = () => {
    setStep('nickname-input');
  };

  const handleGuestDoOnboarding = () => {
    router.push(`/onboarding/intro?next=/session/join/${token}`);
  };

  const handleLoginSkipOnboarding = () => {
    setStep('nickname-input');
  };

  const handleLoginDoOnboarding = () => {
    router.push(`/onboarding/intro?next=/session/join/${token}`);
  };

  const handleJoin = async () => {
    const name = nickname.trim() || user?.nickname || generateGuestNickname();
    setIsSubmitting(true);
    setError('');

    try {
      let authToken: string | null = localStorage.getItem('again-spring-token');

      if (!user || user.isGuest) {
        // 게스트: inviteToken별 Guest ID 재사용
        const { guestId, token: guestJwt } = await getOrCreateGuestId(token, name);
        authToken = guestJwt;
        setUser({
          id: guestId,
          nickname: name,
          isGuest: true,
          temperatureHistory: [],
          createdAt: new Date().toISOString(),
        });
      }

      // 세션 참여 API (Authorization 헤더 직접 설정)
      const res = await api.post(
        `/api/sessions/join/${token}`,
        { nickname: name },
        authToken ? { headers: { Authorization: `Bearer ${authToken}` } } : undefined,
      );

      setPartnerNickname(sessionData?.inviterName ?? '초대자');
      setRole('B');
      router.push('/session/describe?role=B');
    } catch (err: any) {
      setError(err.response?.data?.message ?? '참여에 실패했어요. 다시 시도해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <PhoneFrame tone="L">
        <div style={{ padding: 28, textAlign: 'center', color: 'var(--L-sub)', fontSize: 14 }}>
          초대장을 열고 있어요...
        </div>
      </PhoneFrame>
    );
  }

  const inviterName = sessionData?.inviterName ?? '초대자';

  // ── Step: choose-mode (로그인 or 게스트) ──
  if (step === 'choose-mode') {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title={`${inviterName}님이 보내온 초대`} back={true} onBack={() => router.push('/')} />
        <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div className="letter-card" style={{ padding: 24, marginBottom: 24 }}>
            <div className="quote-it" style={{ fontSize: 12, marginBottom: 12 }}>
              {inviterName}님의 초대
            </div>
            <div className="serif" style={{ fontSize: 15, lineHeight: 1.8, color: 'var(--L-sub)' }}>
              두 사람의 이야기가 모이면<br />중재자가 관계 회복을 도와드려요.
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button className="btn-L" onClick={handleLoginMode} style={{ width: '100%' }}>
              로그인하고 참여하기
            </button>
            <button className="btn-L ghost" onClick={handleGuestMode} style={{ width: '100%' }}>
              게스트로 참여하기
            </button>
          </div>
        </div>
      </PhoneFrame>
    );
  }

  // ── Step: guest-onboarding-prompt ──
  if (step === 'guest-onboarding-prompt') {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="성격검사 안내" onBack={() => setStep('choose-mode')} />
        <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div className="letter-card" style={{ padding: 24, marginBottom: 24 }}>
            <div className="quote-it" style={{ fontSize: 12, marginBottom: 12 }}>
              선택 사항이에요
            </div>
            <div className="serif" style={{ fontSize: 16, lineHeight: 1.7, marginBottom: 12 }}>
              10가지 질문에 답하면<br />더 정확한 중재를 받을 수 있어요.
            </div>
            <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              성격검사는 약 2분이 걸려요.<br />
              검사 없이도 바로 참여하실 수 있어요.
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button className="btn-L" onClick={handleGuestDoOnboarding} style={{ width: '100%' }}>
              검사하고 더 정확하게
            </button>
            <button className="btn-L ghost" onClick={handleGuestSkipOnboarding} style={{ width: '100%' }}>
              건너뛰고 바로 참여하기
            </button>
          </div>
        </div>
      </PhoneFrame>
    );
  }

  // ── Step: login-onboarding-prompt ──
  if (step === 'login-onboarding-prompt') {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="성격검사 안내" />
        <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div className="letter-card" style={{ padding: 24, marginBottom: 24 }}>
            <div className="quote-it" style={{ fontSize: 12, marginBottom: 12 }}>
              아직 성격검사를 하지 않으셨어요
            </div>
            <div className="serif" style={{ fontSize: 16, lineHeight: 1.7, marginBottom: 12 }}>
              10가지 질문에 답하면<br />중재 결과가 더 정확해져요.
            </div>
            <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              검사 없이도 바로 참여하실 수 있어요.
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button className="btn-L" onClick={handleLoginDoOnboarding} style={{ width: '100%' }}>
              검사하고 더 정확하게
            </button>
            <button className="btn-L ghost" onClick={handleLoginSkipOnboarding} style={{ width: '100%' }}>
              건너뛰고 바로 참여하기
            </button>
          </div>
        </div>
      </PhoneFrame>
    );
  }

  // ── Step: nickname-input ──
  return (
    <PhoneFrame tone="L">
      <PhoneHeader title={`${inviterName}님이 보내온 마음`} back={true} onBack={() => setStep('choose-mode')} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
          중재자의 요약 · 원문은 서로의 답변 후 공개돼요
        </div>

        <div className="letter-card" style={{ padding: 24, marginBottom: 22 }}>
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 14 }}>
            {inviterName}님의 이야기
          </div>
          <div className="serif" style={{ fontSize: 15, lineHeight: 1.8 }}>
            {inviterName}님이 이야기를 남겨두셨어요.<br />
            두 사람의 이야기가 모이면 중재가 시작됩니다.
          </div>
        </div>

        <div style={{ marginBottom: 28, fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7 }}>
          이제 당신의 이야기도 들려주세요.<br />
          두 사람의 이야기가 모이면 중재가 시작돼요.
        </div>

        {!user || user.isGuest ? (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 8 }}>
              이야기에 쓰실 이름을 알려주세요
            </div>
            <input
              type="text"
              placeholder="닉네임"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              style={{
                width: '100%',
                padding: '12px 0',
                border: 'none',
                borderBottom: '1px solid var(--L-border)',
                fontSize: 15,
                fontFamily: 'var(--font-sans)',
                color: 'var(--L-ink)',
                outline: 'none',
                transition: 'border-color 0.15s',
                boxSizing: 'border-box',
              }}
              onFocus={(e) => { e.currentTarget.style.borderBottomColor = 'var(--L-ink)'; }}
              onBlur={(e) => { e.currentTarget.style.borderBottomColor = 'var(--L-border)'; }}
            />
          </div>
        ) : (
          <div style={{ marginBottom: 16, fontSize: 14, color: 'var(--L-sub)' }}>
            <span style={{ color: 'var(--L-ink)', fontWeight: 500 }}>{user.nickname}</span>으로 참여합니다.
          </div>
        )}

        {error && (
          <div style={{ fontSize: 13, color: 'var(--L-point)', marginBottom: 12 }}>{error}</div>
        )}

        <button
          className="btn-L"
          style={{ width: '100%' }}
          disabled={(!user && !nickname.trim()) || isSubmitting}
          onClick={handleJoin}
        >
          {isSubmitting ? '참여 중...' : '내 이야기 적기'}
        </button>
      </div>
    </PhoneFrame>
  );
}
