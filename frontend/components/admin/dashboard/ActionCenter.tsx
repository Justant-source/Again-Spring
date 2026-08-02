'use client';

import { useRouter } from 'next/navigation';
import { Card } from '@/components/ui/card';
import { ActionCenterResponse } from '@/lib/api/admin/dashboard';

interface ActionCenterProps {
  data: ActionCenterResponse | null;
  loading?: boolean;
}

const ACTION_ITEMS = [
  {
    key: 'pendingReports',
    label: '신고 대기',
    color: 'bg-red-50 border-red-200',
    href: '/admin/reports?filter=PENDING',
  },
  {
    key: 'openInquiries',
    label: '미처리 문의',
    color: 'bg-orange-50 border-orange-200',
    href: '/admin/inquiries',
  },
  {
    key: 'marketingAwaitingApproval',
    label: '마케팅 검수 대기',
    color: 'bg-amber-50 border-amber-200',
    href: '/admin/marketing?tab=jobs&filter=READY',
  },
  {
    key: 'marketingFailed',
    label: '마케팅 실패',
    color: 'bg-red-50 border-red-200',
    href: '/admin/marketing?tab=jobs&filter=FAILED',
  },
  {
    key: 'aiFailuresToday',
    label: 'AI 생성 실패 (오늘)',
    color: 'bg-purple-50 border-purple-200',
    href: '/admin/ai-user',
  },
  {
    key: 'aiBlockedToday',
    label: 'AI 생성 차단 (오늘)',
    color: 'bg-purple-50 border-purple-200',
    href: '/admin/ai-user',
  },
  {
    key: 'crisisRecent24h',
    label: '위기 신고 (24h)',
    color: 'bg-red-50 border-red-200',
    href: '/admin/crisis',
  },
];

export function ActionCenter({ data, loading }: ActionCenterProps) {
  const router = useRouter();

  if (loading || !data) {
    return (
      <div className="p-6 bg-white rounded-lg border animate-pulse">
        <div className="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="h-16 bg-gray-200 rounded"></div>
          ))}
        </div>
      </div>
    );
  }

  const allZero = ACTION_ITEMS.every((item) => (data as any)[item.key] === 0);

  if (allZero) {
    return (
      <div
        className="p-6 bg-green-50 rounded-lg border border-green-200 text-center"
        data-testid="admin-action-center"
      >
        <p className="text-sm font-medium text-green-700">모두 처리됨 ✓</p>
      </div>
    );
  }

  return (
    <div className="p-6 bg-white rounded-lg border" data-testid="admin-action-center">
      <h2 className="text-sm font-semibold text-gray-900 mb-4">처리 대기</h2>
      <div className="flex flex-wrap gap-3">
        {ACTION_ITEMS.map((item) => {
          const count = (data as any)[item.key] ?? 0;
          const isUrgent = count > 0 && ['pendingReports', 'openInquiries', 'aiFailuresToday', 'aiBlockedToday'].includes(item.key);

          return (
            <button
              key={item.key}
              onClick={() => router.push(item.href)}
              className={`flex flex-col items-center justify-center px-4 py-3 rounded-lg border text-sm font-medium transition-colors ${
                count === 0
                  ? 'bg-gray-50 border-gray-200 text-gray-400 cursor-default'
                  : isUrgent
                    ? 'bg-red-50 border-red-200 text-red-700 hover:bg-red-100'
                    : 'bg-amber-50 border-amber-200 text-amber-700 hover:bg-amber-100'
              }`}
              disabled={count === 0}
            >
              <div className="text-lg font-semibold">{count}</div>
              <div className="text-xs text-center">{item.label}</div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
