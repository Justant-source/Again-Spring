// Source: /home/justant/Data/Again-Spring/frontend/lib/types/session.ts (Report interface)
// Copied for self-contained shared types; keep in sync with frontend canonical

import type {
  ConflictType,
  HorsemenDetection,
  NeedsMapPayload,
  NVCScript,
  ContributionRatio,
} from './session';

export interface Report {
  id: string;
  sessionId: string;
  conflictType: ConflictType | null;
  contributionRatio: ContributionRatio | null;
  needsMap: NeedsMapPayload;
  nvcScripts?: {
    aToB: NVCScript;
    bToA: NVCScript;
  };
  repairSuggestions: string[];
  isSoloMode: boolean;
  aPatternFeedback?: string;
  suggestedApproach?: string;
  inviteAgainCTA?: string;
  createdAt: string;
}
