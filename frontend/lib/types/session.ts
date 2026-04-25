export type RelationType =
  | 'couple'
  | 'marriage'
  | 'friend'
  | 'family'
  | 'parent_child';

export type ConflictType = 'factual' | 'difference' | 'mixed';

export type ParticipantRole = 'A' | 'B';

export type SessionStatus =
  | 'waiting_b'
  | 'b_joined'
  | 'in_mediation'
  | 'completed'
  | 'solo_mode'
  | 'terminated';

export interface Session {
  id: string;
  createdBy: string;
  inviteToken: string;
  inviteeId?: string;
  inviteeGuestName?: string;
  relationType: RelationType;
  category: {
    majorId: string;
    middleId: string;
    minorId: string;
    customText?: string;
  };
  status: SessionStatus;
  currentTurn: number;
  turns: Turn[];
  createdAt: string;
  completedAt?: string;
  reportId?: string;
}

export interface Turn {
  turnNumber: number;
  role: ParticipantRole;
  content: string;
  mediatorMessage?: string;
  isPerspectiveTaking?: boolean;
  skipped?: boolean;
  createdAt: string;
}

export interface NVCScript {
  observation: string;
  feeling: string;
  need: string;
  request: string;
}

export interface HorsemenDetection {
  criticism: { detected: boolean; examples?: string[] };
  defensiveness: { detected: boolean; examples?: string[] };
  contempt: { detected: boolean; examples?: string[] };
  stonewalling: { detected: boolean; examples?: string[] };
}

export interface NeedsMapPayload {
  axisX: string;
  axisY?: string;
  positionA: { x: number; y: number };
  positionB: { x: number; y: number } | null;
  interpretation: string;
}

export interface ContributionRatio {
  a: number;
  b: number;
  label: {
    a: string;
    b: string;
  };
}

export interface MetaphorCard {
  title: string;
  body: string;
  color: 'lavender' | 'green' | 'pink';
}

export interface Report {
  id: string;
  sessionId: string;
  conflictType: ConflictType | null;
  contributionRatio: ContributionRatio | null;
  needsMap: NeedsMapPayload;
  metaphorCards?: MetaphorCard[];
  nvcScripts?: {
    aToB: NVCScript;
    bToA: NVCScript;
  };
  repairSuggestions: string[];
  isSoloMode: boolean;
  powerImbalanceDetected?: boolean;
  aPatternFeedback?: string;
  suggestedApproach?: string;
  inviteAgainCTA?: string;
  createdAt: string;
}
