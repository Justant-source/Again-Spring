import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { HoldingDraftDialog } from '@/components/admin/marketing/HoldingDraftDialog';
import type { MarketingHoldingDraft } from '@/lib/api/admin/marketing';
import type { AdminPost } from '@/lib/api/admin/content';

vi.mock('@/lib/api/admin/content', () => ({
  getAdminPost: vi.fn(),
}));

import { getAdminPost } from '@/lib/api/admin/content';

const post: AdminPost = {
  id: 'post-1',
  authorId: 'user-1',
  title: '오늘의 사연',
  category: 'RELATIONSHIP',
  status: 'PUBLISHED',
  viewCount: 10,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
  deletedAt: null,
  deletedByAdminId: null,
  bodyPublished: '작성자 본문입니다.',
  partnerBodyPublished: '상대방 본문입니다.',
};

const draft: MarketingHoldingDraft = {
  title: '기존 draft 제목',
  promoTitle: '기존 promo 제목',
  tags: ['갈등', '연애', '이별'],
  topComments: [],
};

describe('HoldingDraftDialog', () => {
  beforeEach(() => {
    vi.mocked(getAdminPost).mockResolvedValue(post);
  });

  it('loads and shows the live post title/author/partner body (read-only)', async () => {
    render(
      <HoldingDraftDialog
        open
        postId="post-1"
        draft={draft}
        onClose={() => {}}
        onSave={vi.fn()}
      />
    );

    expect(getAdminPost).toHaveBeenCalledWith('post-1');
    expect(await screen.findByText('오늘의 사연')).toBeInTheDocument();
    expect(screen.getByTestId('holding-draft-live-author-body')).toHaveTextContent(
      '작성자 본문입니다.'
    );
    expect(screen.getByTestId('holding-draft-live-partner-body')).toHaveTextContent(
      '상대방 본문입니다.'
    );
    // promoTitle must never be rendered as an editable/visible field
    expect(screen.queryByText(/promoTitle/i)).not.toBeInTheDocument();
  });

  it('shows an error state when getAdminPost fails', async () => {
    vi.mocked(getAdminPost).mockRejectedValue(new Error('network down'));
    render(
      <HoldingDraftDialog
        open
        postId="post-1"
        draft={draft}
        onClose={() => {}}
        onSave={vi.fn()}
      />
    );
    expect(
      await screen.findByTestId('holding-draft-live-post-error')
    ).toHaveTextContent('network down');
  });

  it('renders every tag as its own chip and allows removing one', async () => {
    render(
      <HoldingDraftDialog
        open
        postId="post-1"
        draft={draft}
        onClose={() => {}}
        onSave={vi.fn()}
      />
    );
    await screen.findByText('오늘의 사연');
    expect(screen.getByTestId('holding-draft-tag-0')).toHaveTextContent('갈등');
    expect(screen.getByTestId('holding-draft-tag-1')).toHaveTextContent('연애');
    expect(screen.getByTestId('holding-draft-tag-2')).toHaveTextContent('이별');

    fireEvent.click(screen.getByTestId('holding-draft-tag-remove-1'));
    expect(screen.queryByText('연애')).not.toBeInTheDocument();
    expect(screen.getByTestId('holding-draft-tag-0')).toHaveTextContent('갈등');
    expect(screen.getByTestId('holding-draft-tag-1')).toHaveTextContent('이별');
  });

  it('adds tags typed comma-separated or as a JSON array via the add button', async () => {
    render(
      <HoldingDraftDialog
        open
        postId="post-1"
        draft={{ ...draft, tags: [] }}
        onClose={() => {}}
        onSave={vi.fn()}
      />
    );
    await screen.findByText('오늘의 사연');
    const input = screen.getByTestId('holding-draft-tags-input');
    fireEvent.change(input, { target: { value: '하나, 둘, 셋' } });
    fireEvent.click(screen.getByTestId('holding-draft-tags-add'));

    expect(screen.getByTestId('holding-draft-tag-0')).toHaveTextContent('하나');
    expect(screen.getByTestId('holding-draft-tag-1')).toHaveTextContent('둘');
    expect(screen.getByTestId('holding-draft-tag-2')).toHaveTextContent('셋');
  });

  it('saves tags + topComments while preserving title/promoTitle from the base draft', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(
      <HoldingDraftDialog
        open
        postId="post-1"
        draft={draft}
        onClose={() => {}}
        onSave={onSave}
      />
    );
    await screen.findByText('오늘의 사연');
    fireEvent.click(screen.getByTestId('holding-draft-tag-remove-0'));
    fireEvent.click(screen.getByTestId('holding-draft-save'));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const [payload] = onSave.mock.calls[0];
    expect(payload.postId).toBe('post-1');
    expect(payload.draft.tags).toEqual(['연애', '이별']);
    expect(payload.draft.title).toBe('기존 draft 제목');
    expect(payload.draft.promoTitle).toBe('기존 promo 제목');
  });
});
