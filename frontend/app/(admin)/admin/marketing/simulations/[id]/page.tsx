'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import { AdminSection } from '@/components/admin/AdminSection';
import { getSimulation, cancelSimulation, type SimulationResponse } from '@/lib/api/marketing/simulationApi';

function parsePersona(raw: string | undefined): Record<string, string> | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    // DB에 {personaA:{...},personaB:{...}} 형태로 저장된 경우 분리해서 반환
    if (parsed.personaA && typeof parsed.personaA === 'object') return parsed.personaA;
    if (typeof parsed === 'object') return parsed;
    return null;
  } catch {
    return null;
  }
}

function PersonaSection({ personaA, personaB }: { personaA?: string; personaB?: string }) {
  const pA = parsePersona(personaA);
  const pB = parsePersona(personaB);

  // DB 저장 버그로 personaA 컬럼에 전체 JSON이 있을 때 분리
  const rawParsed = (() => { try { return personaA ? JSON.parse(personaA) : null; } catch { return null; } })();
  const resolvedA = rawParsed?.personaA ?? pA;
  const resolvedB = rawParsed?.personaB ?? pB;

  if (!resolvedA && !resolvedB) return null;

  const renderField = (label: string, val: unknown) =>
    val ? <div key={label} style={{ marginBottom: 4 }}><span style={{ color: '#888', fontSize: 11 }}>{label}: </span><span style={{ fontSize: 12 }}>{String(val)}</span></div> : null;

  return (
    <>
      <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>페르소나</h3>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 24 }}>
        {resolvedA && (
          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 8px', fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>당사자 A</p>
            {Object.entries(resolvedA).map(([k, v]) => renderField(k, v))}
          </div>
        )}
        {resolvedB && (
          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 8px', fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>당사자 B</p>
            {Object.entries(resolvedB).map(([k, v]) => renderField(k, v))}
          </div>
        )}
      </div>
    </>
  );
}

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  QUEUED: { bg: '#e6f0ff', fg: '#0066cc' },
  RUNNING: { bg: '#fff9e6', fg: '#b8860b' },
  COMPLETED: { bg: '#e6f7e6', fg: '#2d7a2d' },
  FAILED: { bg: '#ffe6e6', fg: '#b33333' },
  CANCELED: { bg: '#f5f5f5', fg: '#666' },
};

