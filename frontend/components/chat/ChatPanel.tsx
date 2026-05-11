'use client';

import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { ChatHeader } from './ChatHeader';
import { MessageBubble } from './MessageBubble';
import { ChatInput } from './ChatInput';
import { FinalizeSuggestionCard } from './FinalizeSuggestionCard';
import { CrisisModal } from './CrisisModal';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';
import { PartnerJoinNoticeCard } from './PartnerJoinNoticeCard';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';
import { splitMediatorMessage, calculateTypingDelay } from '@/lib/utils/messageSplitter';
import { RelationshipColorSync } from '@/components/shared/RelationshipColorSync';

interface Message {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

interface SecondPart {
  messageId: number; // 원본 메시지 ID
  sender: 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  createdAt: string;
}

interface PendingSplit {
  messageId: number;
  sender: 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  secondPart: string;
  createdAt: string;
  delay: number;
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
  const [secondParts, setSecondParts] = useState<SecondPart[]>([]);
  const [sending, setSending] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [crisisLevel1, setCrisisLevel1] = useState(false);
  const [showCrisisResource, setShowCrisisResource] = useState(false);
  const [finalizeError, setFinalizeError] = useState<string | null>(null);
  const [isTyping, setIsTyping] = useState(false);
  const [pendingSplits, setPendingSplits] = useState<PendingSplit[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const lastFetchRef = useRef<number>(0);
  const abortControllerRef = useRef<AbortController | null>(null);
  const sendCountRef = useRef(0);
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const sendingRef = useRef(false);
  const sendStartedAtRef = useRef<number>(0);
  const sendingShownAtRef = useRef<number>(0);
  const MIN_TYPING_VISIBLE_MS = 800;

  useEffect(() => {
    sendingRef.current = sending;
    if (sending) sendingShownAtRef.current = Date.now();
  }, [sending]);

  // sending=false를 800ms 최소 노출 보장 후 호출 (인디케이터 깜빡임 방지)
  const setSendingFalseSafely = () => {
    const elapsed = Date.now() - sendingShownAtRef.current;
    if (elapsed >= MIN_TYPING_VISIBLE_MS) {
      setSending(false);
    } else {
      setTimeout(() => setSending(false), MIN_TYPING_VISIBLE_MS - elapsed);
    }
  };

  const fetchMessages = async () => {
    try {
      const url = lastFetchRef.current
        ? `/api/sessions/${sessionId}/messages?since=${lastFetchRef.current}`
        : `/api/sessions/${sessionId}/messages`;
      const r = await api.get(url);
      if (r.data.length > 0) {
        // sending 상태일 때만, 송신 시작 후 도착한 새 중재자 메시지가 있으면 typing indicator 종료
        // (새로고침 후 전체 fetch에 포함된 과거 중재자 메시지로 인한 오인 종료 방지)
        if (sendingRef.current) {
          const hasNewMediator = r.data.some(
            (m: Message) =>
              (m.sender === 'MEDIATOR_TO_A' || m.sender === 'MEDIATOR_TO_B') &&
              new Date(m.createdAt).getTime() > sendStartedAtRef.current
          );
          if (hasNewMediator) {
            setSendingFalseSafely();
          }
        }
        // functional updater 사용 — closure로 잡힌 stale messages 대신 최신 state(prev)와 merge.
        // 이전 버그: handleSend가 optimistic 추가 후 fetchMessages를 호출하면 stale `messages`
        // (optimistic 없음)와 merge되어 setMessages(processed)가 optimistic을 덮어써
        // 메시지가 잠시 화면에서 사라지는 깜빡임 발생.
        setMessages(prev => {
          const merged = mergeMessages(prev, r.data);
          return processMediatorMessages(merged, setPendingSplits);
        });
        lastFetchRef.current = Date.now();
      }
    } catch (e) {
      console.debug('Messages poll error:', e);
    }
  };

  useEffect(() => {
    fetchMessages();
  }, [sessionId]);

  // 새로고침 후 진행 중 invocation이 있으면 TypingBubble 복원
  // (mount 시 1회만 호출, 이후 정상 폴링이 mediator 도착 시 sending=false 처리)
  useEffect(() => {
    let cancelled = false;
    api.get(`/api/sessions/${sessionId}/invocation-status`)
      .then(r => {
        if (cancelled) return;
        if (r.data?.inProgress) {
          // sendStartedAt: BE가 반환한 사용자 최근 메시지 시각 기준 (1초 마진).
          // 이 시각보다 최근의 mediator 메시지만 "신규 응답"으로 판단되도록 폴링 검사 기준 정렬.
          // BE가 시각 못 주면 60초 전으로 fallback.
          const lastAt = r.data?.lastUserMessageAt;
          sendStartedAtRef.current = lastAt
            ? new Date(lastAt).getTime() - 1000
            : Date.now() - 60_000;
          setSending(true);
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [sessionId]);

  usePolling(fetchMessages, 3000);

  // pendingSplits 큐 처리: 첫 번째 분할 항목 처리
  useEffect(() => {
    if (pendingSplits.length === 0) {
      setIsTyping(false);
      return;
    }

    const first = pendingSplits[0];

    // 1단계: 타이핑 시작 (delay 전)
    setIsTyping(true);

    // 2단계: delay 후 isTyping false + 두 번째 부분 메시지 추가
    const timeoutId = setTimeout(() => {
      setIsTyping(false);

      const secondPartMsg: SecondPart = {
        messageId: first.messageId,
        sender: first.sender,
        content: first.secondPart,
        charCount: first.secondPart.length,
        createdAt: first.createdAt,
      };

      setSecondParts(prev => [...prev, secondPartMsg]);

      // 큐에서 처리된 항목 제거
      setPendingSplits(prev => prev.slice(1));
    }, first.delay);

    typingTimeoutRef.current = timeoutId;

    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
    };
  }, [pendingSplits]);

  // isFinalized: 본인이 정리하기를 누른 + 상대 동의 대기 중 → 입력창 비활성화
  const myAgreed = currentUserSender === 'USER_A'
    ? session?.finalizeAgreedByA
    : session?.finalizeAgreedByB;
  const isFinalized = session?.status === 'awaiting_finalization' && !!myAgreed;

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'instant' });
  }, [messages, secondParts, sending, isTyping]);

