'use client';

import { TrafficDto } from '@/lib/api/admin/stats';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';

interface TrafficPanelProps {
  traffic: TrafficDto | null;
  loading: boolean;
}

export function TrafficPanel({ traffic, loading }: TrafficPanelProps) {
  if (loading) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200 animate-pulse space-y-4">
        <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
        <div className="h-48 bg-gray-200 rounded mb-4" />
        <div className="h-40 bg-gray-200 rounded" />
      </div>
    );
  }

  if (!traffic) {
    return (
      <div className="p-4 bg-white rounded-lg border border-gray-200">
        <p className="text-sm text-gray-500">유입 데이터가 아직 수집되지 않았어요</p>
      </div>
    );
  }

  const hasTrafficData = traffic.dailySeries && traffic.dailySeries.length > 0;
  const hasSources = traffic.topSources && traffic.topSources.length > 0;
  const hasCampaigns = traffic.topCampaigns && traffic.topCampaigns.length > 0;

  return (
    <div className="p-4 bg-white rounded-lg border border-gray-200 space-y-6">
      <h3 className="text-sm font-semibold text-gray-900">트래픽 분석</h3>

      {/* Daily Traffic Chart */}
      {hasTrafficData ? (
        <div>
          <h4 className="text-xs font-medium text-gray-700 mb-3">일별 방문</h4>
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={traffic.dailySeries} margin={{ top: 10, right: 20, left: 0, bottom: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis
                dataKey="date"
                tick={{ fontSize: 10 }}
                stroke="#9ca3af"
              />
              <YAxis
                tick={{ fontSize: 10 }}
                stroke="#9ca3af"
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'white',
                  border: '1px solid #e5e7eb',
                  borderRadius: 4,
                }}
              />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              <Line
                type="monotone"
                dataKey="visits"
                stroke="#1F2937"
                dot={false}
                name="방문"
                strokeWidth={2}
              />
              <Line
                type="monotone"
                dataKey="uniqueSessions"
                stroke="#5F8F76"
                dot={false}
                name="고유 세션"
                strokeWidth={2}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="text-sm text-gray-500">일별 트래픽 데이터 없음</div>
      )}

      {/* Top Sources Table */}
      {hasSources ? (
        <div>
          <h4 className="text-xs font-medium text-gray-700 mb-3">상위 유입 채널</h4>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-2 px-3 font-medium text-gray-700">채널</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-700">방문</th>
                </tr>
              </thead>
              <tbody>
                {traffic.topSources.slice(0, 10).map((source, idx) => (
                  <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-2 px-3 text-gray-700">{source.source || '(direct)'}</td>
                    <td className="py-2 px-3 text-right text-gray-700">
                      {source.visits.toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="text-sm text-gray-500">유입 채널 데이터 없음</div>
      )}

      {/* Top Campaigns Table */}
      {hasCampaigns ? (
        <div>
          <h4 className="text-xs font-medium text-gray-700 mb-3">상위 캠페인</h4>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-2 px-3 font-medium text-gray-700">캠페인</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-700">방문</th>
                </tr>
              </thead>
              <tbody>
                {traffic.topCampaigns.slice(0, 10).map((campaign, idx) => (
                  <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-2 px-3 text-gray-700">{campaign.campaign || '(none)'}</td>
                    <td className="py-2 px-3 text-right text-gray-700">
                      {campaign.visits.toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="text-sm text-gray-500">캠페인 데이터 없음</div>
      )}
    </div>
  );
}
