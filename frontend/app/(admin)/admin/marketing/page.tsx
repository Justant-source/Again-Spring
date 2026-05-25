'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { AdminStatCard } from '@/components/admin/AdminStatCard';
import { getDailyStats, getMonthlyStats, DailyStats, MonthlyStats } from '@/lib/api/marketing/costApi';

const MONTHLY_BUDGET_USD = 20;

export default function MarketingPage() {
  const [dailyStats, setDailyStats] = useState<DailyStats | null>(null);
  const [monthlyStats, setMonthlyStats] = useState<MonthlyStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadStats() {
      try {
        setLoading(true);
        const [daily, monthly] = await Promise.all([getDailyStats(), getMonthlyStats()]);
        setDailyStats(daily);
        setMonthlyStats(monthly);
        setError(null);
      } catch (err) {
        setError('통계를 불러올 수 없습니다.');
        console.error('Failed to load stats:', err);
      } finally {
        setLoading(false);
      }
    }

    loadStats();
  }, []);

  const budgetPercentage = monthlyStats
    ? Math.min((monthlyStats.costUsd / MONTHLY_BUDGET_USD) * 100, 100)
    : 0;
  const budgetExceeded = budgetPercentage > 100;
  const budgetWarning = budgetPercentage > 80 && budgetPercentage <= 100;

  return (
    <div>
      {/* 헤더 */}
      <div
        style={{
          marginBottom: 24,
          padding: '20px',
          background: 'white',
          borderRadius: 12,
          border: '1px solid #e7e3d8',
        }}
      >
        <h1 style={{ fontSize: 18, fontWeight: 600, color: '#1A1A2E', margin: '0 0 8px' }}>
          마케팅 자동화 대시보드
        </h1>
        <p style={{ fontSize: 14, color: '#888', margin: 0 }}>
          시뮬레이션 현황 및 비용 관리
        </p>
      </div>

      {/* 통계 카드 */}
      {!loading && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 16,
            marginBottom: 24,
          }}
        >
          <AdminStatCard
            label="오늘 시뮬레이션 수"
            value={dailyStats?.count ?? 0}
          />
          <AdminStatCard
            label="이달 비용"
            value={`$${monthlyStats?.costUsd.toFixed(2) ?? '0.00'} USD`}
          />
        </div>
      )}

      {/* 월 예산 진행률 */}
      {!loading && monthlyStats && (
        <div
          style={{
            padding: '20px',
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
            marginBottom: 24,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h3 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>
              월 예산 사용률
            </h3>
            <span
              style={{
                fontSize: 12,
                fontWeight: 500,
                color: budgetExceeded ? '#e55' : budgetWarning ? '#cc8800' : '#446620',
              }}
            >
              ${monthlyStats.costUsd.toFixed(2)} / ${MONTHLY_BUDGET_USD}
            </span>
          </div>
          <div
            style={{
              width: '100%',
              height: 8,
              background: '#e7e3d8',
              borderRadius: 4,
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                height: '100%',
                width: `${budgetPercentage}%`,
                background: budgetExceeded ? '#e55' : budgetWarning ? '#cc8800' : '#446620',
                transition: 'width 0.3s ease',
              }}
            />
          </div>
          {budgetExceeded && (
            <p style={{ fontSize: 12, color: '#e55', marginTop: 8, margin: '8px 0 0' }}>
              월 예산({MONTHLY_BUDGET_USD}원)을 초과했습니다.
            </p>
          )}
          {budgetWarning && !budgetExceeded && (
            <p style={{ fontSize: 12, color: '#cc8800', marginTop: 8, margin: '8px 0 0' }}>
              월 예산의 80% 이상 사용했습니다.
            </p>
          )}
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '16px',
            background: '#ffe6e6',
            border: '1px solid #e55',
            borderRadius: 8,
            marginBottom: 24,
            color: '#e55',
            fontSize: 14,
          }}
        >
          {error}
        </div>
      )}

      {/* 빠른 접근 */}
      <div
        style={{
          padding: '20px',
          background: 'white',
          borderRadius: 12,
          border: '1px solid #e7e3d8',
        }}
      >
        <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 16px' }}>
          빠른 접근
        </h2>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Link
            href="/admin/marketing/stories"
            style={{
              display: 'inline-block',
              padding: '12px 20px',
              background: '#1A1A2E',
              color: 'white',
              borderRadius: 6,
              textDecoration: 'none',
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              border: 'none',
              transition: 'opacity 0.15s',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.88')}
            onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
          >
            사연 관리
          </Link>
          <Link
            href="/admin/marketing/simulations"
            style={{
              display: 'inline-block',
              padding: '12px 20px',
              background: '#1A1A2E',
              color: 'white',
              borderRadius: 6,
              textDecoration: 'none',
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              border: 'none',
              transition: 'opacity 0.15s',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.88')}
            onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
          >
            시뮬레이션
          </Link>
          <Link
            href="/admin/marketing/contents"
            style={{
              display: 'inline-block',
              padding: '12px 20px',
              background: '#1A1A2E',
              color: 'white',
              borderRadius: 6,
              textDecoration: 'none',
              fontSize: 14,
              fontWeight: 500,
              cursor: 'pointer',
              border: 'none',
              transition: 'opacity 0.15s',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.88')}
            onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
          >
            콘텐츠
          </Link>
        </div>
      </div>
    </div>
  );
}
