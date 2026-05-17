'use client';

import { useEffect, useState, useRef, useMemo } from 'react';
import { ChatHeader } from './ChatHeader';
import { MessageBubble } from './MessageBubble';
import { ChatInput } from './ChatInput';
import { FinalizeSuggestionCard } from './FinalizeSuggestionCard';
import { CrisisModal } from './CrisisModal';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';
import { PartnerJoinNoticeCard } from './PartnerJoinNoticeCard';
import { RelationshipColorSync } from '@/components/shared/RelationshipColorSync';
import { useChatSession } from '@/lib/hooks/useChatSession';
import { useFinalize } from '@/lib/hooks/useFinalize';
import { useCrisisGuard } from '@/lib/hooks/useCrisisGuard';
import { useUserStore } from '@/lib/store/userStore';
import { useUiStore } from '@/lib/store/uiStore';
import type { ChatMessage, SecondPart } from '@/lib/hooks/useChatSession';

interface Props {
  sessionId: string;
  session: any;
  currentUserSender: 'USER_A' | 'USER_B';
  isDuo: boolean;
  onOpenInvite?: () => void;
}

export function ChatPanel({
  sessionId,
  session,
  currentUserSender,
  isDuo,
  onOpenInvite,
}: Props) {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [entryTypingDone, setEntryTypingDone] = useState(false);
  // 신규 세션 첫 메시지: 중재자가 3초간 생각하는 척 후 표시
  const [firstMessageReady, setFirstMessageReady] = useState(false);
  const firstMessageTimerStarted = useRef(false);

  const { user } = useUserStore();
  const { showGuestLimitModal } = useUiStore();
  const { crisisLevel1, setCrisisLevel1, showCrisisResource, setShowCrisisResource } = useCrisisGuard();
  const { messages, secondParts, sending, isTyping, fetchMessages, handleSend } = useChatSession(
    sessionId,
    currentUserSender,
    { onCrisis: () => setCrisisLevel1(true) },
  );
  const {
    finalizing, finalizeError, clearFinalizeError,
    handleFinalize, handleAgreeFinalize, handleDeclineFinalize,
  } = useFinalize(sessionId, fetchMessages);

  const mediatorSender = currentUserSender === 'USER_A' ? 'MEDIATOR_TO_A' : 'MEDIATOR_TO_B';
  const mediatorTurnCount = useMemo(
    () => messages.filter(m => m.sender === mediatorSender).length,
    [messages, mediatorSender],
  );
  const guestLimitTriggeredRef = useRef(false);

  useEffect(() => {
    const t = setTimeout(() => setEntryTypingDone(true), 1000);
    return () => clearTimeout(t);
  }, []);

  // 신규 세션(유저 메시지 없음 + 중재자 첫마디 존재): 2초 추가 지연 (진입 1s + 2s = ~3s)
  // 진행 중 세션(유저 메시지 있음) 또는 빈 세션: 즉시 표시
  useEffect(() => {
    if (!entryTypingDone) return;
    if (firstMessageTimerStarted.current) return;

    const hasMediatorMsg = messages.some(m => m.sender === mediatorSender);
    const hasUserMsg = messages.some(m => m.sender === currentUserSender);

    if (hasMediatorMsg && !hasUserMsg) {
      firstMessageTimerStarted.current = true;
      const t = setTimeout(() => setFirstMessageReady(true), 2000);
      return () => clearTimeout(t);
    }
    if (hasUserMsg || messages.length > 0) {
      firstMessageTimerStarted.current = true;
      setFirstMessageReady(true);
    }
  }, [entryTypingDone, messages, mediatorSender, currentUserSender]);

  // 안전 장치: 7초 후 무조건 표시 (메시지 로드 지연 또는 빈 세션 대응)
  useEffect(() => {
    const t = setTimeout(() => {
      if (!firstMessageTimerStarted.current) {
        firstMessageTimerStarted.current = true;
        setFirstMessageReady(true);
      }
    }, 7000);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    if (!user?.isGuest) return;
    if (guestLimitTriggeredRef.current) return;
    if (mediatorTurnCount >= 3 && !isTyping && !sending) {
      guestLimitTriggeredRef.current = true;
      const t = setTimeout(() => showGuestLimitModal(sessionId), 3000);
      return () => clearTimeout(t);
    }
  }, [user?.isGuest, mediatorTurnCount, isTyping, sending, sessionId, showGuestLimitModal]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'instant' });
  }, [messages, secondParts, sending, isTyping]);

  const myMessages = messages.filter(m => m.sender === currentUserSender);
  const canFinalize = myMessages.length >= 5;
  const myAgreed = currentUserSender === 'USER_A' ? session?.finalizeAgreedByA : session?.finalizeAgreedByB;
  const isFinalized = session?.status === 'awaiting_finalization' && !!myAgreed;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--P-bg)' }}>
      <RelationshipColorSync type={session?.relationType ?? null} />

      <ChatHeader
        isDuo={isDuo}
        canFinalize={canFinalize}
        turnCount={myMessages.length}
        canInvite={!isDuo && !!onOpenInvite}
        onOpenInvite={onOpenInvite}
        onFinalize={handleFinalize}
        finalizing={finalizing}
        onOpenCrisis={() => setShowCrisisResource(true)}
      />

      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 12px' }}>
        {(!entryTypingDone || !firstMessageReady) && <TypingBubble />}
        {entryTypingDone && firstMessageReady && messages.length === 0 && <EmptyChatPlaceholder />}
        {entryTypingDone && firstMessageReady && messages.map(msg => {
          if (msg.isPartnerJoinNotice) {
            return <PartnerJoinNoticeCard key={msg.id} message={msg} />;
          }
          if (msg.isFinalizeSuggestion) {
            return (
              <FinalizeSuggestionCard
                key={msg.id}
                message={msg}
                onAgree={handleFinalize}
                onDecline={handleDeclineFinalize}
              />
            );
          }
          return (
            <div key={msg.id}>
              <MessageBubble message={msg} isMine={msg.sender === currentUserSender} />
              {secondParts
                .filter(sp => sp.messageId === msg.id)
                .map((sp, idx) => (
                  <MessageBubble
                    key={`${msg.id}-part2-${idx}`}
                    message={secondPartAsMessage(msg, sp)}
                    isMine={msg.sender === currentUserSender}
                  />
                ))}
            </div>
          );
        })}
        {entryTypingDone && firstMessageReady && sending && <TypingBubble />}
        {entryTypingDone && firstMessageReady && isTyping && <TypingBubble />}
        <div ref={messagesEndRef} />
      </div>

      {finalizeError && (
        <div style={{
          margin: '0 12px 6px', padding: '10px 14px',
          background: '#FFF3F0', border: '1px solid #F5C0B0',
          borderRadius: 10, fontSize: 13, color: '#8A2A10',
          lineHeight: 1.6, display: 'flex', justifyContent: 'space-between',
          alignItems: 'flex-start', gap: 8,
        }}>
          <span>{finalizeError}</span>
          <button
            onClick={clearFinalizeError}
            style={{ background: 'none', border: 'none', color: '#8A2A10', fontSize: 16, cursor: 'pointer', padding: 0, lineHeight: 1, flexShrink: 0 }}
            aria-label="닫기"
          >×</button>
        </div>
      )}

      {isFinalized && (
        <div style={{
          margin: '0 12px 6px', padding: '12px 16px',
          background: 'var(--P-card)', border: '1px solid var(--P-border)',
          borderRadius: 10, fontSize: 13, color: 'var(--P-sub)',
          lineHeight: 1.6, textAlign: 'center',
        }}>
          정리하기를 눌러 더 이상 대화를 이어갈 수 없어요.
          <br />
          상대방의 동의를 기다리고 있어요.
        </div>
      )}

      <ChatInput
        onSend={handleSend}
        disabled={isFinalized || (user?.isGuest === true && mediatorTurnCount >= 3)}
        onCrisis={() => setCrisisLevel1(true)}
      />

      {crisisLevel1 && <CrisisModal onClose={() => setCrisisLevel1(false)} />}
      <CrisisResourceModal open={showCrisisResource} onClose={() => setShowCrisisResource(false)} />
    </div>
  );
}

