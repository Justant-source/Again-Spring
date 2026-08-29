import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { AcquisitionFunnelPanel } from '@/components/admin/marketing/AcquisitionFunnelPanel';
import type { AcquisitionFunnel } from '@/lib/api/admin/acquisition';

vi.mock('@/lib/api/client', () => ({
  api: {
    get: vi.fn(),
  },
}));

const { api } = await import('@/lib/api/client');

function buildFunnel(overrides: Partial<AcquisitionFunnel> = {}): AcquisitionFunnel {
  return {
    days: 30,
    totalVisits: 0,
    totalVisitors: 0,
    totalSignups: 0,
    botSplit: { human: 0, bot: 0 },
    byChannel: [],
    daily: [],
    topReferrers: [],
    topPaths: [],
    ...overrides,
  };
}

describe('AcquisitionFunnelPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests the acquisition endpoint with the default 30-day window', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: buildFunnel() });

    render(<AcquisitionFunnelPanel />);

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith(
        '/api/admin/marketing/stats/acquisition',
        { params: { days: 30 } }
      );
    });
  });

  it('shows an honest empty state and highlights every zero KPI when all totals are 0', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: buildFunnel() });

    render(<AcquisitionFunnelPanel />);

    await waitFor(() => {
      expect(screen.getByTestId('acquisition-empty-state')).toBeInTheDocument();
    });
    expect(screen.getByTestId('acquisition-empty-state')).toHaveTextContent(
      '아직 유입이 없습니다'
    );

    const visitsCard = screen.getByTestId('acquisition-kpi-visits');
    expect(visitsCard).toHaveTextContent('0');
    expect(visitsCard).toHaveTextContent('방문 0건');

    const visitorsCard = screen.getByTestId('acquisition-kpi-visitors');
    expect(visitorsCard).toHaveTextContent('고유 방문자 0명');

    const signupsCard = screen.getByTestId('acquisition-kpi-signups');
    expect(signupsCard).toHaveTextContent('가입 0건');

    const botRateCard = screen.getByTestId('acquisition-kpi-bot-rate');
    expect(botRateCard).toHaveTextContent('봇 트래픽 없음');
  });

  it('does not show the empty state and renders real numbers when there is traffic', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: buildFunnel({
        totalVisits: 812,
        totalVisitors: 640,
        totalSignups: 1,
        botSplit: { human: 812, bot: 114 },
        byChannel: [
          { source: 'x_thread', visits: 500, visitors: 400, sessions: 420, signups: 1 },
          { source: '(direct/organic)', visits: 200, visitors: 150, sessions: 160, signups: 0 },
          { source: '(unknown)', visits: 0, visitors: 0, sessions: 0, signups: 0 },
        ],
        daily: [
          { date: '2026-08-27', visits: 300, visitors: 250, signups: 0 },
          { date: '2026-08-28', visits: 512, visitors: 390, signups: 1 },
        ],
        topReferrers: [{ host: 't.co', visits: 114 }],
        topPaths: [{ path: '/posts/abc', visits: 300, visitors: 250 }],
      }),
    });

    render(<AcquisitionFunnelPanel />);

    await waitFor(() => {
      expect(screen.queryByTestId('acquisition-empty-state')).not.toBeInTheDocument();
    });

    expect(screen.getByTestId('acquisition-kpi-visits')).toHaveTextContent('812');
    expect(screen.getByTestId('acquisition-kpi-visitors')).toHaveTextContent('640');
    // signups is 1, not 0, so the zero-note copy must not appear
    expect(screen.getByTestId('acquisition-kpi-signups')).not.toHaveTextContent('가입 0건');
    expect(screen.getByTestId('acquisition-kpi-bot-rate')).toHaveTextContent('12.3%');

    // channel table keeps the (direct/organic) and (unknown) rows visible verbatim
    const channelTable = screen.getByTestId('acquisition-channel-table');
    expect(channelTable).toHaveTextContent('x_thread');
    expect(channelTable).toHaveTextContent('(direct/organic)');
    expect(channelTable).toHaveTextContent('(unknown)');

    expect(screen.getByTestId('acquisition-daily-chart')).toBeInTheDocument();
    expect(screen.getByTestId('acquisition-top-referrers')).toHaveTextContent('t.co');
    expect(screen.getByTestId('acquisition-top-paths')).toHaveTextContent('/posts/abc');
  });

  it('shows an error message when the request fails', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('network down'));

    render(<AcquisitionFunnelPanel />);

    await waitFor(() => {
      expect(screen.getByText('network down')).toBeInTheDocument();
    });
  });
});
