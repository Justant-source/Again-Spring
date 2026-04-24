import type { CommunicationStyle } from '@/lib/types';

/**
 * Maps 10 Likert answers (1–5) to 6 style axes, then picks the strongest.
 * Algorithm lifted from docs/ONBOARDING_MAPPING.md.
 */
export function calculateStyleAxes(
  answers: number[],
): Record<CommunicationStyle, number> {
  if (answers.length !== 10) {
    throw new Error(`Expected 10 answers, got ${answers.length}`);
  }
  const [q1, q2, q3, q4, q5, q6, q7, , , q10] = answers;

  return {
    wave: (((6 - q1) + q2 + (6 - q5)) / 3) * 2,
    mountain: ((q1 + (6 - q2)) / 2) * 2,
    flame: ((q3 + (6 - q5) + (6 - q6)) / 3) * 2,
    leaf: ((q4 + q6) / 2) * 2,
    moon: ((q5 + q10) / 2) * 2,
    star: ((q3 + q7) / 2) * 2,
  };
}

export function determineStyle(answers: number[]): CommunicationStyle {
  const axes = calculateStyleAxes(answers);
  return (Object.entries(axes).sort(
    (a, b) => b[1] - a[1],
  )[0][0] as CommunicationStyle);
}