  const myMessages = messages.filter(m => m.sender === currentUserSender);
  const canFinalize = myMessages.length >= 3;

  const handleSend = async (content: string) => {
    // 진행 중인 요청이 있으면 취소하고 새 메시지로 재시작
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    const myCount = ++sendCountRef.current;
    // 송신 시작 시점을 1초 전으로 잡아 BE 처리 시점의 약간의 시계 오차를 흡수
    sendStartedAtRef.current = Date.now() - 1000;
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
      const r = await api.post(
        `/api/sessions/${sessionId}/messages`,
        { content },
        { signal: controller.signal },
      );
      if (r.data.crisisLevel === 1) {
        setMessages(prev => prev.filter(m => m.id !== tempId));
        setCrisisLevel1(true);
        if (myCount === sendCountRef.current) setSendingFalseSafely();
        return;
      }
      // optimistic 메시지는 명시적으로 제거하지 않음 — 폴링이 server user 메시지를
      // 가져오면 mergeMessages가 자동으로 음수 ID(optimistic)를 cleanup함.
      // (race condition으로 사용자 메시지가 깜빡 사라지는 이슈 차단)

      // V1.5: BE는 즉시 응답하고 중재자 메시지는 폴링으로 도착
      // sending=true 유지 → fetchMessages()가 중재자 메시지 수신 시 false 처리
      // 즉시 1회 폴링 트리거 + 1초 후 재폴링으로 빠른 도착 감지
      lastFetchRef.current = 0;
      fetchMessages();
      const fastPollId = setInterval(() => {
        if (myCount !== sendCountRef.current) {
          clearInterval(fastPollId);
          return;
        }
        fetchMessages();
      }, 1000);

      // 안전 타임아웃: 60초 후 강제 sending=false (네트워크/서버 장애 대비)
      const safetyTimeoutId = setTimeout(() => {
        clearInterval(fastPollId);
        if (myCount === sendCountRef.current) {
          setSendingFalseSafely();
        }
      }, 60000);

      // sending이 false가 되면 fastPoll/safetyTimeout 정리
      const cleanupCheckId = setInterval(() => {
        if (!sendingRef.current || myCount !== sendCountRef.current) {
          clearInterval(fastPollId);
          clearInterval(cleanupCheckId);
          clearTimeout(safetyTimeoutId);
        }
      }, 500);

      return;
    } catch (e: any) {
      // 연속 전송으로 인한 취소 — 이후 새 요청이 진행 중이므로 정리만
      if (e.code === 'ERR_CANCELED' || e.name === 'CanceledError') {
        return;
      }
      setMessages(prev => prev.filter(m => m.id !== tempId));
      if (e.response?.status === 409) {
        setCrisisLevel1(true);
      } else {
        console.error('Send failed:', e);
      }
      if (myCount === sendCountRef.current) setSendingFalseSafely();
    }
  };

