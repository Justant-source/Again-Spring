import { describe, it, expect } from 'vitest';
import {
  groupVariantsByPost,
  resolveHookRule,
  getRenderProfileOption,
  RENDER_PROFILE_OPTIONS,
  type MarketingJob,
} from '@/lib/api/admin/marketing';

function job(over: Partial<MarketingJob>): MarketingJob {
  return {
    id: 1,
    remoteJobId: null,
    postId: 'p1',
    status: 'READY',
    phase: null,
    progress: 1,
    targets: ['youtube_shorts'],
    autoPublish: false,
    artifacts: null,
    publications: null,
    errorMessage: null,
    pollFailCount: 0,
    lastPolledAt: null,
    createdAt: '2026-10-18T00:00:00Z',
    updatedAt: '2026-10-18T00:00:00Z',
    ...over,
  };
}

describe('render test variants', () => {
  it('허용 프로필 5종을 노출한다', () => {
    expect(RENDER_PROFILE_OPTIONS.map((o) => o.key)).toEqual([
      'marketing_v2',
      'marketing_fast',
      'marketing_hook_a',
      'marketing_hook_b',
      'marketing_hook_c',
    ]);
    expect(getRenderProfileOption('x').short).toBe('x');
  });

  it('훅 규칙 태그가 없으면 v1', () => {
    expect(resolveHookRule(job({}))).toBe('v1');
    expect(resolveHookRule(job({ hookRule: 'v2' }))).toBe('v2');
    expect(resolveHookRule(null)).toBe('v1');
  });

  it('같은 사연의 서로 다른 변형만 묶고, 변형당 최신 1건만 남긴다', () => {
    const jobs = [
      job({ id: 1, postId: 'a', renderProfile: 'marketing_v2', createdAt: '2026-10-18T01:00:00Z' }),
      job({ id: 2, postId: 'a', renderProfile: 'marketing_v2', createdAt: '2026-10-18T02:00:00Z' }),
      job({ id: 3, postId: 'a', renderProfile: 'marketing_hook_b', hookRule: 'v2', createdAt: '2026-10-18T03:00:00Z' }),
      job({ id: 4, postId: 'b', renderProfile: 'marketing_v2' }),
    ];
    const groups = groupVariantsByPost(jobs);
    expect(groups).toHaveLength(1);
    expect(groups[0].map((j) => j.id)).toEqual([2, 3]);
  });
});
