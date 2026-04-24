// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (Solo mediation 3-turn flow)

'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { api } from '@/lib/api/client';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { Dashes } from '@/components/shared/Dashes';
import { MediatorMessage } from '@/components/mediation/MediatorMessage';
import { TurnInput } from '@/components/mediation/TurnInput';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';

export default function SoloMediationPage() {
  const router = useRouter();
  const sessionId = useSessionStore((s) => s.sessionId);
  const relationType = useSessionStore((s) => s.relationType);
  const description = useSessionStore((s) => s.description);
  const setSessionStatus = useSessionStore((s) => s.setStatus);
  const setCurrentTurn = useSessionStore((s) => s.setCurrentTurn);
  const appendTurn = useSessionStore((s) => s.appendTurn);
  const user = useUserStore((s) => s.user);

  const [currentTurn, setLocalTurn] = useState(1);
  const [turnInput, setTurnInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [crisisModalOpen, setCrisisModalOpen] = useState(false);

  // Initialize solo mode if not already
  useEffect(() => {
    const initSolo = async () => {
      if (!sessionId) {
        router.push('/session/new');
        return;
      }

      // POST to /api/sessions/:id/solo to set session to solo_mode
      try {
        await api.post(`/api/sessions/${sessionId}/solo`);
        setSessionStatus('solo_mode');
      } catch (err) {
        console.error('Failed to init solo mode:', err);
      }
    };

    initSolo();
  }, [sessionId, setSessionStatus, router]);

  const mediatorMessages: Record<number, string> = {
    1: `어떤 일이 있었는지는 이미 "${description || '상황'}"으로 정리하셨네요. 이제 상대방의 입장을 추측해볼까요?`,
    2: `만약 상대방 입장이라면 어떻게 느껴졌을까요?`,
    3: `이제 마지막으로, 지금 당신이 건넬 수 있는 가장 솔직한 한 문장은 뭘까요?`,
  };

  const turnLabels: Record<number, string> = {
    1: '내가 본 상황',
    2: '상대의 입장 추측',
    3: '나의 진솔한 한 문장',
  };

  const handleSubmit = async () => {
    if (!turnInput.trim() || !sessionId) return;

    // Check keywords
    const result = checkKeywords(turnInput);
    if (result.level === 1) {
      setCrisisModalOpen(true);
      return;
    }

    setSubmitting(true);
    try {
      // Append turn to store
      appendTurn({
        turnNumber: currentTurn,
        role: 'A',
        content: turnInput,
        createdAt: new Date().toISOString(),
      });

      // Move to next turn or finish
      if (currentTurn === 3) {
        // Request report
        const reportRes = await api.post(`/api/sessions/${sessionId}/report`);
        setCurrentTurn(4);
        router.push(`/session/result/${sessionId}?solo=true`);
      } else {
        setLocalTurn(currentTurn + 1);
        setCurrentTurn(currentTurn + 1);
        setTurnInput('');
      }
    } catch (err) {
      console.error('Failed to submit turn:', err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <PhoneFrame tone="P">
      <PhoneHeader title="혼자 정리한 이야기" tone="P" onBack={() => router.back()} />

      {/* Watermark banner */}
      <div
        style={{
          padding: '10px 14px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          borderRadius: 12,
          fontSize: 12,
          color: 'var(--P-sub)',
          display: 'flex',
          alignItems: 'flex-start',
          gap: 8,
          margin: '8px 22px 14px',
          lineHeight: 1.5,
        }}
      >
        <span style={{ minWidth: 6, marginTop: 2 }}>
          <span
            style={{
              display: 'block',
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: 'var(--P-a)',
            }}
          />
        </span>
        <span>
          한쪽 분석 · 지금이라도 상대를 초대하면 완전한 리포트가 생성돼요{' '}
          <button
            onClick={() => router.push('/session/invite')}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--P-a)',
              fontWeight: 500,
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
          >
            초대 링크 다시 보내기
          </button>
        </span>
      </div>

      {/* Progress */}
      <div style={{ padding: '8px 22px', marginBottom: 14 }}>
        <Dashes n={3} done={currentTurn - 1} />
        <div style={{ marginTop: 6, fontSize: 11, color: 'var(--P-sub)' }}>
          {currentTurn} / 3
        </div>
      </div>

      {/* Content */}
      <div style={{ padding: '0 22px 28px', display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Mediator message */}
        <MediatorMessage
          text={mediatorMessages[currentTurn]}
          variant="letter"
          turnLabel={turnLabels[currentTurn]}
        />

        {/* Input section */}
        <div>
          <TurnInput
            value={turnInput}
            onChange={setTurnInput}
            onSubmit={handleSubmit}
            disabled={submitting}
            canSkip={false}
            keywordLevel={checkKeywords(turnInput).level}
          />
        </div>

        {/* Submit button */}
        <button
          onClick={handleSubmit}
          disabled={!turnInput.trim() || submitting}
          className="btn-P"
          style={{
            width: '100%',
            opacity: !turnInput.trim() || submitting ? 0.5 : 1,
          }}
        >
          {submitting ? '처리 중...' : currentTurn === 3 ? '완료' : '다음'}
        </button>
      </div>

      {/* Crisis modal */}
      <CrisisResourceModal
        open={crisisModalOpen}
        onClose={() => setCrisisModalOpen(false)}
        severity="critical"
      />
    </PhoneFrame>
  );
}
