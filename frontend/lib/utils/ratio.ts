/** Round contribution ratio to the nearest 5 while preserving a + b = 100. */
export function roundRatio(
  a: number,
  b: number,
): { a: number; b: number } {
  const total = a + b || 100;
  const normalized = Math.round((a / total) * 100);
  const rounded = Math.round(normalized / 5) * 5;
  const clamped = Math.max(30, Math.min(70, rounded));
  return { a: clamped, b: 100 - clamped };
}

/**
 * Derive chip labels from the rounded ratio.
 * Higher-value side = "먼저 다가가면 좋은 쪽".
 */
export function ratioLabels(
  a: number,
  b: number,
): { a: string; b: string } {
  if (a > b) {
    return {
      a: '먼저 다가가면 좋은 쪽',
      b: '마음 열고 기다려주면 좋은 쪽',
    };
  }
  if (b > a) {
    return {
      a: '마음 열고 기다려주면 좋은 쪽',
      b: '먼저 다가가면 좋은 쪽',
    };
  }
  return {
    a: '함께 마주 걷는 쪽',
    b: '함께 마주 걷는 쪽',
  };
}
