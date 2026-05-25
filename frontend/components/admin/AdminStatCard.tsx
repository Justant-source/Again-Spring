import React from 'react';

interface AdminStatCardProps {
  label: string;
  value: string | number;
  delta?: string;
  deltaPositive?: boolean;
}

export function AdminStatCard({ label, value, delta, deltaPositive }: AdminStatCardProps) {
  return (
    <div
      style={{
        padding: '14px 18px',
        background: '#fafaf5',
        borderRadius: 8,
        border: '1px solid #e7e3d8',
      }}
    >
      <div style={{ fontSize: 11, color: '#888', marginBottom: 6 }}>{label}</div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <div style={{ fontSize: 22, fontWeight: 700, color: '#1A1A2E' }}>{value}</div>
        {delta && (
          <div
            style={{
              fontSize: 11,
              color: deltaPositive ? '#446620' : '#e55',
              fontWeight: 500,
            }}
          >
            {deltaPositive ? '▲' : '▼'} {delta}
          </div>
        )}
      </div>
    </div>
  );
}
