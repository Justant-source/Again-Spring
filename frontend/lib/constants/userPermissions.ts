/**
 * 사용자 등급별 권한·제한 정책 — FE 미러.
 *
 * 권위본: shared/docs/policies/user-permissions.json (BE 런타임 로드 + 정책 문서 기준)
 *
 * Docker 빌드 컨텍스트가 frontend/ 하위로 격리되어 shared/를 직접 import 할 수 없으므로
 * 본 파일에 동일 데이터를 수동 동기화한다. JSON 변경 시 반드시 본 파일도 함께 갱신.
 */

export type DailyLimitScope = 'ip' | 'user';
export type MediatorStyleSource = 'default' | 'profile' | 'per_session';

export interface TierPermissions {
  label: string;
  summary: string;
  auth: {
    tokenExpirationSeconds: number;
    requiresEmailVerification: boolean;
    requiresOnboarding: boolean;
    canChangePassword: boolean;
  };
  sessions: {
    dailyLimit: number;
    dailyLimitScope: DailyLimitScope;
    messageTurnLimit: number | null;
    duoModeAllowed: boolean;
    canInvitePartner: boolean;
    canResumeOldSession: boolean;
  };
  profile: {
    canEditNickname: boolean;
    canEditEmail: boolean;
    canEditCommunicationStyle: boolean;
    canCompleteOnboarding: boolean;
    canSetMbti: boolean;
    canViewHistory: boolean;
    canDeleteAccount: boolean;
    deleteRequiresPassword: boolean;
  };
  data: {
    messageContentRetentionDays: number;
    sessionRetentionDays: number;
    reportRetentionDays: number | null;
  };
  ui: {
    showHistoryMenu: boolean;
    showProfileEditing: boolean;
    showConsentReconfirmModal: boolean;
    showUpgradeModalOnLimit: boolean;
    showGuestModeBadge: boolean;
    showBetaBanner: boolean;
    showAdminEntryButton: boolean;
    showLandingChatEntry: boolean;
    showCommunicationStyleSection: boolean;
  };
  mediator: {
    styleSource: MediatorStyleSource;
    defaultStyleX: number;
    defaultStyleY: number;
  };
  admin: {
    canAccessDashboard: boolean;
    canViewAllUsers: boolean;
    canModifyFeedback: boolean;
    canAnonymizeUser: boolean;
    canViewCrisisMonitor: boolean;
    canViewSystemHealth: boolean;
  };
}

