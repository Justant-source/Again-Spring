'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  ComposedChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, Cell,
} from 'recharts';
import {
  getHourlyDistribution,
  type HourlyDistributionResponse,
} from '@/lib/api/admin/ai-user';

interface ChartDataPoint {
  hour: number;
  actual: number;
  [key: string]: number | string;
}

const ACTION_COLORS: Record<string, string> = {
  POST: '#5F8F76',
  COMMENT: '#4CAF50',
  REPLY: '#2196F3',
  VOTE: '#FF9800',
  LIKE: '#F44336',
};

export function HourlyDistributionChart({ className }: { className?: string }) {
  const [data, setData] = useState<ChartDataPoint[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchDistribution = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getHourlyDistribution(24);
      const chartData: ChartDataPoint[] = result.hours.map(slot => {
        const point: ChartDataPoint = {
          hour: slot.hour,
          actual: slot.actual,
        };
        // Add action types as separate fields
        Object.entries(slot.byType).forEach(([action, count]) => {
          point[action] = count;
        });
        return point;
      });
      setData(chartData);
    } catch (e) {
      console.error('Failed to fetch hourly distribution:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDistribution();
  }, [fetchDistribution]);

  const actionTypes = Array.from(
    new Set(
      data.flatMap(point =>
        Object.keys(point).filter(
          k => k !== 'hour' && k !== 'actual' && typeof point[k] === 'number'
        )
      )
    )
  ).sort() as string[];

  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-6 ${className || ''}`}>
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-800">시간대별 생성 분포</h3>
        {loading && <span className="text-xs text-gray-400">업데이트 중...</span>}
      </div>

      {data.length === 0 ? (
        <div className="h-64 flex items-center justify-center text-gray-400">
          아직 생성 기록이 없어요
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={300}>
          <ComposedChart
            data={data}
            margin={{ top: 20, right: 30, left: 0, bottom: 60 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="hour"
              label={{ value: '시간 (KST)', position: 'bottom', offset: 10 }}
              interval={5}
              tickFormatter={h => `${h}:00`}
            />
            <YAxis label={{ value: '행동 수', angle: -90, position: 'insideLeft' }} />
            <Tooltip
              contentStyle={{
                backgroundColor: '#fff',
                border: '1px solid #e5e7eb',
                borderRadius: '8px',
              }}
              formatter={(value: any) => value.toLocaleString()}
              labelFormatter={label => `${label}:00`}
            />
            <Legend />

            {/* 메인 bar — 실제 행동 수 */}
            <Bar dataKey="actual" fill="#5F8F76" name="실제 행동" />

            {/* 행동 타입별 stacked bars */}
            {actionTypes.map(action => (
              <Bar
                key={action}
                dataKey={action}
                stackId="actions"
                fill={ACTION_COLORS[action] || '#999'}
                name={action}
              />
            ))}
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
