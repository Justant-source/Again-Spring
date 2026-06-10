'use client';

import { KpiMetricDto } from '@/lib/api/admin/dashboard';
import { AdminStatCard } from '@/components/admin/AdminStatCard';

interface KpiGridProps {
  metrics: KpiMetricDto[];
  loading?: boolean;
}

const KPI_HREF_MAP: Record<string, string> = {
  pendingReports: '/admin/reports?filter=PENDING',
  openInquiries: '/admin/inquiries',
};

export function KpiGrid({ metrics, loading }: KpiGridProps) {
  if (loading || metrics.length === 0) {
    return (
      <div className="p-6 bg-white rounded-lg border">
        <h2 className="text-sm font-semibold text-gray-900 mb-4">주요 지표</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4" data-testid="admin-kpi-grid">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="h-24 bg-gray-200 rounded animate-pulse"></div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 bg-white rounded-lg border">
      <h2 className="text-sm font-semibold text-gray-900 mb-4">주요 지표</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4" data-testid="admin-kpi-grid">
        {metrics.map((metric) => (
          <AdminStatCard
            key={metric.key}
            label={metric.label}
            value={metric.value.toLocaleString()}
            delta={
              metric.deltaPercent !== null
                ? `${metric.deltaPercent > 0 ? '+' : ''}${metric.deltaPercent.toFixed(1)}%`
                : undefined
            }
            deltaPositive={metric.deltaPercent !== null && metric.deltaPercent >= 0}
            sparkline={metric.sparkline}
            href={KPI_HREF_MAP[metric.key]}
          />
        ))}
      </div>
    </div>
  );
}
