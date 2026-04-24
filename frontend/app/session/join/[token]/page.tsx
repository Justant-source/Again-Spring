// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (BSummary)

'use client';

import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { api } from '@/lib/api/client';
import { PhoneFrame, PhoneHeader } from '@/components/shared';

interface SessionData {
  inviterName?: string;
  relationType?: string;
  category?: { majorId: string; middleId: string; minorId: string };
}

export default function JoinPage({ params }: { params: { token: string } }) {
  const router = useRouter();
  const { sessionId, setSession, setRole, setPartnerNickname } = useSessionStore();
  const [nickname, setNickname] = useState('');
  const [sessionData, setSessionData] = useState<SessionData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const token = params.token;

  // Fetch session data by token
  useEffect(() => {
    const fetchSession = async () => {
      try {
        const response = await api.get(`/sessions/by-token/${token}`);
        setSessionData(response.data);
        // For demo: set a temporary session ID from token
        if (!sessionId) {
          const tempId = `session-${token}`;
          setSession({ id: tempId, inviteToken: token });
        }
      } catch (error) {
        console.error('Failed to fetch session:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchSession();
  }, [token, sessionId, setSession]);

  const handleJoin = async () => {
    if (!nickname.trim()) return;

    setIsSubmitting(true);
    try {
      const sessionIdToUse = sessionId || `session-${token}`;
      await api.post(`/sessions/${sessionIdToUse}/join`, {
        nickname: nickname.trim(),
      });

      setPartnerNickname(sessionData?.inviterName || '서현');
      setRole('B');
      router.push(`/session/describe?role=B`);
    } catch (error) {
      console.error('Failed to join session:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <PhoneFrame tone="L">
        <div style={{ padding: 28, textAlign: 'center' }}>로드 중...</div>
      </PhoneFrame>
    );
  }

  const inviterName = sessionData?.inviterName || '서현';

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title={`${inviterName}님이 보내온 마음`} back={false} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
          중재자의 요약 · 원문은 서로의 답변 후 공개돼요
        </div>

        <div className="letter-card" style={{ padding: 24, marginBottom: 22 }}>
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 14 }}>
            {inviterName}님의 이야기
          </div>
          <div className="serif" style={{ fontSize: 15, lineHeight: 1.8 }}>
            {inviterName}님은 맞벌이 상황에서 집안일과 육아가 한쪽으로 기울고 있다고 느끼셨어요. 특히 주말에도
            쉬지 못하는 날이 반복되면서, 곁에서 쉬고 있는 모습을 볼 때 서운함과 지침이 함께 올라온다고
            하셨습니다.
          </div>
        </div>

        <div style={{ marginBottom: 28, fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7 }}>
          이제 당신의 이야기도 들려주세요.
          <br />
          두 사람의 이야기가 모이면 중재가 시작돼요.
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 8 }}>
            이야기에 쓰실 이름을 알려주세요
          </div>
          <input
            type="text"
            placeholder="준호"
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
            onFocus={(e) => {
              e.currentTarget.style.borderBottomColor = 'var(--L-ink)';
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderBottomColor = 'var(--L-border)';
            }}
          />
        </div>

        <button
          className="btn-L"
          style={{ width: '100%', marginBottom: 12 }}
          disabled={!nickname.trim() || isSubmitting}
          onClick={handleJoin}
        >
          내 이야기 적기
        </button>

        <div style={{ textAlign: 'center' }}>
          <button
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--L-sub)',
              fontSize: 12,
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
            onClick={() => {
              setPartnerNickname(inviterName);
              setRole('B');
              router.push(`/session/describe?role=B`);
            }}
          >
            게스트로 참여
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
