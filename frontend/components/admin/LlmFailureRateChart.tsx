'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, Legend,
} from 'recharts';
import { getLlmFailureRate, type LlmFailureRateRow } from '@/lib/api/admin';

const THRESHOLD = 0.05; // 5%

interface Props {
  days?: number;
  refreshSignal?: number;
}

export function LlmFailureRateChart({ days = 7, refreshSignal }: Props) {
  const [rows, setRows] = useState<LlmFailureRateRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    getLlmFailureRate(days)
      .then((r) => { setRows(r); setError(''); })
      .catch(() => setError('실패율 데이터를 불러오지 못했어요'))
      .finally(() => setLoading(false));
  }, [days, refreshSignal]);

  const data = useMemo(() => {
    return rows.map((r) => ({
      date: r.date,
      haiku: r.haikuTotal > 0 ? r.haikuFallback / r.haikuTotal : 0,
      sonnet: r.sonnetTotal > 0 ? r.sonnetFallback / r.sonnetTotal : 0,
      haikuTotal: r.haikuTotal,
    }));
  }, [rows]);

  if (loading) return <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중…</p>;
  if (error) return <p style={{ color: '#e55', fontSize: 13, padding: '12px 4px' }}>{error}</p>;
  if (data.length === 0) {
    return <p style={{ color: '#aaa', fontSize: 13, padding: '12px 4px' }}>최근 {days}일 호출 데이터 없음</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={200}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" tick={{ fontSize: 10 }} />
        <YAxis
          tick={{ fontSize: 10 }}
          tickFormatter={(v) => `${(Number(v) * 100).toFixed(0)}%`}
          domain={[0, (dataMax: number) => Math.max(0.1, dataMax * 1.2)]}
        />
        <Tooltip
          formatter={(v: number, name: string) => [`${(v * 100).toFixed(2)}%`, name]}
        />
        <Legend wrapperStyle={{ fontSize: 11 }} />
        <ReferenceLine
          y={THRESHOLD}
          stroke="#d33636"
          strokeDasharray="4 4"
          label={{ value: '임계 5%', fill: '#d33636', fontSize: 10, position: 'right' }}
        />
        <Line type="monotone" dataKey="haiku" stroke="#1A1A2E" dot={false} name="Haiku 실패율" />
        <Line type="monotone" dataKey="sonnet" stroke="#888" dot={false} name="Sonnet 실패율" />
      </LineChart>
    </ResponsiveContainer>
  );
}
