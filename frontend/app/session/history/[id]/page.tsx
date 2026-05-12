'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { MessageBubble } from '@/components/chat/MessageBubble';
import { PartnerJoinNoticeCard } from '@/components/chat/PartnerJoinNoticeCard';
import { api } from '@/lib/api/client';

interface Message {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

export default function SessionHistoryPage() {
  const router = useRouter();
  const params = useParams();
  const sessionId = params.id as string;

  const [messages, setMessages] = useState<Message[]>([]);
  const [myRole, setMyRole] = useState<'USER_A' | 'USER_B'>('USER_A');
  const [sessionTitle, setSessionTitle] = useState('지난 대화');
  const [hasReport, setHasReport] = useState(false);
  const [reportGenerating, setReportGenerating] = useState(false);
  const [loading, setLoading] = useState(true);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const [sessionRes, msgRes] = await Promise.all([
          api.get(`/api/sessions/${sessionId}`),
          api.get(`/api/sessions/${sessionId}/messages`),
        ]);

        const session = sessionRes.data;
        const r = session.myRole;
        if (r === 'B' || r === 'USER_B') setMyRole('USER_B');

        if (session.soloMode) {
          setSessionTitle('혼자 정리한 이야기');
        } else if (session.partnerNickname) {
          setSessionTitle(`${session.partnerNickname}분과의 대화`);
        }

        if (session.reportId) {
          setHasReport(true);
        } else if (session.status === 'completed') {
          setReportGenerating(true);
        }

        setMessages(msgRes.data || []);
      } catch {
        // 대화 내용 없음 — 빈 상태로 표시
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [sessionId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  }, [messages]);

  useEffect(() => {
    if (!reportGenerating || hasReport) return;
    const interval = setInterval(async () => {
      try {
        const res = await api.get(`/api/sessions/${sessionId}`);
        if (res.data.reportId) {
          setHasReport(true);
          setReportGenerating(false);
        }
      } catch {}
    }, 5000);
    return () => clearInterval(interval);
  }, [reportGenerating, hasReport, sessionId]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await api.delete(`/api/sessions/${sessionId}`);
      router.replace('/history');
    } catch {
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  if (loading) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="지난 대화" tone="P" back onBack={() => router.push('/history')} />
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--P-sub)', fontSize: 14 }}>
          불러오는 중...
        </div>
      </PhoneFrame>
    );
  }

  return (
    <PhoneFrame tone="P">
      <PhoneHeader
        title={sessionTitle}
        tone="P"
        back
        onBack={() => router.push('/history')}
        right={
          <button
            onClick={() => setShowDeleteConfirm(true)}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--P-sub)',
              fontSize: 13,
              padding: '4px 8px',
            }}
          >
            삭제
          </button>
        }
      />

      {/* 메시지 목록 */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 12px' }}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--P-sub)', fontSize: 13, lineHeight: 1.7 }}>
            대화 내용을 불러올 수 없어요.
          </div>
        ) : (
          messages.map(msg => {
            if (msg.isPartnerJoinNotice) {
              return <PartnerJoinNoticeCard key={msg.id} message={msg} />;
            }
            if (msg.isFinalizeSuggestion) {
              return (
                <div
                  key={msg.id}
                  style={{
                    margin: '12px 8px',
                    padding: '12px 16px',
                    background: 'var(--P-card)',
                    border: '1px dashed var(--P-border)',
                    borderRadius: 10,
                    textAlign: 'center',
                    fontSize: 12,
                    color: 'var(--P-sub)',
                    lineHeight: 1.7,
                  }}
                >
                  {msg.content}
                </div>
              );
            }
            return (
              <MessageBubble
                key={msg.id}
                message={msg}
                isMine={msg.sender === myRole}
              />
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* 하단: 결과 보기 버튼 + 읽기 전용 안내 */}
      <div style={{
        padding: '12px 16px',
        borderTop: '1px solid var(--P-border)',
        background: 'var(--P-bg)',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        alignItems: 'center',
      }}>
        {hasReport ? (
          <button
            onClick={() => router.push(`/session/result/${sessionId}`)}
            style={{
              width: '100%',
              padding: '12px',
              background: 'var(--P-ink)',
              color: 'var(--P-card)',
              border: 'none',
              borderRadius: 10,
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            결과 보기
          </button>
        ) : reportGenerating ? (
          <div style={{
            width: '100%',
            padding: '12px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 10,
            fontSize: 14,
            color: 'var(--P-sub)',
            textAlign: 'center',
          }}>
            결과 생성중...
          </div>
        ) : null}
        <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>
          지난 대화를 읽기 전용으로 보고 있어요
        </div>
      </div>

      {/* 삭제 확인 모달 */}
      {showDeleteConfirm && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 999,
          }}
          onClick={() => !deleting && setShowDeleteConfirm(false)}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              background: 'var(--P-bg)',
              borderRadius: 16,
              padding: '24px 20px',
              width: 'min(320px, 85vw)',
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
          >
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--P-ink)', textAlign: 'center' }}>
              대화를 삭제할까요?
            </div>
            <div style={{ fontSize: 13, color: 'var(--P-sub)', textAlign: 'center', lineHeight: 1.6 }}>
              삭제하면 대화 내용과 리포트를<br />다시 볼 수 없어요.
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
                style={{
                  flex: 1,
                  padding: '12px',
                  background: 'var(--P-card)',
                  border: '1px solid var(--P-border)',
                  borderRadius: 10,
                  fontSize: 14,
                  cursor: 'pointer',
                  color: 'var(--P-ink)',
                }}
              >
                취소
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                style={{
                  flex: 1,
                  padding: '12px',
                  background: '#e84c4c',
                  border: 'none',
                  borderRadius: 10,
                  fontSize: 14,
                  cursor: deleting ? 'not-allowed' : 'pointer',
                  color: '#fff',
                  opacity: deleting ? 0.6 : 1,
                }}
              >
                {deleting ? '삭제 중...' : '삭제'}
              </button>
            </div>
          </div>
        </div>
      )}
    </PhoneFrame>
  );
}
