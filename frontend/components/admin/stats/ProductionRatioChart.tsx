'use client';

import { InsightsDto } from '@/lib/api/admin/stats';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

interface ProductionRatioChartProps {
  series: InsightsDto['productionSeries'];
  loading: boolean;
}

export function ProductionRatioChart({ series, loading }: ProductionRatioChartProps) {
  if (loading) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200 animate-pulse h-64" data-testid="admin-stats-production-ratio">
        <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
        <div className="h-48 bg-gray-200 rounded" />
      </div>
    );
  }

  if (!series || series.length === 0) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200" data-testid="admin-stats-production-ratio">
        <p className="text-sm text-gray-500">데이터 없음</p>
      </div>
    );
  }

  // Convert to percentage-based stacked area data
  const chartData = series.map((item) => {
    const totalPosts = item.realPosts + item.aiPosts;
    const totalComments = item.realComments + item.aiComments;

    return {
      date: item.date,
      realPostsRatio: totalPosts > 0 ? (item.realPosts / totalPosts) * 100 : 0,
      aiPostsRatio: totalPosts > 0 ? (item.aiPosts / totalPosts) * 100 : 0,
      realCommentsRatio: totalComments > 0 ? (item.realComments / totalComments) * 100 : 0,
      aiCommentsRatio: totalComments > 0 ? (item.aiComments / totalComments) * 100 : 0,
    };
  });

  return (
    <div className="p-4 bg-white rounded-lg border border-gray-200" data-testid="admin-stats-production-ratio">
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-gray-900">커뮤니티 자생도</h3>
        <p className="text-xs text-gray-500 mt-1">(실유저 콘텐츠 비중)</p>
      </div>

      <ResponsiveContainer width="100%" height={250}>
        <AreaChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 20 }}>
          <defs>
            <linearGradient id="realPostsGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#5F8F76" stopOpacity={0.8} />
              <stop offset="95%" stopColor="#5F8F76" stopOpacity={0.1} />
            </linearGradient>
            <linearGradient id="aiPostsGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#A8C4B8" stopOpacity={0.6} />
              <stop offset="95%" stopColor="#A8C4B8" stopOpacity={0.05} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 10 }}
            stroke="#9ca3af"
          />
          <YAxis
            label={{ value: '비중 (%)', angle: -90, position: 'insideLeft' }}
            tick={{ fontSize: 10 }}
            stroke="#9ca3af"
            domain={[0, 100]}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: 'white',
              border: '1px solid #e5e7eb',
              borderRadius: 4,
            }}
            formatter={(value) => `${(value as number).toFixed(1)}%`}
          />
          <Area
            type="monotone"
            dataKey="realPostsRatio"
            stackId="1"
            stroke="#5F8F76"
            fill="url(#realPostsGradient)"
            name="실유저 게시글"
          />
          <Area
            type="monotone"
            dataKey="aiPostsRatio"
            stackId="1"
            stroke="#A8C4B8"
            fill="url(#aiPostsGradient)"
            name="AI 게시글"
          />
        </AreaChart>
      </ResponsiveContainer>

      {/* Legend */}
      <div className="flex gap-6 mt-4 text-xs">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-[#5F8F76]" />
          <span className="text-gray-700">실유저 콘텐츠</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-[#A8C4B8]" />
          <span className="text-gray-700">AI 콘텐츠</span>
        </div>
      </div>
    </div>
  );
}
