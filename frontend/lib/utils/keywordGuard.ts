import { CRISIS_KEYWORDS, WARNING_KEYWORDS } from '@/lib/constants/forbiddenWords';

export type KeywordLevel = 1 | 2 | null;

export interface KeywordCheckResult {
  level: KeywordLevel;
  category: string | null;
  matchedKeyword: string | null;
}

/**
 * Scans user text for crisis (Level 1) and warning (Level 2) keywords.
 * Level 1 → session must halt, surface CrisisResource modal.
 * Level 2 → allow session to continue but show advisory banner.
 */
export function checkKeywords(text: string): KeywordCheckResult {
  if (!text) return { level: null, category: null, matchedKeyword: null };
  const normalized = text.replace(/\s+/g, '');

  for (const [category, keywords] of Object.entries(CRISIS_KEYWORDS)) {
    for (const keyword of keywords) {
      if (normalized.includes(keyword.replace(/\s+/g, ''))) {
        return { level: 1, category, matchedKeyword: keyword };
      }
    }
  }

  for (const [category, keywords] of Object.entries(WARNING_KEYWORDS)) {
    for (const keyword of keywords) {
      if (normalized.includes(keyword.replace(/\s+/g, ''))) {
        return { level: 2, category, matchedKeyword: keyword };
      }
    }
  }

  return { level: null, category: null, matchedKeyword: null };
}
