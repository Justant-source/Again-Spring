'use client';

import { useCallback, useEffect, useState } from 'react';
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
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { AdminTable } from '@/components/admin/AdminTable';
import {
  getAcquisitionFunnel,
  type AcquisitionFunnel,
} from '@/lib/api/admin/acquisition';

const DAY_OPTIONS = [7, 30, 90] as const;

function formatNumber(n: number): string {
  if (!Number.isFinite(n)) return '—';
  return Math.round(n).toLocaleString('ko-KR');
}

function formatPct(n: number): string {
  if (!Number.isFinite(n)) return '—';
  return `${n.toFixed(1)}%`;
}

interface KpiCardProps {
  testId: string;
  label: string;
  value: string;
  isZero: boolean;
  zeroNote: string;
}

/**
 * 0은 회색으로 죽이지 않는다 — 이 화면의 존재 이유가 "0을 보이게 하는 것"이다.
 * 값이 0이면 카드 자체를 앰버 톤으로 강조하고 별도 문구를 붙인다.
 */
function KpiCard({ testId, label, value, isZero, zeroNote }: KpiCardProps) {
  return (
    <Card
      data-testid={testId}
      className={
        isZero ? 'p-4 space-y-1 border-amber-300 bg-amber-50' : 'p-4 space-y-1'
      }
    >
      <p className="text-xs font-medium text-gray-500">{label}</p>
      <p
        className={
          isZero
            ? 'text-2xl font-bold tabular-nums text-amber-700'
            : 'text-2xl font-semibold tabular-nums text-gray-900'
        }
      >
        {value}
      </p>
      {isZero && <p className="text-xs font-medium text-amber-700">{zeroNote}</p>}
    </Card>
  );
}

/**
 * 유입 퍼널 패널 — 방문 → 고유 방문자 → 가입.
 *
 * 배경(2026-08-29): 어드민 마케팅 탭은 "발행 성공"과 플랫폼 지표(뷰·도달)까지만
 * 보여줬다. 그래서 "8만 뷰가 방문 0"이라는 사실을 한 달 동안 아무도 볼 수
 * 없었다. 이 패널은 그 다음 칸(클릭→방문→가입)을 채운다.
 */
