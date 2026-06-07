'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getDashboardSummary, type DashboardSummary } from '@/lib/api/marketing/dashboardApi';
import { KpiGrid } from '@/components/admin/marketing/KpiGrid';
import { PlatformPerformanceTable } from '@/components/admin/marketing/PlatformPerformanceTable';
import { UpcomingPublishList } from '@/components/admin/marketing/UpcomingPublishList';

const MONTHLY_BUDGET_USD = 20;

export default function MarketingPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const data = await getDashboardSummary();
        setSummary(data);
        setError(null);
      } catch (err) {
        setError('통계를 불러올 수 없습니다.');
        console.error('Failed to load dashboard:', err);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const budgetPct = summary
    ? Math.min((Number(summary.weeklyCostUsd) / MONTHLY_BUDGET_USD) * 100, 100)
    : 0;
  const budgetExceeded = budgetPct >= 100;
  const budgetWarning = budgetPct >= 80 && budgetPct < 100;

  return (
    <div>
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
        <p style={{ fontSize: 14, color: '#888', margin: 0 }}>커뮤니티 사연 홍보 콘텐츠 생성 및 비용 관리</p>
      </div>

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

      {!loading && summary && (
        <>
          <KpiGrid summary={summary} />

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: 20,
              marginBottom: 24,
            }}
          >
            <div
              style={{
                padding: '20px',
                background: 'white',
                borderRadius: 12,
                border: '1px solid #e7e3d8',
              }}
            >
              <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 16px' }}>
                플랫폼별 성과
              </h2>
              <PlatformPerformanceTable stats={summary.platformStats} />
            </div>

            <div
              style={{
                padding: '20px',
                background: 'white',
                borderRadius: 12,
                border: '1px solid #e7e3d8',
              }}
            >
              <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 16px' }}>
                24시간 내 예약 발행
              </h2>
              <UpcomingPublishList items={summary.upcomingPublishes} />
            </div>
          </div>

          <div
            style={{
              padding: '20px',
              background: 'white',
              borderRadius: 12,
              border: '1px solid #e7e3d8',
              marginBottom: 24,
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 12,
              }}
            >
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
                ${Number(summary.weeklyCostUsd).toFixed(2)} / ${MONTHLY_BUDGET_USD}
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
                  width: `${budgetPct}%`,
                  background: budgetExceeded ? '#e55' : budgetWarning ? '#cc8800' : '#446620',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>
            {budgetExceeded && (
              <p style={{ fontSize: 12, color: '#e55', margin: '8px 0 0' }}>
                월 예산({MONTHLY_BUDGET_USD} USD)을 초과했습니다.
              </p>
            )}
            {budgetWarning && (
              <p style={{ fontSize: 12, color: '#cc8800', margin: '8px 0 0' }}>
                월 예산의 80% 이상 사용했습니다.
              </p>
            )}
          </div>
        </>
      )}

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
          {[
            { href: '/admin/marketing/contents', label: '콘텐츠 생성' },
            { href: '/admin/marketing/calendar', label: '캘린더' },
            { href: '/admin/marketing/templates', label: '템플릿' },
            { href: '/admin/marketing/hashtags', label: '해시태그' },
          ].map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              style={{
                display: 'inline-block',
                padding: '12px 20px',
                background: '#1A1A2E',
                color: 'white',
                borderRadius: 6,
                textDecoration: 'none',
                fontSize: 14,
                fontWeight: 500,
              }}
            >
              {label}
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