function secondPartAsMessage(orig: ChatMessage, sp: SecondPart) {
  return {
    id: orig.id,
    sender: sp.sender,
    content: sp.content,
    charCount: sp.charCount,
    isFinalizeSuggestion: false as const,
    isPartnerJoinNotice: false as const,
    createdAt: sp.createdAt,
  };
}

function TypingBubble() {
  return (
    <div
      style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 12, alignItems: 'flex-end', gap: 6 }}
      aria-live="polite"
      aria-label="중재자가 응답을 작성 중입니다"
    >
      <div style={{
        padding: '14px 18px', borderRadius: '4px 14px 14px 14px',
        background: 'var(--P-card)', border: '1px solid var(--P-border)',
        minWidth: 56, display: 'flex', alignItems: 'center', gap: 2,
      }}>
        <span className="typing-dot" />
        <span className="typing-dot" />
        <span className="typing-dot" />
      </div>
    </div>
  );
}

function EmptyChatPlaceholder() {
  return (
    <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--P-sub)' }}>
      <div className="serif" style={{ fontSize: 18, marginBottom: 12, color: 'var(--P-ink)' }}>
        무슨 일이 있으셨어요?
      </div>
      <div style={{ fontSize: 13, lineHeight: 1.7 }}>
        편한 말로, 한 줄씩 적어주세요.
        <br />
        제가 차분히 들을게요.
        <br /><br />
        <span style={{ fontSize: 12, color: 'var(--P-sub)', opacity: 0.8 }}>
          AI는 누가 옳은지 판단하지 않아요. 편안하게 말씀해 주세요.
        </span>
      </div>
    </div>
  );
}
