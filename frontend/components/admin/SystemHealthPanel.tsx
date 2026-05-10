'use client';

import { useEffect, useRef, useState } from 'react';
import { getSystemHealth, type SystemHealth, type ComponentHealth, type HealthStatus } from '@/lib/api/admin';

const POLL_OK_MS = 60_000;
const POLL_DEGRADED_MS = 30_000;

const COMPONENTS: Array<{ key: keyof SystemHealth['components']; label: string }> = [
  { key: 'backend', label: 'Backend API' },
  { key: 'database', label: 'Database' },
  { key: 'smtp', label: 'Gmail SMTP' },
  { key: 'anthropic', label: 'Anthropic API' },
];

const COLOR: Record<HealthStatus, { dot: string; bg: string; fg: string; label: string }> = {
  OK: { dot: '#22a06b', bg: '#e7f6ee', fg: '#0e6e3f', label: '정상' },
  WARN: { dot: '#d99000', bg: '#fff5d6', fg: '#7a5500', label: '경고' },
  ERROR: { dot: '#d33636', bg: '#fde8e8', fg: '#a02020', label: '장애' },
};

interface Props {
  /** 외부에서 수동 새로고침 트리거 (값 변경 시 즉시 재요청) */
  refreshSignal?: number;
}

export function SystemHealthPanel({ refreshSignal }: Props) {
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<{ key: string; label: string; comp: ComponentHealth } | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const h = await getSystemHealth();
        if (cancelled) return;
        setHealth(h);
        setError('');
        // 장애 시 30초, 그 외 60초 폴링
        const anyDegraded = Object.values(h.components).some((c) => c.status !== 'OK');
        const interval = anyDegraded ? POLL_DEGRADED_MS : POLL_OK_MS;
        timerRef.current = setTimeout(tick, interval);
      } catch {
        if (cancelled) return;
        setError('헬스 정보를 가져올 수 없습니다.');
        timerRef.current = setTimeout(tick, POLL_DEGRADED_MS);
      }
    };
    tick();
    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [refreshSignal]);

  return (
    <div
      style={{
        marginBottom: 22,
        padding: 16,
        background: 'white',
        borderRadius: 12,
        border: '1px solid #e7e3d8',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 12 }}>
        <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>시스템 헬스</h2>
        <span style={{ fontSize: 11, color: '#888' }}>
          {health?.checkedAt
            ? `마지막 체크 ${relativeAgo(health.checkedAt)}`
            : error || '확인 중…'}
        </span>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 8,
        }}
      >
        {COMPONENTS.map(({ key, label }) => {
          const comp = health?.components?.[key];
          const status = (comp?.status ?? 'WARN') as HealthStatus;
          const color = COLOR[status];
          return (
            <button
              key={key}
              onClick={() => comp && setSelected({ key, label, comp })}
              disabled={!comp}
              style={{
                textAlign: 'left',
                padding: '10px 12px',
                background: color.bg,
                border: `1px solid ${color.bg}`,
                borderRadius: 8,
                cursor: comp ? 'pointer' : 'default',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
              }}
            >
              <span
                aria-hidden
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: color.dot,
                  flexShrink: 0,
                }}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: color.fg }}>{label}</div>
                <div style={{ fontSize: 11, color: color.fg, opacity: 0.85, marginTop: 2 }}>
                  {comp?.message ?? color.label}
                </div>
              </div>
            </button>
          );
        })}
      </div>

      {selected && (
        <DetailModal
          title={selected.label}
          comp={selected.comp}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  );
}

function DetailModal({
  title, comp, onClose,
}: {
  title: string;
  comp: ComponentHealth;
  onClose: () => void;
}) {
  const color = COLOR[comp.status];
  return (
    <div
      role="dialog"
      aria-modal="true"
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 10000, padding: 16,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'white', borderRadius: 12,
          maxWidth: 480, width: '100%',
          padding: 22,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ width: 10, height: 10, borderRadius: '50%', background: color.dot }} />
            <span style={{ fontSize: 15, fontWeight: 600, color: '#111' }}>{title}</span>
            <span style={{ fontSize: 11, color: color.fg, background: color.bg, padding: '2px 8px', borderRadius: 10 }}>
              {color.label}
            </span>
          </div>
          <button
            onClick={onClose}
            aria-label="닫기"
            style={{ background: 'none', border: 'none', fontSize: 20, color: '#888', cursor: 'pointer' }}
          >
            ×
          </button>
        </div>
        {comp.message && (
          <p style={{ fontSize: 13, color: color.fg, margin: '0 0 12px' }}>{comp.message}</p>
        )}
        <div style={{ background: '#f7f6f2', borderRadius: 6, padding: 12 }}>
          {comp.details && Object.keys(comp.details).length > 0 ? (
            <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
              <tbody>
                {Object.entries(comp.details).map(([k, v]) => (
                  <tr key={k}>
                    <td style={{ padding: '4px 8px', color: '#888', minWidth: 120, verticalAlign: 'top' }}>{k}</td>
                    <td style={{ padding: '4px 8px', color: '#333', fontFamily: 'ui-monospace, monospace', overflowWrap: 'anywhere' }}>
                      {String(v ?? '-')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <span style={{ fontSize: 12, color: '#888' }}>추가 정보 없음</span>
          )}
        </div>
      </div>
    </div>
  );
}

function relativeAgo(iso: string): string {
  const sec = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (sec < 60) return `${sec}초 전`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hr = Math.round(min / 60);
  return `${hr}시간 전`;
}
