'use client';

import type { DashboardSummary } from '@/lib/api/marketing/dashboardApi';

interface Props {
  summary: DashboardSummary;
}

export function KpiGrid({ summary }: Props) {
  const cards = [
    { label: '주 발행', value: summary.weeklyPublished.toString() },
    { label: '누적 노출', value: summary.cumulativeImpressions.toLocaleString() },
    {
      label: '평균 반응률',
      value: `${(summary.averageEngagementRate * 100).toFixed(1)}%`,
    },
    {
      label: '주간 비용',
      value: `$${Number(summary.weeklyCostUsd).toFixed(2)}`,
    },
  ];

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 16,
        marginBottom: 24,
      }}
    >
      {cards.map((card) => (
        <div
          key={card.label}
          style={{
            padding: '20px',
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <p style={{ margin: '0 0 8px', fontSize: 12, color: '#888', fontWeight: 500 }}>
            {card.label}
          </p>
          <p style={{ margin: 0, fontSize: 22, fontWeight: 700, color: '#1A1A2E' }}>
            {card.value}
          </p>
        </div>
      ))}
    </div>
  );
}
