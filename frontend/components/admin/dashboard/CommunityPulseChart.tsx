'use client';

import { useState } from 'react';
import { PulseSlot } from '@/lib/api/admin/dashboard';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';

interface CommunityPulseChartProps {
  data: PulseSlot[];
  loading?: boolean;
}

type PulseMetric = 'comments' | 'posts' | 'votes';

export function CommunityPulseChart({ data, loading }: CommunityPulseChartProps) {
  const [metric, setMetric] = useState<PulseMetric>('comments');
  const [showAi, setShowAi] = useState(true);
  const [showReal, setShowReal] = useState(true);

  if (loading || data.length === 0) {
    return (
      <div className="p-6 bg-white rounded-lg border">
        <div className="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
        <div className="h-64 bg-gray-200 rounded animate-pulse"></div>
      </div>
    );
  }

  const chartData = data.map((slot) => {
    if (metric === 'posts') {
      return {
        hour: slot.hour,
        실유저: showReal ? slot.postsReal : 0,
        AI: showAi ? slot.postsAi : 0,
      };
    } else if (metric === 'votes') {
      return {
        hour: slot.hour,
        실유저: showReal ? slot.votesReal : 0,
        AI: showAi ? slot.votesAi : 0,
      };
    } else {
      return {
        hour: slot.hour,
        실유저: showReal ? slot.commentsReal : 0,
        AI: showAi ? slot.commentsAi : 0,
      };
    }
  });

  const metricLabel =
    metric === 'posts' ? '게시글' : metric === 'votes' ? '투표' : '댓글';

  return (
    <div className="p-6 bg-white rounded-lg border" data-testid="admin-pulse-chart">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-gray-900">커뮤니티 펄스 ({metricLabel})</h2>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <select
              value={metric}
              onChange={(e) => setMetric(e.target.value as PulseMetric)}
              className="text-xs px-2 py-1 border rounded bg-white"
            >
              <option value="posts">게시글</option>
              <option value="comments">댓글</option>
              <option value="votes">투표</option>
            </select>
          </div>
          <div className="flex items-center gap-1">
            <label className="text-xs cursor-pointer flex items-center gap-1">
              <input
                type="checkbox"
                checked={showReal}
                onChange={(e) => setShowReal(e.target.checked)}
                className="w-3 h-3"
              />
              실유저
            </label>
            <label className="text-xs cursor-pointer flex items-center gap-1">
              <input
                type="checkbox"
                checked={showAi}
                onChange={(e) => setShowAi(e.target.checked)}
                className="w-3 h-3"
              />
              AI
            </label>
          </div>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={250}>
        <BarChart
          data={chartData}
          margin={{ top: 10, right: 20, left: 0, bottom: 10 }}
        >
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="hour"
            tick={{ fontSize: 10 }}
            tickFormatter={(h) => `${h}:00`}
            interval={2}
          />
          <YAxis tick={{ fontSize: 10 }} />
          <Tooltip
            formatter={(value: any) => value.toLocaleString()}
            labelFormatter={(label) => `${label}시`}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {showReal && (
            <Bar dataKey="실유저" stackId="a" fill="#5F8F76" />
          )}
          {showAi && (
            <Bar dataKey="AI" stackId="a" fill="#A8C4B8" />
          )}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
