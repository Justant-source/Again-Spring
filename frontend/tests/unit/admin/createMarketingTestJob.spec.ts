import { describe, it, expect, vi, beforeEach } from 'vitest';

const post = vi.fn();
vi.mock('@/lib/api/client', () => ({ api: { post: (...a: unknown[]) => post(...a) } }));

import { createMarketingTestJob } from '@/lib/api/admin/marketing';

describe('createMarketingTestJob', () => {
  beforeEach(() => {
    post.mockReset();
    post.mockResolvedValue({ data: { id: 7 } });
  });

  it('arm이 없으면 본문에 formatArm을 넣지 않는다(기존 동작)', async () => {
    await createMarketingTestJob('p1', ['youtube_shorts'], 'marketing_v2');
    expect(post).toHaveBeenCalledWith('/api/admin/marketing/jobs', {
      postId: 'p1',
      targets: ['youtube_shorts'],
      autoPublish: false,
      renderProfile: 'marketing_v2',
    });
  });

  it('arm을 지정하면 formatArm을 보내고 autoPublish는 항상 false', async () => {
    await createMarketingTestJob('p1', ['youtube_shorts'], 'marketing_v2', 'short_reveal');
    const body = post.mock.calls[0][1];
    expect(body.formatArm).toBe('short_reveal');
    expect(body.autoPublish).toBe(false);
  });
});

describe('startPreviewBatch', () => {
  it('preview-batch 엔드포인트에 본문을 그대로 보낸다', async () => {
    post.mockReset();
    post.mockResolvedValue({ data: { postIds: ['a'], arms: ['dilemma'], platform: 'youtube_shorts' } });
    const { startPreviewBatch } = await import('@/lib/api/admin/marketingExperiment');
    const r = await startPreviewBatch({ postCount: 5, platform: 'youtube_shorts' });
    expect(post).toHaveBeenCalledWith('/api/admin/marketing/experiment/preview-batch', {
      postCount: 5,
      platform: 'youtube_shorts',
    });
    expect(r.arms).toEqual(['dilemma']);
  });
});
