'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import {
  getActionCenter,
  getKpiMetrics,
  getCommunityPulse,
  getHotPosts,
  type ActionCenterResponse,
  type KpiMetricDto,
  type PulseSlot,
  type HotPostDto,
} from '@/lib/api/admin/dashboard';
import { getDailyStats, type DailyStatsResponse } from '@/lib/api/admin/stats';
import { RefreshControl } from '@/components/admin/RefreshControl';
import { ActionCenter } from '@/components/admin/dashboard/ActionCenter';
import { KpiGrid } from '@/components/admin/dashboard/KpiGrid';
import { CommunityPulseChart } from '@/components/admin/dashboard/CommunityPulseChart';
import { HotPostsCard } from '@/components/admin/dashboard/HotPostsCard';
import { SystemHealthPanel } from '@/components/admin/SystemHealthPanel';
import { LlmFailureRateChart } from '@/components/admin/LlmFailureRateChart';

export default function AdminPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();

  const [actionCenter, setActionCenter] = useState<ActionCenterResponse | null>(null);
  const [kpiMetrics, setKpiMetrics] = useState<KpiMetricDto[]>([]);
  const [pulseData, setPulseData] = useState<PulseSlot[]>([]);
  const [hotPosts, setHotPosts] = useState<HotPostDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  useEffect(() => {
    if (!isAuthorizedAdmin) return;

    const loadData = async () => {
      try {
        setLoading(true);
        const [ac, kpi, pulse, hot] = await Promise.all([
          getActionCenter().catch(() => null),
          getKpiMetrics(7).catch(() => []),
          getCommunityPulse(24).catch(() => ({ data: [] })),
          getHotPosts(48, 10).catch(() => []),
        ]);

        setActionCenter(ac);
        setKpiMetrics(kpi);
        setPulseData(pulse?.data || []);
        setHotPosts(hot);
        setError('');
      } catch (e: any) {
        if (e.response?.status === 403) router.replace('/');
        else setError('데이터를 불러오지 못했어요.');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [isAuthorizedAdmin, router, refreshTrigger]);

  function handleRefresh() {
    setRefreshTrigger((n) => n + 1);
  }

  if (error) {
    return (
      <div className="p-8 text-red-600 font-medium">
        {error}
      </div>
    );
  }

  const isDataLoaded = !loading && (actionCenter || kpiMetrics.length > 0);

  return (
    <div className="space-y-6">
      {/* Header with refresh control */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">관리자 대시보드</h1>
        <RefreshControl
          onRefresh={handleRefresh}
          loading={loading}
          autoRefreshSeconds={60}
          data-testid="admin-page-refresh"
        />
      </div>

      {/* Action Center */}
      <ActionCenter data={actionCenter} loading={loading} />

      {/* KPI Grid */}
      <KpiGrid metrics={kpiMetrics} loading={loading} />

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Community Pulse (2/3) */}
        <div className="lg:col-span-2">
          <CommunityPulseChart data={pulseData} loading={loading} />
        </div>

        {/* Hot Posts (1/3) */}
        <div className="lg:col-span-1">
          <HotPostsCard posts={hotPosts} loading={loading} />
        </div>
      </div>

      {/* System Health & LLM Failure */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <SystemHealthPanel refreshSignal={refreshTrigger} />
        <div className="p-6 bg-white rounded-lg border">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">LLM 호출 실패율 (최근 7일)</h2>
          <LlmFailureRateChart days={7} refreshSignal={refreshTrigger} />
        </div>
      </div>
    </div>
  );
}
