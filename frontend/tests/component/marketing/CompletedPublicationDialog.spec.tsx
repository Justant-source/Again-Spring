import { render, screen } from '@testing-library/react';
import { CompletedPublicationDialog } from '@/components/admin/marketing/CompletedPublicationDialog';
import type { CompletedHoldingView } from '@/components/admin/marketing/CompletedHoldingsBoard';

const item: CompletedHoldingView = {
  postId: 'post_test1',
  status: 'COMMITTED',
  pinFormat: 'VIDEO',
  scoreSnapshot: 20,
  lockedAt: '2026-08-16T00:00:00Z',
  createdAt: '2026-08-16T00:00:00Z',
  updatedAt: '2026-08-16T00:00:00Z',
  title: '테스트 사연',
  format: 'VIDEO',
  jobs: [
    {
      id: 665,
      status: 'RUNNING',
      targets: ['youtube_shorts'],
      createdAt: '2026-08-16T00:00:00Z',
      publications: [
        { platform: 'youtube_shorts', state: 'PENDING', url: null },
      ],
    },
  ],
};

describe('CompletedPublicationDialog', () => {
  it('Job {id}를 잡 상세 페이지 링크로 렌더한다', () => {
    render(
      <CompletedPublicationDialog open item={item} onClose={() => undefined} />,
    );

    const link = screen.getByTestId('completed-publication-job-link-665');
    expect(link).toHaveTextContent('Job 665');
    expect(link).toHaveAttribute('href', '/admin/marketing/jobs/665');
  });
});
