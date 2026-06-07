'use client';

import { useEffect, useState } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { SystemHealthPanel } from '@/components/admin/SystemHealthPanel';
import { LlmFailureRateChart } from '@/components/admin/LlmFailureRateChart';
import { reloadPrompts, getSystemLogs, type SystemLogEntry } from '@/lib/api/admin/system';

const KNOWN_FEATURE_FLAGS = [
  { key: 'app.admin.enabled', label: 'Admin Panel', description: '관리자 패널 활성화' },
  { key: 'app.features.marketing.enabled', label: 'Marketing', description: '마케팅 기능' },
  { key: 'app.features.crisis.enabled', label: 'Crisis Monitor', description: '위기 모니터링' },
];

export default function SystemPage() {
  const user = useUserStore((s) => s.user);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [reloadingPrompts, setReloadingPrompts] = useState(false);
  const [logs, setLogs] = useState<SystemLogEntry[]>([]);
  const [logLevel, setLogLevel] = useState<'ERROR' | 'WARN' | ''>('');
  const [logsLoading, setLogsLoading] = useState(false);
  const [expandedLog, setExpandedLog] = useState<number | null>(null);

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  async function loadLogs(level: 'ERROR' | 'WARN' | '') {
    setLogsLoading(true);
    try {
      const data = await getSystemLogs(level || undefined, 200);
      setLogs(data.reverse());
    } catch {
      setLogs([]);
    } finally {
      setLogsLoading(false);
    }
  }

  useEffect(() => {
    if (isAuthorizedAdmin) loadLogs(logLevel);
  }, [isAuthorizedAdmin, logLevel]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleReloadPrompts() {
    setReloadingPrompts(true);
    try {
      await reloadPrompts();
      alert('프롬프트가 재로드되었습니다.');
    } catch {
      alert('프롬프트 재로드 중 오류가 발생했습니다.');
    } finally {
      setReloadingPrompts(false);
    }
  }

  if (!isAuthorizedAdmin) {
    return <div style={{ padding: 40, fontFamily: 'sans-serif', color: '#e55' }}>권한이 없습니다.</div>;
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      {/* 헤더 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid #e7e3d8',
          padding: '12px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>시스템</div>
      </header>

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 60px' }}>
        {/* 시스템 헬스 */}
        <SystemHealthPanel refreshSignal={refreshSignal} />

        {/* LLM 실패율 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            LLM 호출 실패율 (최근 7일)
          </h2>
          <LlmFailureRateChart days={7} refreshSignal={refreshSignal} />
        </div>

        {/* 기능 플래그 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            활성화된 기능 (Feature Flags)
          </h2>
          <div style={{ display: 'grid', gap: 12 }}>
            {KNOWN_FEATURE_FLAGS.map((flag) => (
              <div
                key={flag.key}
                style={{
                  padding: 12,
                  background: '#f7f6f2',
                  border: '1px solid #e7e3d8',
                  borderRadius: 6,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>
                    {flag.label}
                  </div>
                  <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>
                    {flag.description}
                  </div>
                  <div style={{ fontSize: 10, color: '#aaa', marginTop: 2, fontFamily: 'ui-monospace' }}>
                    {flag.key}
                  </div>
                </div>
                <div
                  style={{
                    fontSize: 11,
                    padding: '2px 8px',
                    background: '#22a06b',
                    color: 'white',
                    borderRadius: 4,
                  }}
                >
                  활성화
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 애플리케이션 로그 (ERROR / WARN) */}
        <div style={{ marginBottom: 22, padding: 16, background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>
              애플리케이션 로그
            </h2>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              {(['', 'ERROR', 'WARN'] as const).map((lv) => (
                <button
                  key={lv}
                  onClick={() => setLogLevel(lv)}
                  style={{
                    padding: '3px 10px', borderRadius: 4, fontSize: 11, fontWeight: 500, cursor: 'pointer',
                    border: '1px solid',
                    borderColor: logLevel === lv ? '#1A1A2E' : '#e7e3d8',
                    background: logLevel === lv ? '#1A1A2E' : 'white',
                    color: logLevel === lv ? 'white' : (lv === 'ERROR' ? '#d33' : lv === 'WARN' ? '#c80' : '#666'),
                  }}
                >
                  {lv || '전체'}
                </button>
              ))}
              <button
                onClick={() => loadLogs(logLevel)}
                style={{ padding: '3px 10px', borderRadius: 4, fontSize: 11, cursor: 'pointer', border: '1px solid #e7e3d8', background: 'white', color: '#666' }}
              >
                ↺
              </button>
            </div>
          </div>

          {logsLoading ? (
            <div style={{ fontSize: 12, color: '#888', padding: '12px 0' }}>불러오는 중...</div>
          ) : logs.length === 0 ? (
            <div style={{ fontSize: 12, color: '#aaa', padding: '12px 0', textAlign: 'center' }}>수집된 로그가 없습니다</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 480, overflowY: 'auto' }}>
              {logs.map((log, i) => (
                <div
                  key={i}
                  onClick={() => setExpandedLog(expandedLog === i ? null : i)}
                  style={{
                    padding: '7px 10px', borderRadius: 6, cursor: 'pointer', fontSize: 11,
                    background: log.level === 'ERROR' ? '#fdf2f2' : '#fffbe6',
                    border: `1px solid ${log.level === 'ERROR' ? '#f5c0c0' : '#ffe58f'}`,
                  }}
                >
                  <div style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}>
                    <span style={{
                      fontWeight: 700, flexShrink: 0,
                      color: log.level === 'ERROR' ? '#c0392b' : '#b7820a',
                    }}>
                      {log.level}
                    </span>
                    <span style={{ color: '#666', flexShrink: 0 }}>
                      {new Date(log.timestamp).toLocaleTimeString('ko-KR')}
                    </span>
                    <span style={{ color: '#999', flexShrink: 0, fontFamily: 'ui-monospace', fontSize: 10 }}>
                      {log.logger.split('.').pop()}
                    </span>
                    <span style={{ color: '#333', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                      {log.message}
                    </span>
                  </div>
                  {expandedLog === i && (
                    <div style={{ marginTop: 6, paddingTop: 6, borderTop: '1px solid rgba(0,0,0,0.06)' }}>
                      <div style={{ color: '#555', wordBreak: 'break-all', whiteSpace: 'pre-wrap', marginBottom: log.exception ? 6 : 0 }}>
                        {log.message}
                      </div>
                      {log.exception && (
                        <div style={{ color: '#c0392b', fontFamily: 'ui-monospace', fontSize: 10, wordBreak: 'break-all' }}>
                          {log.exception}
                        </div>
                      )}
                      <div style={{ color: '#999', fontSize: 10, marginTop: 4, fontFamily: 'ui-monospace' }}>
                        {log.logger}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 프롬프트 재로드 */}
        <div
          style={{
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            LLM 프롬프트
          </h2>
          <p style={{ fontSize: 12, color: '#666', margin: '0 0 12px' }}>
            메모리에 로드된 프롬프트를 디스크에서 다시 읽어옵니다. 프롬프트 파일을 수정한 후 사용하세요.
          </p>
          <button
            onClick={handleReloadPrompts}
            disabled={reloadingPrompts}
            style={{
              padding: '10px 20px',
              background: '#1A1A2E',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: reloadingPrompts ? 'wait' : 'pointer',
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            {reloadingPrompts ? '재로드 중...' : '프롬프트 재로드'}
          </button>
        </div>
      </div>
    </div>
  );
}
