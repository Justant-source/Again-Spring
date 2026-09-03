import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EffectiveGatesPanel } from '@/components/admin/ai-user/EffectiveGatesPanel';
import type { EffectiveGates } from '@/lib/api/admin/ai-user';

vi.mock('@/lib/api/client', () => ({
  api: {
    get: vi.fn(),
  },
}));

const { api } = await import('@/lib/api/client');

function buildGates(overrides: Partial<EffectiveGates> = {}): EffectiveGates {
  return {
    generationAllowed: true,
    publishingAllowed: true,
    reasons: [],
    gates: [],
    ...overrides,
  };
}

describe('EffectiveGatesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests the effective-gates endpoint on mount', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: buildGates() });

    render(<EffectiveGatesPanel />);

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith('/api/admin/ai-user/effective-gates');
    });
  });

  it('renders a graceful fallback instead of crashing when the endpoint is unavailable (e.g. 404)', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('Request failed with status code 404'));

    render(<EffectiveGatesPanel />);

    await waitFor(() => {
      expect(screen.getByTestId('ai-user-gates-error')).toBeInTheDocument();
    });
    expect(screen.getByTestId('ai-user-gates-error')).toHaveTextContent('게이트 상태를 불러올 수 없음');
    expect(screen.queryByTestId('ai-user-gate-table')).not.toBeInTheDocument();
  });

  it('renders generation/publishing verdicts, reasons, and the gate table when blocked', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: buildGates({
        generationAllowed: false,
        publishingAllowed: false,
        reasons: ['ai_user.kill-switch=true', 'provider_ai_post_bundle=OFF'],
        gates: [
          { name: 'aiUserKillSwitch', source: 'db', value: true, blocks: '생성+발행' },
          { name: 'ai.user.enabled', source: 'yml', value: false, blocks: '생성' },
          { name: 'LLM_WORKER_URL', source: 'env', value: 'unset', blocks: '생성' },
        ],
      }),
    });

    render(<EffectiveGatesPanel />);

    await waitFor(() => {
      expect(screen.getByTestId('ai-user-gate-generation')).toHaveTextContent('생성 막힘');
    });
    expect(screen.getByTestId('ai-user-gate-publishing')).toHaveTextContent('발행 막힘');

    const reasons = screen.getByTestId('ai-user-gate-reasons');
    expect(reasons).toHaveTextContent('ai_user.kill-switch=true');
    expect(reasons).toHaveTextContent('provider_ai_post_bundle=OFF');

    const table = screen.getByTestId('ai-user-gate-table');
    expect(table).toHaveTextContent('aiUserKillSwitch');
    expect(table).toHaveTextContent('db');
    expect(table).toHaveTextContent('yml');
    expect(table).toHaveTextContent('env');
    expect(table).toHaveTextContent('LLM_WORKER_URL');
  });

  it('renders a caveat that the verdict is a summary, not per-workload truth', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: buildGates() });

    render(<EffectiveGatesPanel />);

    await waitFor(() => {
      expect(screen.getByTestId('ai-user-gates-caveat')).toBeInTheDocument();
    });
    expect(screen.getByTestId('ai-user-gates-caveat')).toHaveTextContent(
      '요약 판정입니다. 워크로드별 실제 동작은 gates 표와 각 스케줄러 로그를 기준으로 확인하세요.'
    );
  });

  it('renders a null gate value as - instead of the literal string "null"', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: buildGates({
        gates: [{ name: 'config_updated_by', source: 'db', value: null, blocks: '' }],
      }),
    });

    render(<EffectiveGatesPanel />);

    const table = await screen.findByTestId('ai-user-gate-table');
    expect(table).toHaveTextContent('config_updated_by');
    expect(table).toHaveTextContent('-');
    expect(table).not.toHaveTextContent('null');
  });

  it('shows an open verdict without an error state when both gates allow', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: buildGates({ gates: [{ name: 'ai.user.enabled', source: 'yml', value: true, blocks: '' }] }),
    });

    render(<EffectiveGatesPanel />);

    await waitFor(() => {
      expect(screen.getByTestId('ai-user-gate-generation')).toHaveTextContent('생성 열림');
    });
    expect(screen.getByTestId('ai-user-gate-publishing')).toHaveTextContent('발행 열림');
    expect(screen.queryByTestId('ai-user-gates-error')).not.toBeInTheDocument();
  });
});
