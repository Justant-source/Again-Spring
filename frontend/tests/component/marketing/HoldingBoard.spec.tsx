import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import { HoldingBoard } from '@/components/admin/marketing/HoldingBoard';
import type { MarketingHoldingRow } from '@/lib/api/admin/marketing';

function buildRow(overrides: Partial<MarketingHoldingRow> = {}): MarketingHoldingRow {
  return {
    postId: 'post-1',
    title: '테스트 사연',
    status: 'IN_POOL',
    pinFormat: null,
    scoreSnapshot: 12.3,
    rankSnapshot: 1,
    platformRankSnapshot: {},
    viewCount: 10,
    commentCount: 2,
    voteCount: 5,
    projectedFormat: 'VIDEO',
    draft: null,
    lockedAt: null,
    postCreatedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('HoldingBoard', () => {
  it('renders normalized status/format labels', () => {
    render(
      <HoldingBoard
        rows={[
          buildRow({ postId: 'p-pool', status: 'IN_POOL' }),
          buildRow({ postId: 'p-cut', status: 'OUT_OF_CUT' }),
        ]}
      />
    );

    expect(screen.getByText('포맷')).toBeInTheDocument();
    expect(screen.getByText('후보')).toBeInTheDocument();
    expect(screen.getByText('후보 외')).toBeInTheDocument();
    expect(screen.queryByText('풀내')).not.toBeInTheDocument();
    expect(screen.queryByText('후보외')).not.toBeInTheDocument();
    expect(screen.queryByText('투영 포맷')).not.toBeInTheDocument();
  });

  it('reveals an inline VIDEO/TEXT select on pin click and calls onPin with the chosen format', () => {
    const onPin = vi.fn();
    const row = buildRow({ status: 'IN_POOL', projectedFormat: 'TEXT' });
    render(<HoldingBoard rows={[row]} onPin={onPin} onUnpin={vi.fn()} />);

    expect(screen.queryByTestId('holding-pin-format-select-post-1')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('holding-pin-post-1'));

    const select = screen.getByTestId('holding-pin-format-select-post-1');
    expect(select).toBeInTheDocument();
    expect(onPin).not.toHaveBeenCalled();

    fireEvent.click(select);
    fireEvent.click(screen.getByText('영상'));

    expect(onPin).toHaveBeenCalledWith(row, 'VIDEO');
    expect(screen.queryByTestId('holding-pin-format-select-post-1')).not.toBeInTheDocument();
  });

  it('cancels the inline picker without calling onPin', () => {
    const onPin = vi.fn();
    const row = buildRow({ status: 'IN_POOL' });
    render(<HoldingBoard rows={[row]} onPin={onPin} onUnpin={vi.fn()} />);

    fireEvent.click(screen.getByTestId('holding-pin-post-1'));
    expect(screen.getByTestId('holding-pin-format-select-post-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('holding-pin-cancel-post-1'));

    expect(onPin).not.toHaveBeenCalled();
    expect(screen.getByTestId('holding-pin-post-1')).toBeInTheDocument();
  });

  it('keeps the "핀 해제" unpin flow unchanged for pinned rows', () => {
    const onUnpin = vi.fn();
    const row = buildRow({ status: 'PINNED', pinFormat: 'VIDEO' });
    render(<HoldingBoard rows={[row]} onPin={vi.fn()} onUnpin={onUnpin} />);

    const unpinButton = screen.getByTestId('holding-unpin-post-1');
    fireEvent.click(unpinButton);
    expect(onUnpin).toHaveBeenCalledWith(row);
  });
});
