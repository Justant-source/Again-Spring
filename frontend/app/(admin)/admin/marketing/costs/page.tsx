'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { AdminStatCard } from '@/components/admin/AdminStatCard';
import { getDailyStats, getMonthlyStats, DailyStats, MonthlyStats } from '@/lib/api/marketing/costApi';

const MONTHLY_BUDGET_USD = 20;

export default function CostsPage() {
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
        setError('비용 통계를 불러올 수 없습니다.');
        console.error('Failed to load cost stats:', err);
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
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div>
          <h1 style={{ fontSize: 18, fontWeight: 600, color: '#1A1A2E', margin: '0 0 8px' }}>
            비용 현황
          </h1>
          <p style={{ fontSize: 14, color: '#888', margin: 0 }}>
            시뮬레이션 비용 및 예산 추적
          </p>
        </div>
        <Link
          href="/admin/marketing"
          style={{
            padding: '12px 20px',
            background: '#f5f5f0',
            border: '1px solid #e7e3d8',
            borderRadius: 6,
            textDecoration: 'none',
            fontSize: 14,
            fontWeight: 500,
            color: '#1A1A2E',
            cursor: 'pointer',
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.88')}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
        >
          뒤로 가기
        </Link>
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

      {/* 예산 사용률 */}
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
              height: 12,
              background: '#e7e3d8',
              borderRadius: 6,
              overflow: 'hidden',
              marginBottom: 12,
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
          <div style={{ fontSize: 12, color: '#888' }}>
            사용률: {budgetPercentage.toFixed(1)}%
          </div>
          {budgetExceeded && (
            <div
              style={{
                marginTop: 12,
                padding: '12px',
                background: '#ffe6e6',
                border: '1px solid #e55',
                borderRadius: 6,
                color: '#e55',
                fontSize: 12,
              }}
            >
              월 예산({MONTHLY_BUDGET_USD}USD)을 초과했습니다. 신규 시뮬레이션이 제한될 수 있습니다.
            </div>
          )}
          {budgetWarning && !budgetExceeded && (
            <div
              style={{
                marginTop: 12,
                padding: '12px',
                background: '#fff8e6',
                border: '1px solid #cc8800',
                borderRadius: 6,
                color: '#cc8800',
                fontSize: 12,
              }}
            >
              월 예산의 80% 이상 사용했습니다.
            </div>
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

      {loading && (
        <div style={{ textAlign: 'center', padding: '40px 20px', color: '#888' }}>
          비용 데이터를 불러오는 중입니다...
        </div>
      )}
    </div>
  );
}
