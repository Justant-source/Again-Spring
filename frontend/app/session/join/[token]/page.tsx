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
        // 로그인 사용자: 스타일 등록 여부에 따라 분기
        if (user && !user.isGuest) {
          setStep(
            user.onboardingCompletedAt && user.communicationStyle
              ? 'nickname-input'
              : 'login-onboarding-prompt',
          );
        } else if (user?.isGuest && user.communicationStyle) {
          // 게스트가 이미 성격검사를 마친 경우(예: 온보딩 후 복귀) → 바로 참여 단계로
          setStep('nickname-input');
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

  const handleGuestDoOnboarding = async () => {
    // 온보딩 결과(communicationStyle)가 user 객체에 저장되도록 게스트를 먼저 생성한다.
    // 생성 실패해도 온보딩 자체는 진행 (handleJoin 시점에 다시 시도됨)
    if (!user) {
      try {
        const tempName = generateGuestNickname();
        const { guestId, token: guestJwt } = await getOrCreateGuestId(token, tempName);
        localStorage.setItem('again-spring-token', guestJwt);
        setUser({
          id: guestId,
          nickname: tempName,
          isGuest: true,
          createdAt: new Date().toISOString(),
        });
      } catch {
        // 무시 — 온보딩 후 닉네임 입력 단계에서 다시 시도된다
      }
    }
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
        // 이후 채팅 페이지 API 호출에도 인증이 되도록 localStorage에도 저장
        localStorage.setItem('again-spring-token', guestJwt);
        setUser({
          id: guestId,
          nickname: name,
          isGuest: true,
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
      // BE는 sessionId 가 아닌 id 필드로 반환
      const sessionId = res.data.id || sessionData?.id;
      router.push(`/session/chat/${sessionId}`);
    } catch (err: any) {
      const code = err.response?.data?.error?.code ?? err.response?.data?.code;
      const errorMessages: Record<string, string> = {
        INVITE_TOKEN_INVALID: '초대 링크가 유효하지 않아요. 링크를 다시 확인해주세요.',
        INVITE_TOKEN_EXPIRED: '초대 링크가 만료됐어요. 상대방에게 새 링크를 요청해주세요.',
        SESSION_ALREADY_JOINED: '이미 다른 분이 참여한 대화예요. 상대방에게 새 링크를 요청해주세요.',
        SESSION_INVALID_STATE: '현재 참여할 수 없는 대화예요. 상대방에게 문의해주세요.',
        SESSION_SELF_JOIN_FORBIDDEN: '본인이 만든 대화에는 참여할 수 없어요.',
      };
      setError(errorMessages[code] ?? '참여에 실패했어요. 잠시 후 다시 시도해주세요.');
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