  const handleFinalize = async () => {
    if (finalizing) return;
    setFinalizeError(null);
    setFinalizing(true);
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
        return;
      } else if (r.data.awaitingPartner) {
        // 본인 측 카드는 BE에서 dismiss됨 → 재폴링으로 반영
        lastFetchRef.current = 0;
        await fetchMessages();
      }
    } catch (e: any) {
      const msg = e.response?.data?.error?.message
                || e.response?.data?.message
                || '정리할 수 없어요. 잠시 후 다시 시도해 주세요.';
      setFinalizeError(msg);
    } finally {
      setFinalizing(false);
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
      <RelationshipColorSync type={session?.relationType ?? null} />
      {/* Header */}
      <ChatHeader
        isDuo={isDuo}
        canFinalize={canFinalize}
        canInvite={!isDuo}
        onOpenInvite={onOpenInvite}
        onFinalize={handleFinalize}
        finalizing={finalizing}
        onOpenCrisis={() => setShowCrisisResource(true)}
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
            <div key={msg.id}>
              <MessageBubble
                message={msg}
                isMine={msg.sender === currentUserSender}
              />
              {/* 같은 원본 메시지의 두 번째 부분 표시 */}
              {secondParts
                .filter(sp => sp.messageId === msg.id)
                .map((sp, idx) => (
                  <MessageBubble
                    key={`${msg.id}-part2-${idx}`}
                    message={{
                      id: msg.id,
                      sender: sp.sender,
                      content: sp.content,
                      charCount: sp.charCount,
                      isFinalizeSuggestion: false,
                      isPartnerJoinNotice: false,
                      createdAt: sp.createdAt,
                    }}
                    isMine={msg.sender === currentUserSender}
                  />
                ))}
            </div>
          );
        })}
        {sending && <TypingBubble />}
        {isTyping && <TypingBubble />}
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
      <ChatInput onSend={handleSend} disabled={isFinalized} onCrisis={() => setCrisisLevel1(true)} />

      {crisisLevel1 && (
        <CrisisModal onClose={() => setCrisisLevel1(false)} />
      )}
      <CrisisResourceModal open={showCrisisResource} onClose={() => setShowCrisisResource(false)} />
    </div>
  );
}

function mergeMessages(prev: Message[], incoming: Message[]): Message[] {
  const map = new Map<number, Message>();
  for (const m of prev) map.set(m.id, m);
  for (const m of incoming) map.set(m.id, m); // incoming이 권위 (BE 데이터 우선)

  const all = Array.from(map.values());
  // optimistic(음수 ID) 메시지가 server user(양수 ID, 같은 sender·content)와 중복이면 제거
  // 폴링 응답이 사용자 메시지를 가져오는 즉시 음수 ID 자동 cleanup
  const optimisticConfirmed = new Set<number>();
  const positiveBySig = new Set<string>();
  for (const m of all) {
    if (m.id >= 0) positiveBySig.add(`${m.sender}::${m.content}`);
  }
  for (const m of all) {
    if (m.id < 0 && positiveBySig.has(`${m.sender}::${m.content}`)) {
      optimisticConfirmed.add(m.id);
    }
  }

  return all
    .filter(m => !optimisticConfirmed.has(m.id))
    .sort((a, b) => {
      const ta = new Date(a.createdAt).getTime();
      const tb = new Date(b.createdAt).getTime();
      return ta !== tb ? ta - tb : a.id - b.id;
    });
}

/**
 * 중재자 메시지에서 분할이 필요한 메시지를 찾아 처리
 * 분할이 필요하면: 첫 번째 부분만 messages에 추가, 두 번째 부분을 pendingSplits에 추가
 * 분할이 불필요하면: 원본 메시지를 그대로 반환
 */
function processMediatorMessages(
  messages: Message[],
  setPendingSplits: (cb: (prev: PendingSplit[]) => PendingSplit[]) => void
): Message[] {
  const result: Message[] = [];

  for (const msg of messages) {
    // 중재자 메시지만 분할 처리
    if (msg.sender === 'MEDIATOR_TO_A' || msg.sender === 'MEDIATOR_TO_B') {
      const split = splitMediatorMessage(msg.content);

      if (split) {
        // 첫 번째 부분만 messages에 추가
        result.push({
          ...msg,
          content: split.first,
        });

        // 두 번째 부분을 pendingSplits에 추가
        const delay = calculateTypingDelay(split.second.length);
        const mediatorSender: 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B' = msg.sender;
        setPendingSplits(prev => [
          ...prev,
          {
            messageId: msg.id,
            sender: mediatorSender,
            secondPart: split.second,
            createdAt: msg.createdAt,
            delay,
          },
        ]);
      } else {
        // 분할 불필요 → 원본 그대로
        result.push(msg);
      }
    } else {
      // 일반 사용자 메시지
      result.push(msg);
    }
  }

  return result;
}

function TypingBubble() {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'flex-start',
        marginBottom: 12,
        alignItems: 'flex-end',
        gap: 6,
      }}
      aria-live="polite"
      aria-label="중재자가 응답을 작성 중입니다"
    >
      <div
        style={{
          padding: '14px 18px',
          borderRadius: '4px 14px 14px 14px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          minWidth: 56,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
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
