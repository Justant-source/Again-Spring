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
  const [finalizePending, setFinalizePending] = useState(false);
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

  // 상대가 정리를 거부하면 session.status가 chatting_duo로 돌아옴 → pending 초기화
  useEffect(() => {
    if (session?.status === 'chatting_duo') {
      setFinalizePending(false);
    }
  }, [session?.status]);

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
        // 위기 메시지는 서버가 거절하므로 임시 메시지 제거
      setMessages(prev => prev.filter(m => m.id !== tempId));
        setCrisisLevel1(true);
        return;
      }
      // 임시 메시지의 id만 서버 실제 id로 교체 (content/sender는 optimistic 값 유지)
      // 미디에이터 메시지는 BE가 sender를 내려주지 않으므로 currentUserSender로 추론
      const mediatorSender = (
        currentUserSender === 'USER_A' ? 'MEDIATOR_TO_A' : 'MEDIATOR_TO_B'
      ) as Message['sender'];
      setMessages(prev => [
        ...prev.map(m => m.id === tempId ? { ...m, id: r.data.userMessage.id } : m),
        {
          id: r.data.mediatorMessage.id,
          sender: mediatorSender,
          content: r.data.mediatorMessage.content,
          charCount: r.data.mediatorMessage.charCount,
          isFinalizeSuggestion: r.data.mediatorMessage.isFinalizeSuggestion ?? false,
          isPartnerJoinNotice: false,
          createdAt: r.data.mediatorMessage.createdAt,
        },
      ]);
      lastFetchRef.current = Date.now();
      // 정리 제안이 트리거됐으면 별도 저장된 isFinalizeSuggestion 메시지를 즉시 불러옴
      if (r.data.finalizeSuggested) {
        lastFetchRef.current = 0;
        await fetchMessages();
      }
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
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
      } else if (r.data.awaitingPartner) {
        setFinalizePending(true);
      }
    } catch (e: any) {
      alert(e.response?.data?.message || '정리할 수 없어요');
    }
  };

  const handleAgreeFinalize = async () => {
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize/agree`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
      } else if (r.data.awaitingPartner) {
        setFinalizePending(true);
      }
    } catch (e) {
      console.error('Finalize agree failed:', e);
    }
  };

  const handleDeclineFinalize = async () => {
    try {
      await api.post(`/api/sessions/${sessionId}/finalize/decline`);
      setMessages(prev => prev.filter(m => !m.isFinalizeSuggestion));
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
                pending={finalizePending}
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

      {/* Input */}
      <ChatInput onSend={handleSend} disabled={sending} onCrisis={() => setCrisisLevel1(true)} />

      {crisisLevel1 && (
        <CrisisModal onClose={() => setCrisisLevel1(false)} />
      )}
    </div>
  );
}

function mergeMessages(prev: Message[], incoming: Message[]): Message[] {
  const ids = new Set(prev.map(m => m.id));
  return [...prev, ...incoming.filter(m => !ids.has(m.id))];
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
