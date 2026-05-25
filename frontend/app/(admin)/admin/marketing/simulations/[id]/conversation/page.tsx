'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { MessageBubble } from '@/components/chat/MessageBubble';
import { SoloReport } from '@/components/result/solo/SoloReport';
import {
  getSimulation,
  getSimulationMessages,
  getSimulationReport,
  type SimulationMessageResponse,
  type SimulationResponse,
} from '@/lib/api/marketing/simulationApi';
import type { Report } from '@/lib/types';

export default function SimulationConversationPage() {
  const router = useRouter();
  const params = useParams();
  const id = Number(params.id);

  const [simulation, setSimulation] = useState<SimulationResponse | null>(null);
  const [messages, setMessages] = useState<SimulationMessageResponse[]>([]);
  const [report, setReport] = useState<Report | null>(null);
  const [loading, setLoading] = useState(true);
  const [reportOpen, setReportOpen] = useState(true);
  const [error, setError] = useState('');
  const [noSession, setNoSession] = useState(false);

  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    async function load() {
      try {
        const sim = await getSimulation(id);
        setSimulation(sim);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : '알 수 없는 오류';
        setError(`시뮬레이션을 불러오지 못했어요. ${msg}`);
        setLoading(false);
        return;
      }

      try {
        const msgs = await getSimulationMessages(id);
        setMessages(msgs);
      } catch {
        setNoSession(true);
      }

      try {
        const rpt = await getSimulationReport(id);
        setReport(rpt);
      } catch {
        // 리포트 없음 — 정상
      }

      setLoading(false);
    }
    load();
  }, [id]);

  useEffect(() => {
    if (messages.length > 0) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  if (loading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: '#888', fontSize: 14 }}>
        불러오는 중...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: 40 }}>
        <div style={{ background: '#ffe6e6', color: '#b33333', padding: 16, borderRadius: 8, fontSize: 14 }}>
          {error}
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '24px 16px 48px' }}>
      {/* 뒤로 가기 + 메타 스트립 */}
      <div style={{ marginBottom: 20 }}>
        <button
          onClick={() => router.push('/admin/marketing/simulations')}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            fontSize: 13,
            color: '#555',
            padding: '0 0 12px',
            display: 'flex',
            alignItems: 'center',
            gap: 4,
          }}
        >
          &larr; 목록으로
        </button>

        <div
          style={{
            background: '#f8f9fa',
            border: '1px solid #e8e8e8',
            borderRadius: 8,
            padding: '14px 18px',
            display: 'flex',
            gap: 24,
            flexWrap: 'wrap',
            fontSize: 12,
            color: '#555',
          }}
        >
          <span><strong style={{ color: '#1A1A2E' }}>시뮬레이션</strong> #{simulation?.id}</span>
          <span><strong style={{ color: '#1A1A2E' }}>사연</strong> #{simulation?.storyId}</span>
          <span><strong style={{ color: '#1A1A2E' }}>턴수</strong> {simulation?.actualTurnCount ?? '-'}</span>
          <span>
            <strong style={{ color: '#1A1A2E' }}>비용</strong>{' '}
            {simulation?.llmCostUsd ? `$${simulation.llmCostUsd}` : '-'}
          </span>
          {simulation?.finishedAt && (
            <span>
              <strong style={{ color: '#1A1A2E' }}>완료</strong>{' '}
              {new Date(simulation.finishedAt).toLocaleString('ko-KR')}
            </span>
          )}
        </div>
      </div>

      {/* 지난 대화 */}
      <div
        style={{
          background: 'var(--P-bg, #fff)',
          border: '1px solid var(--P-border, #e8e8e8)',
          borderRadius: 12,
          padding: '16px 12px',
          marginBottom: 24,
          minHeight: 200,
        }}
      >
        <div
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: '#888',
            marginBottom: 16,
            paddingBottom: 10,
            borderBottom: '1px solid var(--P-border, #e8e8e8)',
            letterSpacing: '0.04em',
          }}
        >
          지난 대화
        </div>

        {noSession ? (
          <div style={{ textAlign: 'center', color: '#aaa', fontSize: 13, padding: '40px 0', lineHeight: 1.8 }}>
            이 시뮬레이션은 대화 기록이 없어요.<br />
            새 시뮬레이션을 실행하면 대화를 확인할 수 있습니다.
          </div>
        ) : messages.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#aaa', fontSize: 13, padding: '40px 0' }}>
            대화 기록이 없어요.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {messages.map((msg) => (
              <MessageBubble
                key={msg.id}
                message={{
                  id: msg.id,
                  sender: msg.sender,
                  content: msg.content,
                  charCount: msg.charCount,
                  isFinalizeSuggestion: msg.isFinalizeSuggestion,
                  isPartnerJoinNotice: msg.isPartnerJoinNotice,
                  createdAt: msg.createdAt,
                  status: msg.status,
                }}
                isMine={msg.sender === 'USER_A'}
              />
            ))}
            <div ref={chatEndRef} />
          </div>
        )}
      </div>

      {/* 결과 리포트 — 접기/펼치기 */}
      <div
        style={{
          border: '1px solid var(--P-border, #e8e8e8)',
          borderRadius: 12,
          overflow: 'hidden',
        }}
      >
        <button
          onClick={() => setReportOpen((prev) => !prev)}
          style={{
            width: '100%',
            background: '#f8f9fa',
            border: 'none',
            borderBottom: reportOpen ? '1px solid #e8e8e8' : 'none',
            padding: '14px 18px',
            textAlign: 'left',
            cursor: 'pointer',
            fontSize: 13,
            fontWeight: 600,
            color: '#1A1A2E',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          결과 리포트
          <span style={{ fontSize: 11, color: '#888', fontWeight: 400 }}>
            {reportOpen ? '접기' : '펼치기'}
          </span>
        </button>

        {reportOpen && (
          <div>
            {report ? (
              <SoloReport report={report} sessionId={report.sessionId} />
            ) : (
              <div
                style={{
                  padding: '32px 20px',
                  textAlign: 'center',
                  color: '#888',
                  fontSize: 13,
                }}
              >
                리포트 생성 실패 또는 아직 생성 중이에요.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