export function AcquisitionFunnelPanel() {
  const [days, setDays] = useState<number>(30);
  const [data, setData] = useState<AcquisitionFunnel | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (d: number) => {
    setLoading(true);
    setError(null);
    try {
      const funnel = await getAcquisitionFunnel(d);
      setData(funnel);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(days);
  }, [days, load]);

  const totalVisits = data?.totalVisits ?? 0;
  const totalVisitors = data?.totalVisitors ?? 0;
  const totalSignups = data?.totalSignups ?? 0;
  const bot = data?.botSplit?.bot ?? 0;
  const human = data?.botSplit?.human ?? 0;
  const botTotal = bot + human;
  const botRate = botTotal > 0 ? (bot / botTotal) * 100 : 0;

  const isEmpty =
    !!data && totalVisits === 0 && totalVisitors === 0 && totalSignups === 0;

  return (
    <Card className="p-6 space-y-4" data-testid="marketing-acquisition-funnel-panel">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-gray-800">유입 퍼널 — 방문 → 가입</h3>
          <p className="mt-1 text-sm text-gray-500">
            발행 다음 칸입니다. 봇(크롤러)은 제외했습니다. 0은 숨기지 않고 그대로
            보여줍니다.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="space-y-1">
            <span className="text-xs text-gray-500">기간</span>
            <Select value={String(days)} onValueChange={(v) => setDays(Number(v))}>
              <SelectTrigger
                className="h-9 w-24 text-sm"
                data-testid="acquisition-days-select"
              >
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DAY_OPTIONS.map((d) => (
                  <SelectItem key={d} value={String(d)}>
                    {d}일
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button
            variant="outline"
            className="h-9"
            onClick={() => void load(days)}
            disabled={loading}
          >
            새로고침
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading && !data && (
        <div className="py-8 text-center text-sm text-gray-400">로드 중…</div>
      )}

      {!loading && isEmpty && (
        <div
          data-testid="acquisition-empty-state"
          className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800"
        >
          아직 유입이 없습니다. 발행은 되고 있지만 방문·가입으로 이어지지
          않았습니다.
        </div>
      )}

      {data && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <KpiCard
              testId="acquisition-kpi-visits"
              label={`방문 (최근 ${data.days}일)`}
              value={formatNumber(totalVisits)}
              isZero={totalVisits === 0}
              zeroNote="방문 0건"
            />
            <KpiCard
              testId="acquisition-kpi-visitors"
              label="고유 방문자"
              value={formatNumber(totalVisitors)}
              isZero={totalVisitors === 0}
              zeroNote="고유 방문자 0명"
            />
            <KpiCard
              testId="acquisition-kpi-signups"
              label="가입"
              value={formatNumber(totalSignups)}
              isZero={totalSignups === 0}
              zeroNote="가입 0건"
            />
            <KpiCard
              testId="acquisition-kpi-bot-rate"
              label="봇 비율"
              value={formatPct(botRate)}
              isZero={botTotal === 0}
              zeroNote="봇 트래픽 없음"
            />
          </div>

          <div>
            <h4 className="text-xs font-medium text-gray-700 mb-2">
              채널별 (utm_source)
            </h4>
            <div data-testid="acquisition-channel-table">
              <AdminTable
                data={data.byChannel}
                rowKey={(row) => row.source}
                emptyMessage="채널 데이터 없음"
                className="text-sm"
                columns={[
                  { key: 'source', header: '채널', render: (r) => r.source },
                  {
                    key: 'visits',
                    header: '방문',
                    render: (r) => formatNumber(r.visits),
                  },
                  {
                    key: 'visitors',
                    header: '고유방문자',
                    render: (r) => formatNumber(r.visitors),
                  },
                  {
                    key: 'signups',
                    header: '가입',
                    render: (r) => formatNumber(r.signups),
                  },
                ]}
              />
            </div>
          </div>

          <div>
            <h4 className="text-xs font-medium text-gray-700 mb-2">일별 추이</h4>
            {data.daily.length > 0 ? (
              <div data-testid="acquisition-daily-chart">
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart
                    data={data.daily}
                    margin={{ top: 10, right: 20, left: 0, bottom: 20 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} stroke="#9ca3af" />
                    <YAxis tick={{ fontSize: 10 }} stroke="#9ca3af" />
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
                      dataKey="visitors"
                      stroke="#5F8F76"
                      dot={false}
                      name="고유 방문자"
                      strokeWidth={2}
                    />
                    <Line
                      type="monotone"
                      dataKey="signups"
                      stroke="#D97706"
                      dot={false}
                      name="가입"
                      strokeWidth={2}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div
                className="text-sm text-gray-500"
                data-testid="acquisition-daily-chart-empty"
              >
                일별 데이터 없음
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <h4 className="text-xs font-medium text-gray-700 mb-2">
                유입 경로 (Referrer)
              </h4>
              <div data-testid="acquisition-top-referrers">
                <AdminTable
                  data={data.topReferrers}
                  rowKey={(row) => row.host}
                  emptyMessage="레퍼러 데이터 없음"
                  className="text-sm"
                  columns={[
                    { key: 'host', header: '호스트', render: (r) => r.host },
                    {
                      key: 'visits',
                      header: '방문',
                      render: (r) => formatNumber(r.visits),
                    },
                  ]}
                />
              </div>
            </div>
            <div>
              <h4 className="text-xs font-medium text-gray-700 mb-2">
                유입 경로 (Path)
              </h4>
              <div data-testid="acquisition-top-paths">
                <AdminTable
                  data={data.topPaths}
                  rowKey={(row) => row.path}
                  emptyMessage="경로 데이터 없음"
                  className="text-sm"
                  columns={[
                    { key: 'path', header: '경로', render: (r) => r.path },
                    {
                      key: 'visits',
                      header: '방문',
                      render: (r) => formatNumber(r.visits),
                    },
                    {
                      key: 'visitors',
                      header: '고유방문자',
                      render: (r) => formatNumber(r.visitors),
                    },
                  ]}
                />
              </div>
            </div>
          </div>
        </>
      )}
    </Card>
  );
}
