export type RelationType =
  | 'couple'
  | 'marriage'
  | 'friend'
  | 'family'
  | 'parent_child'
  | 'korean_specific';

export type ConflictType = 'factual' | 'difference' | 'mixed';

export type SessionStatus =
  | 'chatting_solo'
  | 'chatting_duo'
  | 'awaiting_finalization'
  | 'completed'
  | 'terminated';

export type MessageSender =
  | 'USER_A'
  | 'USER_B'
  | 'MEDIATOR_TO_A'
  | 'MEDIATOR_TO_B';

export interface Message {
  id: number;
  sender: MessageSender;
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
}

export interface MessageMetadata {
  id: number;
  sender: MessageSender;
  charCount: number;
  createdAt: string;
}

export interface PartnerStatus {
  joined: boolean;
  isActive: boolean;
  inviteSent: boolean;
  messageCount: number;
  lastActivityAt: string | null;
}

export interface Session {
  id: string;
  status: SessionStatus;
  relationType: RelationType;
  category: {
    majorId: string;
    middleId: string;
    minorId: string;
    customText?: string;
  };
  createdByUserId: string;
  inviteeUserId: string | null;
  inviteToken: string | null;
  inviteExpiresAt: string | null;
  userAMessageCount: number;
  userBMessageCount: number;
  partnerJoinedAt: string | null;
  finalizeSuggestedAt: string | null;
  finalizeAgreedByA: boolean;
  finalizeAgreedByB: boolean;
  myRole?: 'USER_A' | 'USER_B';
  createdAt: string;
  completedAt?: string;
  reportId?: string;
}

export interface NVCScript {
  observation: string;
  feeling: string;
  need: string;
  request: string;
}

export interface NvcSuggestion {
  observation: string;
  feeling: string;
  need: string;
  request: string;
  fourSentenceDraft: string; // V1.5: 카톡에 그대로 보낼 수 있는 4문장 합성본
}

export interface HorsemenDetection {
  criticism: { detected: boolean; examples?: string[] };
  defensiveness: { detected: boolean; examples?: string[] };
  contempt: { detected: boolean; examples?: string[] };
  stonewalling: { detected: boolean; examples?: string[] };
}

export interface HorsemenObservation {
  criticism: { score: number; detected: boolean };
  defensiveness: { score: number; detected: boolean };
  contempt: { score: number; detected: boolean };
  stonewalling: { score: number; detected: boolean };
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
  horsemenObservation?: HorsemenObservation;
  repairSuggestions: string[];
  isSoloMode: boolean;
  powerImbalanceDetected?: boolean;
  aPatternFeedback?: string;
  nvcSuggestion?: NvcSuggestion;
  metaphorId?: string; // V1.5: 12종 메타포 id (locked-mailbox, boiling-kettle, ...)
  suggestedApproach?: string;
  inviteAgainCTA?: string;
  createdAt: string;
}
