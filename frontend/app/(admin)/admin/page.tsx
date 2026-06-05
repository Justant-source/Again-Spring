'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useUserStore } from '@/lib/store/userStore';
import { getDashboardSummary, type DashboardSummaryResponse } from '@/lib/api/admin/dashboard';
import { getDailyStats, type DailyStatsResponse } from '@/lib/api/admin/stats';
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { SystemHealthPanel } from '@/components/admin/SystemHealthPanel';
import { LlmFailureRateChart } from '@/components/admin/LlmFailureRateChart';
import { AdminStatCard } from '@/components/admin/AdminStatCard';

const QUICK_LINKS = [
  { label: '회원 관리', href: '/admin/users', icon: '👥' },
  { label: '콘텐츠 관리', href: '/admin/content', icon: '📝' },
  { label: '신고 관리', href: '/admin/reports', icon: '🚩' },
  { label: '문의 관리', href: '/admin/inquiries', icon: '💬' },
  { label: '통계', href: '/admin/stats', icon: '📊' },
  { label: '공지관리', href: '/admin/announcements', icon: '📢' },
  { label: '감사로그', href: '/admin/audit', icon: '🔍' },
  { label: '시스템', href: '/admin/system', icon: '⚙️' },
];

export default function AdminPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();
  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [dailyStats, setDailyStats] = useState<DailyStatsResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshSignal, setRefreshSignal] = useState(0);

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  useEffect(() => {
    if (!isAuthorizedAdmin) return;

    const loadData = async () => {
      try {
        setLoading(true);
        const [s, d] = await Promise.all([
          getDashboardSummary(),
          getDailyStats(7),
        ]);
        setSummary(s);
        setDailyStats(d);
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

  function handleRefresh() {
    setRefreshSignal((n) => n + 1);
  }

  if (loading) {
    return <div style={{ padding: 40, fontFamily: 'sans-serif' }}>로딩 중...</div>;
  }
  if (error) {
    return <div style={{ padding: 40, color: '#e55', fontFamily: 'sans-serif' }}>{error}</div>;
  }

  // KPI 데이터 매핑 (순서 맞춤: 2x4 그리드)
  const kpis = [
    { label: '오늘 신규 회원', value: summary?.todayNewUsers ?? 0 },
    { label: '총 회원', value: summary?.totalUsers ?? 0 },
    { label: '총 게시글', value: summary?.totalPosts ?? 0 },
    { label: '총 투표', value: summary?.totalVotes ?? 0 },
    { label: '대기 신고', value: summary?.pendingReports ?? 0, link: '/admin/reports' },
    { label: '미처리 문의', value: summary?.openInquiries ?? 0, link: '/admin/inquiries' },
  ];

  const chartData = [...dailyStats].reverse();

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      {/* 헤더 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid #e7e3d8',
          padding: '12px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>관리자 대시보드</div>
        <button
          onClick={handleRefresh}
          style={{
            background: '#1A1A2E',
            color: 'white',
            border: 'none',
            padding: '6px 12px',
            borderRadius: 6,
            fontSize: 12,
            cursor: 'pointer',
          }}
        >
          ↻ 새로고침
        </button>
      </header>

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 60px' }}>
        {/* 시스템 헬스 */}
        <SystemHealthPanel refreshSignal={refreshSignal} />

        {/* KPI 카드 (2x4 그리드) */}
        {summary && (
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
              주요 지표
            </h2>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                gap: 12,
              }}
            >
              {kpis.map((kpi, i) => (
                <div key={i} onClick={() => kpi.link && window.location.assign(kpi.link)}>
                  <AdminStatCard label={kpi.label} value={kpi.value.toLocaleString()} />
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 추세 차트 (최근 7일) */}
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
            추세 (최근 7일)
          </h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 18 }}>
            {/* 투표 + 신규 회원 차트 */}
            <div>
              <div style={{ fontSize: 12, color: '#888', marginBottom: 8, fontWeight: 600 }}>
                투표 & 신규 회원
              </div>
              {chartData.length === 0 ? (
                <p style={{ color: '#aaa', fontSize: 13 }}>데이터 없음</p>
              ) : (
                <ResponsiveContainer width="100%" height={200}>
                  <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="statDate" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Line type="monotone" dataKey="voteCount" stroke="#1A1A2E" dot={false} name="투표" />
                    <Line type="monotone" dataKey="newUsers" stroke="#888" dot={false} name="신규" />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* LLM 실패율 차트 */}
            <div>
              <div style={{ fontSize: 12, color: '#888', marginBottom: 8, fontWeight: 600 }}>
                LLM 호출 실패율 (최근 7일)
              </div>
              <LlmFailureRateChart days={7} refreshSignal={refreshSignal} />
            </div>
          </div>
        </div>

        {/* Quick Links */}
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
            빠른 이동
          </h2>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
              gap: 10,
            }}
          >
            {QUICK_LINKS.map((link) => (
              <Link key={link.href} href={link.href}>
                <div
                  style={{
                    padding: '12px 10px',
                    background: '#f7f6f2',
                    border: '1px solid #e7e3d8',
                    borderRadius: 8,
                    textAlign: 'center',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    textDecoration: 'none',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.background = '#ede8dd';
                    (e.currentTarget as HTMLElement).style.borderColor = '#d4c9b5';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.background = '#f7f6f2';
                    (e.currentTarget as HTMLElement).style.borderColor = '#e7e3d8';
                  }}
                >
                  <div style={{ fontSize: 20, marginBottom: 4 }}>{link.icon}</div>
                  <div style={{ fontSize: 12, fontWeight: 500, color: '#333' }}>{link.label}</div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
