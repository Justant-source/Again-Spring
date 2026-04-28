'use client';

import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { ChatHeader } from './ChatHeader';
import { MessageBubble } from './MessageBubble';
import { ChatInput } from './ChatInput';
import { FinalizeSuggestionCard } from './FinalizeSuggestionCard';
import { CrisisModal } from './CrisisModal';
import { PartnerJoinNoticeCard } from './PartnerJoinNoticeCard';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';

interface Message {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

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
  const router = useRouter();
  const [messages, setMessages] = useState<Message[]>([]);
  const [sending, setSending] = useState(false);
  const [crisisLevel1, setCrisisLevel1] = useState(false);
  const [finalizeError, setFinalizeError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const lastFetchRef = useRef<number>(0);

  const fetchMessages = async () => {
    try {
      const url = lastFetchRef.current
        ? `/api/sessions/${sessionId}/messages?since=${lastFetchRef.current}`
        : `/api/sessions/${sessionId}/messages`;
      const r = await api.get(url);
      if (r.data.length > 0) {
        setMessages(prev => mergeMessages(prev, r.data));
        lastFetchRef.current = Date.now();
      }
    } catch (e) {
      console.debug('Messages poll error:', e);
    }
  };

  useEffect(() => {
    fetchMessages();
  }, [sessionId]);

  usePolling(fetchMessages, 3000);

  // isFinalized: 본인이 정리하기를 누른 + 상대 동의 대기 중 → 입력창 비활성화
  const myAgreed = currentUserSender === 'USER_A'
    ? session?.finalizeAgreedByA
    : session?.finalizeAgreedByB;
  const isFinalized = session?.status === 'awaiting_finalization' && !!myAgreed;

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const myMessages = messages.filter(m => m.sender === currentUserSender);
  const canFinalize = myMessages.length >= 3;

  const handleSend = async (content: string) => {
    if (sending) return;
    setSending(true);

    // Optimistic: 사용자 입력을 즉시 화면에 표시 (음수 ID는 임시 표식)
    const tempId = -Date.now();
    const optimisticMsg: Message = {
      id: tempId,
      sender: currentUserSender,
      content,
      charCount: content.length,
      isFinalizeSuggestion: false,
      isPartnerJoinNotice: false,
      createdAt: new Date().toISOString(),
    };
    setMessages(prev => [...prev, optimisticMsg]);

    try {
      const r = await api.post(`/api/sessions/${sessionId}/messages`, {
        content,
      });
      if (r.data.crisisLevel === 1) {
        setMessages(prev => prev.filter(m => m.id !== tempId));
        setCrisisLevel1(true);
        return;
      }
      // optimistic 메시지 제거 후 BE 권위 데이터로 전체 재동기화 (이중 추가/누락 방지)
      setMessages(prev => prev.filter(m => m.id !== tempId));
      lastFetchRef.current = 0;
      await fetchMessages();
    } catch (e: any) {
      setMessages(prev => prev.filter(m => m.id !== tempId));
      if (e.response?.status === 409) {
        setCrisisLevel1(true);
      } else {
        console.error('Send failed:', e);
      }
    } finally {
      setSending(false);
    }
  };

  const handleFinalize = async () => {
    setFinalizeError(null);
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
      } else if (r.data.awaitingPartner) {
        // 본인 측 카드는 BE에서 dismiss됨 → 재폴링으로 반영 + session 갱신은 ChatLayout 5초 폴링 처리
        lastFetchRef.current = 0;
        await fetchMessages();
      }
    } catch (e: any) {
      const msg = e.response?.data?.error?.message
                || e.response?.data?.message
                || '정리할 수 없어요. 잠시 후 다시 시도해 주세요.';
      setFinalizeError(msg);
    }
  };

  const handleAgreeFinalize = async () => {
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize/agree`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
      } else if (r.data.awaitingPartner) {
        lastFetchRef.current = 0;
        await fetchMessages();
      }
    } catch (e) {
      console.error('Finalize agree failed:', e);
    }
  };

  const handleDeclineFinalize = async () => {
    try {
      await api.post(`/api/sessions/${sessionId}/finalize/decline`);
      // BE에서 dismiss 처리 → 재폴링으로 카드 사라짐 (클라이언트 filter 제거, DB 영속)
      lastFetchRef.current = 0;
      await fetchMessages();
    } catch (e) {
      console.error('Finalize decline failed:', e);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        background: 'var(--P-bg)',
      }}
    >
      {/* Header */}
      <ChatHeader
        isDuo={isDuo}
        canFinalize={canFinalize}
        canInvite={!isDuo}
        onOpenInvite={onOpenInvite}
        onFinalize={handleFinalize}
      />

      {/* Messages */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 12px' }}>
        {messages.length === 0 && <EmptyChatPlaceholder />}
        {messages.map(msg => {
          if (msg.isPartnerJoinNotice) {
            return (
              <PartnerJoinNoticeCard key={msg.id} message={msg} />
            );
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
            <MessageBubble
              key={msg.id}
              message={msg}
              isMine={msg.sender === currentUserSender}
            />
          );
        })}
        {sending && <TypingBubble />}
        <div ref={messagesEndRef} />
      </div>

      {/* 정리하기 에러 안내 */}
      {finalizeError && (
        <div
          style={{
            margin: '0 12px 6px',
            padding: '10px 14px',
            background: '#FFF3F0',
            border: '1px solid #F5C0B0',
            borderRadius: 10,
            fontSize: 13,
            color: '#8A2A10',
            lineHeight: 1.6,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: 8,
          }}
        >
          <span>{finalizeError}</span>
          <button
            onClick={() => setFinalizeError(null)}
            style={{ background: 'none', border: 'none', color: '#8A2A10', fontSize: 16, cursor: 'pointer', padding: 0, lineHeight: 1, flexShrink: 0 }}
            aria-label="닫기"
          >×</button>
        </div>
      )}

      {/* 정리하기 완료 후 대기 안내 (입력창 위) */}
      {isFinalized && (
        <div
          style={{
            margin: '0 12px 6px',
            padding: '12px 16px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 10,
            fontSize: 13,
            color: 'var(--P-sub)',
            lineHeight: 1.6,
            textAlign: 'center',
          }}
        >
          정리하기를 눌러 더 이상 대화를 이어갈 수 없어요.
          <br />
          상대방의 동의를 기다리고 있어요.
        </div>
      )}

      {/* Input */}
      <ChatInput onSend={handleSend} disabled={sending || isFinalized} onCrisis={() => setCrisisLevel1(true)} />

      {crisisLevel1 && (
        <CrisisModal onClose={() => setCrisisLevel1(false)} />
      )}
    </div>
  );
}

function mergeMessages(prev: Message[], incoming: Message[]): Message[] {
  const map = new Map<number, Message>();
  for (const m of prev) map.set(m.id, m);
  for (const m of incoming) map.set(m.id, m); // incoming이 권위 (BE 데이터 우선)
  return Array.from(map.values()).sort((a, b) => {
    const ta = new Date(a.createdAt).getTime();
    const tb = new Date(b.createdAt).getTime();
    return ta !== tb ? ta - tb : a.id - b.id;
  });
}

function TypingBubble() {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'flex-start',
        marginBottom: 10,
        alignItems: 'flex-end',
        gap: 6,
      }}
      aria-live="polite"
      aria-label="중재자가 응답을 작성 중입니다"
    >
      <div
        style={{
          padding: '12px 16px',
          borderRadius: 14,
          background: 'var(--P-card)',
          color: 'var(--P-ink)',
          minWidth: 48,
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <span className="typing-dot" />
        <span className="typing-dot" />
        <span className="typing-dot" />
      </div>
    </div>
  );
}

function EmptyChatPlaceholder() {
  return (
    <div
      style={{
        textAlign: 'center',
        padding: '60px 20px',
        color: 'var(--P-sub)',
      }}
    >
      <div
        className="serif"
        style={{
          fontSize: 18,
          marginBottom: 12,
          color: 'var(--P-ink)',
        }}
      >
        무슨 일이 있으셨어요?
      </div>
      <div style={{ fontSize: 13, lineHeight: 1.7 }}>
        편한 말로, 카톡처럼 한 줄씩 적어주세요.
        <br />
        제가 차분히 들을게요.
        <br /><br />
        <span style={{ fontSize: 12, color: 'var(--P-sub)', opacity: 0.8 }}>
          AI는 누가 옳은지 판단하지 않아요. 정리가 어색하면 언제든 다시 말씀해 주세요.
        </span>
      </div>
    </div>
  );
}
