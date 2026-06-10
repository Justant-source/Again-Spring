'use client';

import { InsightsDto } from '@/lib/api/admin/stats';

interface EngagementFunnelProps {
  funnel: InsightsDto['funnel'] | null;
  realOnlyToggle: boolean;
  onToggle: (v: boolean) => void;
  loading: boolean;
}

export function EngagementFunnel({
  funnel,
  realOnlyToggle,
  onToggle,
  loading,
}: EngagementFunnelProps) {
  if (loading) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200 animate-pulse" data-testid="admin-stats-funnel">
        <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
        <div className="space-y-3">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-10 bg-gray-200 rounded" />
          ))}
        </div>
      </div>
    );
  }

  if (!funnel) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200" data-testid="admin-stats-funnel">
        <p className="text-sm text-gray-500">데이터 없음</p>
      </div>
    );
  }

  const stages = [
    { label: '활동 사용자', value: funnel.active, id: 'active' },
    { label: '투표 참여', value: funnel.voters, id: 'voters' },
    { label: '댓글 작성', value: funnel.commenters, id: 'commenters' },
    { label: '글 작성', value: funnel.posters, id: 'posters' },
  ];

  const maxValue = Math.max(...stages.map((s) => s.value));

  return (
    <div className="p-4 bg-white rounded-lg border border-gray-200" data-testid="admin-stats-funnel">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-gray-900">사용자 참여 퍼널</h3>
        <label className="flex items-center gap-2 text-xs">
          <input
            type="checkbox"
            checked={realOnlyToggle}
            onChange={(e) => onToggle(e.target.checked)}
            className="w-3 h-3 cursor-pointer"
          />
          <span className="text-gray-600">실유저만</span>
        </label>
      </div>

      <div className="space-y-3">
        {stages.map((stage, idx) => {
          const widthPercent = maxValue > 0 ? (stage.value / maxValue) * 100 : 0;
          const nextStage = stages[idx + 1];
          const conversionRate =
            nextStage && stage.value > 0
              ? ((nextStage.value / stage.value) * 100).toFixed(1)
              : null;

          return (
            <div key={stage.id} className="space-y-1">
              <div className="flex items-baseline justify-between text-xs">
                <span className="font-medium text-gray-700">{stage.label}</span>
                <span className="text-gray-600">
                  {stage.value.toLocaleString()}
                </span>
              </div>
              <div className="w-full h-6 bg-gray-100 rounded overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-[#5F8F76] to-[#7BA89E] transition-all duration-300"
                  style={{ width: `${widthPercent}%` }}
                />
              </div>
              {conversionRate && (
                <div className="text-xs text-gray-500">
                  다음 단계 전환율: {conversionRate}%
                </div>
              )}
            </div>
          );
        })}
      </div>

      {funnel.realUserOnly && (
        <div className="mt-3 p-2 bg-blue-50 rounded text-xs text-blue-700">
          실유저 데이터만 포함됨
        </div>
      )}
    </div>
  );
}
