import { describe, expect, it } from 'vitest';
import { resolveAuthorPct } from '@/lib/utils/empathyPct';

describe('resolveAuthorPct', () => {
  it('prefers voteResult.options[0].percentage when votes exist', () => {
    expect(
      resolveAuthorPct({
        authorPct: 40,
        voteResult: {
          totalVotes: 10,
          options: [
            { count: 7, percentage: 72.4 },
            { count: 3, percentage: 27.6 },
          ],
        },
      })
    ).toBe(72);
  });

  it('uses authorPct when percentage missing', () => {
    expect(resolveAuthorPct({ authorPct: 63, voteCount: 10 })).toBe(63);
  });

  it('derives from counts when percentage and authorPct missing', () => {
    expect(
      resolveAuthorPct({
        voteResult: {
          totalVotes: 4,
          options: [{ count: 3 }, { count: 1 }],
        },
      })
    ).toBe(75);
  });

  it('does not force 50 when percentage is 0 but votes exist', () => {
    expect(
      resolveAuthorPct({
        voteResult: {
          totalVotes: 5,
          options: [
            { count: 0, percentage: 0 },
            { count: 5, percentage: 100 },
          ],
        },
      })
    ).toBe(0);
  });

  it('returns 50 only when there are no votes', () => {
    expect(resolveAuthorPct({})).toBe(50);
    expect(resolveAuthorPct({ voteCount: 0, authorPct: null })).toBe(50);
  });
});
