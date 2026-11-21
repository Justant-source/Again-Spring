import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { ExperimentPanel } from '@/components/admin/marketing/ExperimentPanel';
import type { ExperimentOverview } from '@/lib/api/admin/marketingExperiment';

vi.mock('@/lib/api/client', () => ({
  api: { get: vi.fn(), put: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

const { api } = await import('@/lib/api/client');
const BASE = '/api/admin/marketing/experiment';

function build(over: Partial<ExperimentOverview> = {}): ExperimentOverview {
  return {
    settings: { enabled: false, topicBiasEnabled: false, minSamples: 5 },
    arms: [
      {
        key: 'fast_cut', renderProfile: 'marketing_hook_a', scriptStyle: 'punchy', commentCount: 3,
        outroMode: 'cta', maxDurationSec: 45, enabled: false, description: '빠른 컷 편집',
        assigned30d: 12, outcomes60d: 8, meanReward: 1.234, breakouts: 2,
      },
    ],
    topics: [
      { platform: 'x_thread', category: 'FAMILY', hookEmotion: 'anger', n: 6, meanReward: 0.5, multiplier: 0.8 },
      { platform: 'youtube_shorts', category: 'WORK', hookEmotion: 'regret', n: 9, meanReward: 2, multiplier: 1.6 },
    ],
    accounts: [
      { platform: 'x', collectedOn: '2026-11-01', followers: 100 },
      { platform: 'x', collectedOn: '2026-11-14', followers: 110 },
      { platform: 'x', collectedOn: '2026-11-21', followers: 130 },
    ],
    ...over,
  };
}

describe('ExperimentPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: build() });
    (api.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} });
    (api.patch as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} });
    (api.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { created: 3 } });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });
  afterEach(() => vi.restoreAllMocks());

  it('renders arms, topics sorted by multiplier desc, and follower deltas', async () => {
    render(<ExperimentPanel />);
    expect(await screen.findByText('빠른 컷 편집')).toBeTruthy();
    expect(api.get).toHaveBeenCalledWith(BASE);

    const rows = within(screen.getByTestId('experiment-topics-table')).getAllByRole('row');
    expect(rows[1].textContent).toContain('1.6배');
    expect(rows[2].textContent).toContain('0.8배');

    const acc = screen.getByTestId('experiment-accounts-table').textContent ?? '';
    expect(acc).toContain('130');
    expect(acc).toContain('+20'); // 7일 전(11-14=110)
    expect(acc).not.toContain('+30'); // 30일 전(10-22) 이전 기록 없음 → '-'
  });

  it('settings switch asks confirmation then PUTs', async () => {
    render(<ExperimentPanel />);
    fireEvent.click(await screen.findByTestId('experiment-switch-enabled'));
    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(api.put).toHaveBeenCalledWith(`${BASE}/settings`, { enabled: true }));

    fireEvent.click(screen.getByTestId('experiment-switch-topic-bias'));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith(`${BASE}/settings`, { topicBiasEnabled: true }));
  });

  it('does not call API when confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<ExperimentPanel />);
    fireEvent.click(await screen.findByTestId('experiment-switch-enabled'));
    expect(api.put).not.toHaveBeenCalled();
  });

  it('arm toggle PATCHes the arm', async () => {
    render(<ExperimentPanel />);
    fireEvent.click(await screen.findByTestId('experiment-arm-toggle-fast_cut'));
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/arms/fast_cut`, { enabled: true }));
  });

  it('score button POSTs and reports created count', async () => {
    render(<ExperimentPanel />);
    fireEvent.click(await screen.findByTestId('experiment-score-run'));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(`${BASE}/score/run`));
    expect(await screen.findByText('3건을 새로 채점했습니다.')).toBeTruthy();
  });

  it('renders empty states', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: build({ arms: [], topics: [], accounts: [] }) });
    render(<ExperimentPanel />);
    expect(await screen.findByTestId('experiment-arms-empty')).toBeTruthy();
    expect(screen.getByTestId('experiment-topics-empty')).toBeTruthy();
    expect(screen.getByTestId('experiment-accounts-empty')).toBeTruthy();
  });

  it('shows error when load fails', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));
    render(<ExperimentPanel />);
    expect((await screen.findByTestId('experiment-error')).textContent).toContain('boom');
  });
});
