'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';
import { splitMediatorMessage, calculateTypingDelay } from '@/lib/utils/messageSplitter';

export interface ChatMessage {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

export interface SecondPart {
  messageId: number;
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

interface Options {
  onCrisis?: () => void;
}

export function useChatSession(
  sessionId: string,
  currentUserSender: 'USER_A' | 'USER_B',
  options?: Options,
) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [secondParts, setSecondParts] = useState<SecondPart[]>([]);
  const [sending, setSending] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [pendingSplits, setPendingSplits] = useState<PendingSplit[]>([]);

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

  const setSendingFalseSafely = useCallback(() => {
    const elapsed = Date.now() - sendingShownAtRef.current;
    if (elapsed >= MIN_TYPING_VISIBLE_MS) {
      setSending(false);
    } else {
      setTimeout(() => setSending(false), MIN_TYPING_VISIBLE_MS - elapsed);
    }
  }, []);

  const fetchMessages = useCallback(async (opts?: { full?: boolean }) => {
    if (opts?.full) lastFetchRef.current = 0;
    try {
      const url = lastFetchRef.current
        ? `/api/sessions/${sessionId}/messages?since=${lastFetchRef.current}`
        : `/api/sessions/${sessionId}/messages`;
      const r = await api.get(url);
      if (r.data.length > 0) {
        if (sendingRef.current) {
          const hasNewMediator = r.data.some(
            (m: ChatMessage) =>
              (m.sender === 'MEDIATOR_TO_A' || m.sender === 'MEDIATOR_TO_B') &&
              new Date(m.createdAt).getTime() > sendStartedAtRef.current,
          );
          if (hasNewMediator) setSendingFalseSafely();
        }
        setMessages(prev => {
          const merged = mergeMessages(prev, r.data);
          return processMediatorMessages(merged, setPendingSplits);
        });
        lastFetchRef.current = Date.now();
      }
    } catch (e) {
      console.debug('Messages poll error:', e);
    }
  }, [sessionId, setSendingFalseSafely]);

  useEffect(() => {
    fetchMessages();
  }, [sessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  // 새로고침 후 진행 중 invocation 복원
  useEffect(() => {
    let cancelled = false;
    api.get(`/api/sessions/${sessionId}/invocation-status`)
      .then(r => {
        if (cancelled) return;
        if (r.data?.inProgress) {
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

  // pendingSplits 큐 처리
  useEffect(() => {
    if (pendingSplits.length === 0) {
      setIsTyping(false);
      return;
    }
    const first = pendingSplits[0];
    setIsTyping(true);
    const timeoutId = setTimeout(() => {
      setIsTyping(false);
      setSecondParts(prev => [
        ...prev,
        {
          messageId: first.messageId,
          sender: first.sender,
          content: first.secondPart,
          charCount: first.secondPart.length,
          createdAt: first.createdAt,
        },
      ]);
      setPendingSplits(prev => prev.slice(1));
    }, first.delay);
    typingTimeoutRef.current = timeoutId;
    return () => { if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current); };
  }, [pendingSplits]);

  const handleSend = useCallback(async (content: string) => {
    if (abortControllerRef.current) abortControllerRef.current.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    const myCount = ++sendCountRef.current;
    sendStartedAtRef.current = Date.now() - 1000;
    setSending(true);

    const tempId = -Date.now();
    const optimisticMsg: ChatMessage = {
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
        options?.onCrisis?.();
        if (myCount === sendCountRef.current) setSendingFalseSafely();
        return;
      }
      lastFetchRef.current = 0;
      fetchMessages();
      const fastPollId = setInterval(() => {
        if (myCount !== sendCountRef.current) { clearInterval(fastPollId); return; }
        fetchMessages();
      }, 1000);
      const safetyTimeoutId = setTimeout(() => {
        clearInterval(fastPollId);
        if (myCount === sendCountRef.current) setSendingFalseSafely();
      }, 60000);
      const cleanupCheckId = setInterval(() => {
        if (!sendingRef.current || myCount !== sendCountRef.current) {
          clearInterval(fastPollId);
          clearInterval(cleanupCheckId);
          clearTimeout(safetyTimeoutId);
        }
      }, 500);
    } catch (e: any) {
      if (e.code === 'ERR_CANCELED' || e.name === 'CanceledError') return;
      setMessages(prev => prev.filter(m => m.id !== tempId));
      if (e.response?.status === 409) {
        options?.onCrisis?.();
      } else {
        console.error('Send failed:', e);
      }
      if (myCount === sendCountRef.current) setSendingFalseSafely();
    }
  }, [sessionId, currentUserSender, options, fetchMessages, setSendingFalseSafely]);

  return { messages, secondParts, sending, isTyping, fetchMessages, handleSend };
}

// ── pure helpers ─────────────────────────────────────────────────────────────

function mergeMessages(prev: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const map = new Map<number, ChatMessage>();
  for (const m of prev) map.set(m.id, m);
  for (const m of incoming) map.set(m.id, m);

  const all = Array.from(map.values());
  const positiveBySig = new Set<string>();
  for (const m of all) {
    if (m.id >= 0) positiveBySig.add(`${m.sender}::${m.content}`);
  }
  const confirmed = new Set<number>();
  for (const m of all) {
    if (m.id < 0 && positiveBySig.has(`${m.sender}::${m.content}`)) confirmed.add(m.id);
  }
  return all
    .filter(m => !confirmed.has(m.id))
    .sort((a, b) => {
      const ta = new Date(a.createdAt).getTime();
      const tb = new Date(b.createdAt).getTime();
      return ta !== tb ? ta - tb : a.id - b.id;
    });
}

function processMediatorMessages(
  messages: ChatMessage[],
  setPendingSplits: (cb: (prev: PendingSplit[]) => PendingSplit[]) => void,
): ChatMessage[] {
  const result: ChatMessage[] = [];
  for (const msg of messages) {
    if (msg.sender === 'MEDIATOR_TO_A' || msg.sender === 'MEDIATOR_TO_B') {
      const mediatorSender: 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B' = msg.sender;
      const split = splitMediatorMessage(msg.content);
      if (split) {
        result.push({ ...msg, content: split.first });
        const delay = calculateTypingDelay(split.second.length);
        setPendingSplits(prev => [
          ...prev,
          { messageId: msg.id, sender: mediatorSender, secondPart: split.second, createdAt: msg.createdAt, delay },
        ]);
      } else {
        result.push(msg);
      }
    } else {
      result.push(msg);
    }
  }
  return result;
}