export const USER_PERMISSIONS: { guest: TierPermissions; registered: TierPermissions; admin: TierPermissions } = {
  guest: {
    label: '게스트',
    summary: '회원가입 없이 체험 가능한 임시 사용자. IP·세션·턴·기간 모두 제한.',
    auth: {
      tokenExpirationSeconds: 7200,
      requiresEmailVerification: false,
      requiresOnboarding: false,
      canChangePassword: false,
    },
    sessions: {
      dailyLimit: 3,
      dailyLimitScope: 'ip',
      messageTurnLimit: 3,
      duoModeAllowed: false,
      canInvitePartner: false,
      canResumeOldSession: true,
    },
    profile: {
      canEditNickname: false,
      canEditEmail: false,
      canEditCommunicationStyle: false,
      canCompleteOnboarding: false,
      canSetMbti: false,
      canViewHistory: false,
      canDeleteAccount: true,
      deleteRequiresPassword: false,
    },
    data: {
      messageContentRetentionDays: 7,
      sessionRetentionDays: 30,
      reportRetentionDays: 30,
    },
    ui: {
      showHistoryMenu: false,
      showProfileEditing: false,
      showConsentReconfirmModal: false,
      showUpgradeModalOnLimit: true,
      showGuestModeBadge: true,
      showBetaBanner: true,
      showAdminEntryButton: false,
      showLandingChatEntry: true,
      showCommunicationStyleSection: false,
    },
    mediator: {
      styleSource: 'per_session',
      defaultStyleX: 50,
      defaultStyleY: 50,
    },
    admin: {
      canAccessDashboard: false,
      canViewAllUsers: false,
      canModifyFeedback: false,
      canAnonymizeUser: false,
      canViewCrisisMonitor: false,
      canViewSystemHealth: false,
    },
  },
  registered: {
    label: '회원',
    summary: '이메일 인증 또는 OAuth 로그인 회원. 전체 기능·장기 보존·Duo 초대 가능.',
    auth: {
      tokenExpirationSeconds: 86400,
      requiresEmailVerification: true,
      requiresOnboarding: true,
      canChangePassword: true,
    },
    sessions: {
      dailyLimit: 5,
      dailyLimitScope: 'user',
      messageTurnLimit: null,
      duoModeAllowed: true,
      canInvitePartner: true,
      canResumeOldSession: true,
    },
    profile: {
      canEditNickname: true,
      canEditEmail: false,
      canEditCommunicationStyle: true,
      canCompleteOnboarding: true,
      canSetMbti: true,
      canViewHistory: true,
      canDeleteAccount: true,
      deleteRequiresPassword: true,
    },
    data: {
      messageContentRetentionDays: 30,
      sessionRetentionDays: 180,
      reportRetentionDays: null,
    },
    ui: {
      showHistoryMenu: true,
      showProfileEditing: true,
      showConsentReconfirmModal: true,
      showUpgradeModalOnLimit: false,
      showGuestModeBadge: false,
      showBetaBanner: true,
      showAdminEntryButton: false,
      showLandingChatEntry: true,
      showCommunicationStyleSection: true,
    },
    mediator: {
      styleSource: 'per_session',
      defaultStyleX: 50,
      defaultStyleY: 50,
    },
    admin: {
      canAccessDashboard: false,
      canViewAllUsers: false,
      canModifyFeedback: false,
      canAnonymizeUser: false,
      canViewCrisisMonitor: false,
      canViewSystemHealth: false,
    },
  },
  admin: {
    label: '관리자',
    summary: '회원 권한 + 관리자 대시보드/감사 권한. 일반 사용자 흐름 모두 가능.',
    auth: {
      tokenExpirationSeconds: 86400,
      requiresEmailVerification: true,
      requiresOnboarding: false,
      canChangePassword: true,
    },
    sessions: {
      dailyLimit: 5,
      dailyLimitScope: 'user',
      messageTurnLimit: null,
      duoModeAllowed: true,
      canInvitePartner: true,
      canResumeOldSession: true,
    },
    profile: {
      canEditNickname: true,
      canEditEmail: false,
      canEditCommunicationStyle: true,
      canCompleteOnboarding: true,
      canSetMbti: true,
      canViewHistory: true,
      canDeleteAccount: true,
      deleteRequiresPassword: true,
    },
    data: {
      messageContentRetentionDays: 30,
      sessionRetentionDays: 180,
      reportRetentionDays: null,
    },
    ui: {
      showHistoryMenu: false,
      showProfileEditing: true,
      showConsentReconfirmModal: true,
      showUpgradeModalOnLimit: false,
      showGuestModeBadge: false,
      showBetaBanner: true,
      showAdminEntryButton: true,
      showLandingChatEntry: false,
      showCommunicationStyleSection: false,
    },
    mediator: {
      styleSource: 'per_session',
      defaultStyleX: 50,
      defaultStyleY: 50,
    },
    admin: {
      canAccessDashboard: true,
      canViewAllUsers: true,
      canModifyFeedback: true,
      canAnonymizeUser: true,
      canViewCrisisMonitor: true,
      canViewSystemHealth: true,
    },
  },
};

/** 사용자 객체로부터 적용 권한 반환 (admin > registered > guest 우선순위). */
export function permissionsFor(
  user: { isGuest?: boolean; roles?: string[] } | null | undefined,
): TierPermissions {
  if (!user) return USER_PERMISSIONS.guest;
  if (user.isGuest) return USER_PERMISSIONS.guest;
  if (user.roles?.includes('ADMIN')) return USER_PERMISSIONS.admin;
  return USER_PERMISSIONS.registered;
}

/** 사용자가 ADMIN 권한을 가지는지 빠르게 판별 */
export function isAdmin(user: { isGuest?: boolean; roles?: string[] } | null | undefined): boolean {
  return !!user && !user.isGuest && !!user.roles?.includes('ADMIN');
}
