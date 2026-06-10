'use client';

import { InsightsDto } from '@/lib/api/admin/stats';
import { AdminStatCard } from '@/components/admin/AdminStatCard';

interface ContentHealthCardsProps {
  health: InsightsDto['contentHealth'] | null;
  loading: boolean;
}

export function ContentHealthCards({ health, loading }: ContentHealthCardsProps) {
  if (loading) {
    return (
      <div className="space-y-3">
        <div className="p-4 bg-white rounded-lg border border-gray-200 animate-pulse h-24" />
        <div className="p-4 bg-white rounded-lg border border-gray-200 animate-pulse h-24" />
      </div>
    );
  }

  if (!health) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200">
        <p className="text-sm text-gray-500">데이터 없음</p>
      </div>
    );
  }

  const avgCommentsFormatted = health.avgCommentsPerPost.toFixed(1);
  const noCommentRateFormatted = health.noComments24hRate.toFixed(1);
  const noCommentRateHigh = health.noComments24hRate > 50;

  return (
    <div className="space-y-3">
      <AdminStatCard
        label="게시글당 평균 댓글 수"
        value={avgCommentsFormatted}
        delta={health.avgCommentsPerPost > 2 ? '양호' : '개선 필요'}
        deltaPositive={health.avgCommentsPerPost > 2}
      />
      <AdminStatCard
        label="24시간 무댓글 글 비율"
        value={`${noCommentRateFormatted}%`}
        delta={noCommentRateHigh ? '주의' : '양호'}
        deltaPositive={!noCommentRateHigh}
      />
    </div>
  );
}
