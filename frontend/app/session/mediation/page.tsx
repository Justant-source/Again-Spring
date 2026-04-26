// ✅ MOCKUP APPLIED — source: design/handoff/mediation-screens.jsx (MediationLetter primary + Bubble/Cards variants)

'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { Dashes } from '@/components/shared/Dashes';
import { ProgressBar } from '@/components/mediation/ProgressBar';
import { MediatorMessage } from '@/components/mediation/MediatorMessage';
import { TurnInput } from '@/components/mediation/TurnInput';
import { ViewToggle } from '@/components/mediation/ViewToggle';
import { useSessionStore } from '@/lib/store/sessionStore';
import { api } from '@/lib/api/client';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import type { KeywordLevel } from '@/lib/utils/keywordGuard';
import { getMediatorTurn } from '@/mocks/fixtures/mockMediations';

export default function MediationPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const sessionStore = useSessionStore();

  // State
  const [draft, setDraft] = useState('');
  const [variant, setVariant] = useState<'letter' | 'bubble' | 'card'>(
    'letter'
  );
  const [loading, setLoading] = useState(false);
  const [keywordLevel, setKeywordLevel] = useState<KeywordLevel>(null);
  const [mediatorMessage, setMediatorMessage] = useState('');
  const [currentForRole, setCurrentForRole] = useState<'A' | 'B' | null>(null);
  const [initialized, setInitialized] = useState(false);

  const sessionId = sessionStore.sessionId;
  const roleParam = searchParams.get('role');
  const role = (roleParam === 'A' || roleParam === 'B'
    ? roleParam
    : sessionStore.role) as 'A' | 'B' | null;
  const currentTurn = sessionStore.currentTurn;
  const partnerNickname = sessionStore.partnerNickname || '상대';

  // On mount: validate session & load first turn if needed
  useEffect(() => {
    if (!sessionId) {
      router.push('/session/new');
      return;
    }

    // If currentTurn > 6, already completed
    if (currentTurn > 6) {
      router.push(`/session/result/${sessionId}`);
      return;
    }

    // Load mediator message for current turn
    const turn = getMediatorTurn(currentTurn);
    if (turn) {
      setMediatorMessage(turn.mediatorMessage);
      setCurrentForRole(turn.forRole);
    }

    setInitialized(true);
  }, [sessionId, currentTurn, router]);

  // Keyword checking on draft change
  useEffect(() => {
    const check = checkKeywords(draft);
    setKeywordLevel(check.level);
  }, [draft]);

  if (!initialized || !role) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="로드 중..." />
        <div className="flex flex-1 items-center justify-center">
          <div style={{ fontSize: '14px', color: 'var(--L-sub)' }}>
            중재자가 마음을 정리 중이에요…
          </div>
        </div>
      </PhoneFrame>
    );
  }

  const isItYourTurn = currentForRole === role;
  const canSkip = currentTurn === 5 || currentTurn === 6;
  const turnLabel =
    variant === 'card'
      ? String(currentTurn)
      : `${currentTurn} / 6 턴 · 중재자의 편지`;

  const handleSubmit = async () => {
    if (!sessionId || !role || !mediatorMessage) return;
    setLoading(true);

    try {
      const response = await api.post(`/api/sessions/${sessionId}/turns`, {
        sessionId,
        turnNumber: currentTurn,
        role,
        content: draft,
      });

      // Append turn to store
      if (role) {
        sessionStore.appendTurn({
          turnNumber: currentTurn,
          role,
          content: draft,
          mediatorMessage,
          createdAt: new Date().toISOString(),
        });
      }

      // If completed, redirect to result
      if (response.data.completed) {
        router.push(`/session/result/${sessionId}`);
        return;
      }

      // Move to next turn
      const nextTurnNumber = response.data.nextTurn?.turnNumber || currentTurn + 1;
      sessionStore.setCurrentTurn(nextTurnNumber);

      // Clear draft
      setDraft('');
    } catch (error) {
      console.error('Failed to submit turn:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSkip = async () => {
    if (!sessionId || !role || !mediatorMessage) return;
    setLoading(true);

    try {
      const response = await api.post(`/api/sessions/${sessionId}/turns`, {
        sessionId,
        turnNumber: currentTurn,
        role,
        content: '',
        skipped: true,
      });

      // Append skipped turn to store
      if (role) {
        sessionStore.appendTurn({
          turnNumber: currentTurn,
          role,
          content: '',
          mediatorMessage,
          skipped: true,
          createdAt: new Date().toISOString(),
        });
      }

      // If completed, redirect to result
      if (response.data.completed) {
        router.push(`/session/result/${sessionId}`);
        return;
      }

      // Move to next turn
      const nextTurnNumber = response.data.nextTurn?.turnNumber || currentTurn + 1;
      sessionStore.setCurrentTurn(nextTurnNumber);

      setDraft('');
    } catch (error) {
      console.error('Failed to skip turn:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSimulatePartnerTurn = async () => {
    // Dev/demo button: simulate posting the partner's turn
    if (!sessionId) return;
    const partnerRole = role === 'A' ? 'B' : 'A';

    try {
      const response = await api.post(`/api/sessions/${sessionId}/turns`, {
        sessionId,
        turnNumber: currentTurn,
        role: partnerRole,
        content: `[시뮬레이션 응답 - ${partnerRole}의 차례]`,
      });

      // Move to next turn
      const nextTurnNumber = response.data.nextTurn?.turnNumber || currentTurn + 1;
      sessionStore.setCurrentTurn(nextTurnNumber);
    } catch (error) {
      console.error('Failed to simulate:', error);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title={turnLabel}
        back={true}
        onBack={() => router.push('/session/wait')}
        right={<ViewToggle value={variant} onChange={setVariant} />}
      />

      <div
        className="flex-1 overflow-y-auto"
        style={{ padding: '28px 28px 40px' }}
      >
        {/* Progress indicator */}
        {variant === 'letter' && (
          <div style={{ marginBottom: '24px' }}>
            <Dashes n={6} done={currentTurn - 1} />
          </div>
        )}
        {variant === 'bubble' && (
          <div style={{ marginBottom: '20px' }}>
            <ProgressBar current={currentTurn} total={6} />
          </div>
        )}
        {variant === 'card' && (
          <div style={{ marginBottom: '24px' }}>
            <Dashes n={6} done={currentTurn - 1} />
          </div>
        )}

        {/* Mediator message */}
        <div style={{ marginBottom: variant === 'letter' ? '28px' : '20px' }}>
          <MediatorMessage
            text={mediatorMessage}
            variant={variant}
            turnLabel={
              variant === 'card'
                ? String(currentTurn)
                : variant === 'bubble'
                  ? `턴 ${currentTurn}`
                  : undefined
            }
            recipientName={`${partnerNickname}님께`}
          />
        </div>

        {/* Turn input section (only if it's their turn) */}
        {isItYourTurn ? (
          <>
            {variant === 'letter' && <hr className="hr-L" />}

            <div
              style={{
                fontSize: '12px',
                color: 'var(--L-sub)',
                marginBottom: '10px',
              }}
            >
              {role}님의 답장
            </div>

            <TurnInput
              value={draft}
              onChange={setDraft}
              onSubmit={handleSubmit}
              onSkip={canSkip ? handleSkip : undefined}
              disabled={loading}
              canSkip={canSkip}
              keywordLevel={keywordLevel}
            />
          </>
        ) : (
          /* Waiting state */
          <div
            style={{
              padding: '24px',
              background: 'var(--L-card)',
              border: '1px solid var(--L-border)',
              borderRadius: '3px',
              textAlign: 'center',
            }}
          >
            <div
              style={{
                fontSize: '14px',
                color: 'var(--L-ink)',
                marginBottom: '12px',
              }}
            >
              상대의 답장을 기다리고 있어요…
            </div>
            <button
              onClick={handleSimulatePartnerTurn}
              style={{
                fontSize: '12px',
                padding: '8px 14px',
                background: 'var(--L-sub)',
                color: 'var(--L-bg)',
                border: 'none',
                borderRadius: '3px',
                cursor: 'pointer',
                transition: 'opacity 0.15s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.8')}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
            >
              상대 응답 시뮬레이션 (dev)
            </button>
          </div>
        )}
      </div>
    </PhoneFrame>
  );
}