export default function SimulationDetailPage() {
  const router = useRouter();
  const params = useParams();
  const simulationId = Number(params.id);

  const [simulation, setSimulation] = useState<SimulationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelLoading, setCancelLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);

  useEffect(() => {
    loadSimulation();
  }, [simulationId]);

  useEffect(() => {
    if (!autoRefresh || !simulation) return;

    if (simulation.status !== 'QUEUED' && simulation.status !== 'RUNNING') {
      setAutoRefresh(false);
      return;
    }

    const interval = setInterval(() => {
      loadSimulation();
    }, 3000);

    return () => clearInterval(interval);
  }, [autoRefresh, simulation]);

  async function loadSimulation() {
    try {
      const data = await getSimulation(simulationId);
      setSimulation(data);
      setError('');

      // Disable auto-refresh if status is not active
      if (data.status !== 'QUEUED' && data.status !== 'RUNNING') {
        setAutoRefresh(false);
      }
    } catch (e: any) {
      setError('시뮬레이션을 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel() {
    if (!simulation) return;

    setCancelLoading(true);
    try {
      await cancelSimulation(simulationId);
      setError('');
      await loadSimulation();
    } catch (e: any) {
      setError(`취소 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setCancelLoading(false);
    }
  }

  if (loading) {
    return (
      <AdminSection title="시뮬레이션 상세">
        <p style={{ color: '#888', fontSize: 13 }}>불러오는 중…</p>
      </AdminSection>
    );
  }

  if (!simulation) {
    return (
      <AdminSection title="시뮬레이션 상세">
        <p style={{ color: '#b33333', fontSize: 13 }}>시뮬레이션을 찾을 수 없어요.</p>
      </AdminSection>
    );
  }

  const colors = STATUS_BADGE_COLORS[simulation.status] || { bg: '#f0f0f0', fg: '#666' };

  return (
    <>
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .spinner {
          display: inline-block;
          width: 16px;
          height: 16px;
          border: 2px solid #ddd;
          border-top: 2px solid #1A1A2E;
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }
      `}</style>

      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
        <button
          onClick={() => router.back()}
          style={{
            padding: '4px 10px',
            background: 'white',
            border: '1px solid #ddd',
            borderRadius: 4,
            cursor: 'pointer',
            fontSize: 12,
          }}
        >
          돌아가기
        </button>
      </div>

      <AdminSection title={`시뮬레이션 #${simulation.id}`}>
        {error && (
          <div
            style={{
              padding: 12,
              background: '#ffe6e6',
              color: '#b33333',
              borderRadius: 6,
              marginBottom: 12,
              fontSize: 13,
            }}
          >
            {error}
          </div>
        )}

        {/* Metadata Grid */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 12,
            marginBottom: 24,
          }}
        >
          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>ID</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>{simulation.id}</p>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>사연ID</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>
              <Link
                href={`/admin/marketing/stories/${simulation.storyId}`}
                style={{ color: '#0066cc', textDecoration: 'none' }}
              >
                {simulation.storyId}
              </Link>
            </p>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>상태</p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {simulation.status === 'RUNNING' && <div className="spinner" />}
              <span
                style={{
                  padding: '2px 8px',
                  borderRadius: 4,
                  fontSize: 11,
                  fontWeight: 600,
                  background: colors.bg,
                  color: colors.fg,
                  display: 'inline-block',
                }}
              >
                {simulation.status === 'RUNNING' ? '처리 중' : simulation.status}
              </span>
            </div>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>턴수</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>{simulation.turnCount || '-'}</p>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>비용 (USD)</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>
              {simulation.llmCostUsd ? `$${Number(simulation.llmCostUsd).toFixed(4)}` : '-'}
            </p>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>시작</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>
              {simulation.startedAt ? new Date(simulation.startedAt).toLocaleString('ko-KR') : '-'}
            </p>
          </div>

          <div style={{ padding: 12, background: '#f9f8f5', borderRadius: 6 }}>
            <p style={{ margin: '0 0 4px', fontSize: 11, color: '#888', fontWeight: 600 }}>완료</p>
            <p style={{ margin: 0, fontSize: 13, color: '#1A1A2E' }}>
              {simulation.finishedAt ? new Date(simulation.finishedAt).toLocaleString('ko-KR') : '-'}
            </p>
          </div>
        </div>

        {/* Personas */}
        {(simulation.personaA || simulation.personaB) && (
          <PersonaSection personaA={simulation.personaA} personaB={simulation.personaB} />
        )}

        {/* Conversation Log */}
        {simulation.conversationLog && (
          <>
            <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>대화 내용</h3>
            <div style={{ marginBottom: 24 }}>
              {simulation.conversationLog
                .split('\n')
                .filter((line) => line.trim())
                .map((line, idx) => {
                  const isA = line.startsWith('A:');
                  const isB = line.startsWith('B:');
                  const isMediator = line.startsWith('Mediator:');
                  const text = line.replace(/^(A:|B:|Mediator:)\s*/, '');
                  const sender = isA ? 'A' : isB ? 'B' : isMediator ? 'Mediator' : null;
                  if (!sender) return null;

                  const styles: Record<string, { bg: string; fg: string; align: 'flex-start' | 'flex-end' | 'center'; label: string }> = {
                    A: { bg: '#e6f0ff', fg: '#1a3a6e', align: 'flex-start', label: '당사자 A' },
                    B: { bg: '#fff0e6', fg: '#7a3a1a', align: 'flex-end', label: '당사자 B' },
                    Mediator: { bg: '#e6f7e6', fg: '#1a5c1a', align: 'center', label: '중재자' },
                  };
                  const s = styles[sender];

                  return (
                    <div key={idx} style={{ display: 'flex', justifyContent: s.align, marginBottom: 10 }}>
                      <div style={{ maxWidth: '75%' }}>
                        <div style={{ fontSize: 10, color: '#888', marginBottom: 3, textAlign: s.align === 'center' ? 'center' : undefined }}>
                          {s.label}
                        </div>
                        <div style={{
                          padding: '8px 12px',
                          background: s.bg,
                          color: s.fg,
                          borderRadius: 8,
                          fontSize: 13,
                          lineHeight: 1.5,
                          wordBreak: 'break-word',
                        }}>
                          {text}
                        </div>
                      </div>
                    </div>
                  );
                })}
            </div>
          </>
        )}

        {/* Error Message */}
        {simulation.status === 'FAILED' && simulation.errorMessage && (
          <>
            <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600, color: '#b33333' }}>
              오류 메시지
            </h3>
            <div
              style={{
                padding: 12,
                background: '#fff5f5',
                border: '1px solid #ffcccc',
                borderRadius: 6,
                marginBottom: 24,
              }}
            >
              <p
                style={{
                  margin: 0,
                  fontSize: 12,
                  color: '#b33333',
                  lineHeight: 1.5,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontFamily: 'monospace',
                }}
              >
                {simulation.errorMessage}
              </p>
            </div>
          </>
        )}

        {/* Actions */}
        <div style={{ display: 'flex', gap: 8 }}>
          {(simulation.status === 'QUEUED' || simulation.status === 'RUNNING') && (
            <button
              onClick={handleCancel}
              disabled={cancelLoading}
              style={{
                padding: '9px 18px',
                background: '#ffe6e6',
                color: '#b33333',
                border: '1px solid #ffcccc',
                borderRadius: 6,
                cursor: cancelLoading ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
                opacity: cancelLoading ? 0.6 : 1,
              }}
            >
              {cancelLoading ? '취소 중...' : '취소'}
            </button>
          )}
          <Link
            href="/admin/marketing/simulations"
            style={{
              padding: '9px 18px',
              background: 'white',
              color: '#1A1A2E',
              border: '1px solid #ddd',
              borderRadius: 6,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
              textDecoration: 'none',
              display: 'inline-block',
            }}
          >
            목록으로
          </Link>
        </div>
      </AdminSection>
    </>
  );
}
