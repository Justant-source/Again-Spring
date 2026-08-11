/**
 * 작성자(orderIdx 0) 공감 비율 해석.
 * 우선순위: voteResult.options[0].percentage → authorPct → count 비율 → (표 없을 때만) 50.
 * 실표가 있는데 percentage가 빠져도 50으로 강제하지 않는다(count/authorPct로 복구).
 */
export function resolveAuthorPct(input: {
  authorPct?: number | null;
  voteResult?: {
    totalVotes?: number;
    options?: Array<{ count?: number; percentage?: number | null }>;
  } | null;
  /** 목록 등 voteResult 없이 voteCount만 있을 때 */
  voteCount?: number | null;
}): number {
  const opts = input.voteResult?.options ?? [];
  const authorOpt = opts[0];
  const totalFromOpts =
    opts.length > 0 ? opts.reduce((s, o) => s + (o.count ?? 0), 0) : 0;
  const total =
    input.voteResult?.totalVotes ??
    (totalFromOpts > 0 ? totalFromOpts : input.voteCount ?? 0);

  const pct = authorOpt?.percentage;
  if (pct != null && !Number.isNaN(Number(pct)) && total > 0) {
    return Math.round(Number(pct));
  }
  if (input.authorPct != null && !Number.isNaN(Number(input.authorPct))) {
    return Math.round(Number(input.authorPct));
  }
  if (total > 0 && authorOpt?.count != null) {
    return Math.round((authorOpt.count / total) * 100);
  }
  return 50;
}
