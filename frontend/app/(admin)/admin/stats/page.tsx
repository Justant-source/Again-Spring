'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import {
  getDailyStats,
  getRetentionCohort,
  backfillStats,
  getCommunityInsights,
  getTrafficSummary,
  type DailyStatsResponse,
  type InsightsDto,
  type TrafficDto,
} from '@/lib/api/admin/stats';
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { AdminStatCard } from '@/components/admin/AdminStatCard';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { RefreshControl } from '@/components/admin/RefreshControl';
import { EngagementFunnel } from '@/components/admin/stats/EngagementFunnel';
import { ProductionRatioChart } from '@/components/admin/stats/ProductionRatioChart';
import { ContentHealthCards } from '@/components/admin/stats/ContentHealthCards';
import { TrafficPanel } from '@/components/admin/stats/TrafficPanel';

export default function StatsPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();

  // Existing stats
  const [stats, setStats] = useState<DailyStatsResponse[]>([]);
  const [retention, setRetention] = useState<any[]>([]);
  const [dateRange, setDateRange] = useState<{ from: string; to: string }>(() => {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 30);
    return {
      from: from.toISOString().split('T')[0],
      to: to.toISOString().split('T')[0],
    };
  });
  const [backfillFrom, setBackfillFrom] = useState('');
  const [backfillTo, setBackfillTo] = useState('');
  const [loading, setLoading] = useState(true);
  const [backfilling, setBackfilling] = useState(false);
  const [error, setError] = useState('');

  // New insights & traffic
  const [days, setDays] = useState(30);
  const [realOnly, setRealOnly] = useState(true);
  const [insights, setInsights] = useState<InsightsDto | null>(null);
  const [traffic, setTraffic] = useState<TrafficDto | null>(null);
  const [insightsLoading, setInsightsLoading] = useState(false);

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  // Load insights and traffic on mount and when days/realOnly change
  useEffect(() => {
    if (!isAuthorizedAdmin) return;

    const loadInsights = async () => {
      try {
        setInsightsLoading(true);
        const [i, t] = await Promise.all([
          getCommunityInsights(days, realOnly),
          getTrafficSummary(days),
        ]);
        setInsights(i);
        setTraffic(t);
      } catch (e: any) {
        if (e.response?.status === 403) {
          router.replace('/');
        }
        // Silently fail on insights/traffic; they're optional enhancements
      } finally {
        setInsightsLoading(false);
      }
    };

    loadInsights();
  }, [isAuthorizedAdmin, days, realOnly, router]);

  // Load legacy stats and retention on mount
  useEffect(() => {
    if (!isAuthorizedAdmin) return;

    const loadData = async () => {
      try {
        setLoading(true);
        const [s, r] = await Promise.all([getDailyStats(30), getRetentionCohort()]);
        setStats(s);
        setRetention(r);
        setError('');
      } catch (e: any) {
        if (e.response?.status === 403) router.replace('/');
        else setError('데이터를 불러오지 못했어요.');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [isAuthorizedAdmin, router]);

  async function handleBackfill() {
    if (!backfillFrom || !backfillTo) {
      alert('날짜를 모두 입력해주세요.');
      return;
    }
    setBackfilling(true);
    try {
      await backfillStats(backfillFrom, backfillTo);
      alert('통계 역산이 완료되었습니다.');
      setBackfillFrom('');
      setBackfillTo('');
      // 데이터 새로고침
      const [s, r] = await Promise.all([getDailyStats(30), getRetentionCohort()]);
      setStats(s);
      setRetention(r);
    } catch {
      alert('역산 중 오류가 발생했습니다.');
    } finally {
      setBackfilling(false);
    }
  }

  async function handleRefresh() {
    try {
      setInsightsLoading(true);
      const [i, t] = await Promise.all([
        getCommunityInsights(days, realOnly),
        getTrafficSummary(days),
      ]);
      setInsights(i);
      setTraffic(t);
    } catch {
      // Silently fail
    } finally {
      setInsightsLoading(false);
    }
  }

  if (loading) {
    return <div style={{ padding: 40, fontFamily: 'sans-serif' }}>불러오는 중…</div>;
  }
  if (error) {
    return <div style={{ padding: 40, color: '#e55', fontFamily: 'sans-serif' }}>{error}</div>;
  }

  const chartData = [...stats].reverse();

  return (
    <div className="space-y-6">
      {/* 페이지 헤더 */}
      <AdminPageHeader title="통계" />

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '0 16px 60px' }}>
        {/* 커뮤니티 인사이트 섹션 */}
        <div className="mb-6 space-y-4">
          {/* 기간 선택 및 제목 */}
          <div className="flex items-center justify-between">
            <h1 className="text-lg font-semibold text-gray-900">커뮤니티 인사이트</h1>
            <RefreshControl
              onRefresh={handleRefresh}
              loading={insightsLoading}
              autoRefreshSeconds={60}
              data-testid="admin-stats-refresh"
            />
          </div>

          {/* 기간 선택 버튼 */}
          <div className="flex gap-2" data-testid="admin-stats-period-select">
            {[7, 14, 30, 90].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-4 py-2 text-sm font-medium rounded transition-colors ${
                  days === d
                    ? 'bg-gray-900 text-white'
                    : 'bg-white text-gray-700 border border-gray-200 hover:bg-gray-50'
                }`}
              >
                {d}일
              </button>
            ))}
          </div>
        </div>

        {/* 핵심 지표 카드 */}
        <div
          className="grid grid-cols-4 gap-4 mb-6"
          data-testid="admin-stats-insights"
        >
          <AdminStatCard
            label="일 평균 활성 사용자"
            value={insightsLoading ? '-' : insights?.dau ?? 0}
          />
          <AdminStatCard
            label="주 평균 활성 사용자"
            value={insightsLoading ? '-' : insights?.wau ?? 0}
          />
          <AdminStatCard
            label="월 활성 사용자"
            value={insightsLoading ? '-' : insights?.mau ?? 0}
          />
          <AdminStatCard
            label="Stickiness"
            value={
              insightsLoading || !insights?.stickiness
                ? '-'
                : `${(insights.stickiness * 100).toFixed(1)}%`
            }
          />
        </div>

        {/* 퍼널 + 콘텐츠 건강도 */}
        <div className="grid grid-cols-2 gap-4 mb-6">
          <EngagementFunnel
            funnel={insights?.funnel ?? null}
            realOnlyToggle={realOnly}
            onToggle={setRealOnly}
            loading={insightsLoading}
          />
          <ContentHealthCards
            health={insights?.contentHealth ?? null}
            loading={insightsLoading}
          />
        </div>

        {/* 자생도 차트 */}
        <div className="mb-6">
          <ProductionRatioChart
            series={insights?.productionSeries ?? []}
            loading={insightsLoading}
          />
        </div>

        {/* 트래픽 패널 */}
        <div className="mb-6">
          <TrafficPanel traffic={traffic} loading={insightsLoading} />
        </div>

        {/* 기존 섹션들 (하단) */}
        {/* 날짜 범위 선택 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            날짜 범위
          </h2>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <input
              type="date"
              value={dateRange.from}
              onChange={(e) => setDateRange((prev) => ({ ...prev, from: e.target.value }))}
              style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6 }}
            />
            <span style={{ display: 'flex', alignItems: 'center' }}>~</span>
            <input
              type="date"
              value={dateRange.to}
              onChange={(e) => setDateRange((prev) => ({ ...prev, to: e.target.value }))}
              style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6 }}
            />
          </div>
        </div>

        {/* 차트 섹션 */}
        <div style={{ marginBottom: 22 }}>
          <div style={{ marginBottom: 18 }}>
            <div
              style={{
                padding: 16,
                background: 'white',
                borderRadius: 12,
                border: '1px solid #e7e3d8',
              }}
            >
              <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
                일일 지표 추이
              </h2>
              {chartData.length === 0 ? (
                <p style={{ color: '#aaa', fontSize: 13 }}>데이터 없음</p>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="statDate" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Line type="monotone" dataKey="voteCount" stroke="#1A1A2E" dot={false} name="투표" />
                    <Line type="monotone" dataKey="newUsers" stroke="#5B8F76" dot={false} name="신규" />
                    <Line type="monotone" dataKey="postCount" stroke="#C9785A" dot={false} name="게시글" />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          <div>
            <div
              style={{
                padding: 16,
                background: 'white',
                borderRadius: 12,
                border: '1px solid #e7e3d8',
              }}
            >
              <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
                의견함 통계
              </h2>
              {chartData.length === 0 ? (
                <p style={{ color: '#aaa', fontSize: 13 }}>데이터 없음</p>
              ) : (
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="statDate" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Bar dataKey="feedbackCount" fill="#D4A574" name="의견함" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>
        </div>

        {/* 리텐션 코호트 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            리텐션 코호트 (최근 14일)
          </h2>
          {retention.length === 0 ? (
            <p style={{ color: '#aaa', fontSize: 13 }}>데이터 없음</p>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#f5f5f5' }}>
                    {['날짜', 'DAU', '신규', '이탈'].map((h) => (
                      <th
                        key={h}
                        style={{
                          padding: '8px 10px',
                          textAlign: 'left',
                          fontWeight: 600,
                          fontSize: 12,
                        }}
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {retention.map((row: any, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid #eee' }}>
                      <td style={{ padding: '8px 10px' }}>{row.date}</td>
                      <td style={{ padding: '8px 10px' }}>{row.dau}</td>
                      <td style={{ padding: '8px 10px' }}>{row.newUsers}</td>
                      <td style={{ padding: '8px 10px' }}>-</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* 역산 채움 섹션 */}
        <div
          style={{
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            통계 역산 채움 (Admin)
          </h2>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <div>
              <label style={{ display: 'block', fontSize: 12, marginBottom: 4, color: '#666' }}>
                시작 날짜
              </label>
              <input
                type="date"
                value={backfillFrom}
                onChange={(e) => setBackfillFrom(e.target.value)}
                style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6 }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 12, marginBottom: 4, color: '#666' }}>
                끝 날짜
              </label>
              <input
                type="date"
                value={backfillTo}
                onChange={(e) => setBackfillTo(e.target.value)}
                style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6 }}
              />
            </div>
            <button
              onClick={handleBackfill}
              disabled={backfilling}
              style={{
                padding: '8px 16px',
                background: '#1A1A2E',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: backfilling ? 'wait' : 'pointer',
                fontSize: 12,
              }}
            >
              {backfilling ? '진행 중...' : '역산 시작'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
