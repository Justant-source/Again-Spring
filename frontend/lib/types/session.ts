export type RelationType =
  | 'couple'
  | 'marriage'
  | 'friend'
  | 'family'
  | 'parent_child'
  | 'korean_specific'
  | 'work';

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
  /** V47~: LLM 추론값. 세션 생성 직후 null일 수 있음 (비동기 추론). */
  relationType: RelationType | null;
  /** V47~: majorId만 잔존 (중·소분류 제거). */
  category: {
    majorId: string | null;
    customText?: string;
  } | null;
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
  /** V47 신규: 자동 생성 제목 (사용자 수정 가능). */
  title?: string | null;
  /** V47 신규: 추론 핵심 키워드 최대 2개. */
  keywords?: string[] | null;
  /** V47 신규: 한국 특화 태그 (in_law|face|lingered|generation|null). */
  koreanTag?: string | null;
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

// V12 — Solo 리포트 전용 타입
export interface SoloStageFlow {
  stage: number;
  stageName: string;
  userQuote: string;
  interpretation: string;
}

export interface RecommendedAction {
  action: string;
  rationale: string;
  isUserChosen: boolean;
}

export interface ExternalResourceGuidance {
  domain: 'crisis' | 'legal' | 'medical' | 'financial';
  resource: string;
  rationale: string;
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
    bToA?: NVCScript;
  };
  horsemenObservation?: HorsemenObservation;
  repairSuggestions: string[];
  isSoloMode: boolean;
  powerImbalanceDetected?: boolean;
  aPatternFeedback?: string;
  nvcSuggestion?: NvcSuggestion;
  metaphorId?: string;
  suggestedApproach?: string;
  inviteAgainCTA?: string;
  llmProvider?: string;
  createdAt: string;
  // V12 fields
  status?: string;
  coreSummary?: string;
  fourStageFlow?: SoloStageFlow[];
  metaphorDisplayName?: string;
  metaphorReason?: string;
  nvcObservation?: string;
  nvcFeeling?: string;
  nvcNeed?: string;
  nvcRequest?: string;
  recommendedActions?: RecommendedAction[];
  externalResourceGuidance?: ExternalResourceGuidance | null;
}
