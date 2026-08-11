import { api } from '../client';

export interface InviteResponse {
  inviteToken: string;
  inviteUrl: string;
}

/** 상대 슬롯 상태 */
export type PartnerState = 'NONE' | 'ACTIVE' | 'TOMBSTONE';

/**
 * 초대 토큰 기준 소유권.
 * - UNOWNED: 게스트 / partner_ 접두 id / 미연결
 * - OWNED: 요청자가 소유 회원
 * - OWNED_BY_OTHER: 다른 회원이 연결됨
 * - AUTHOR: 요청자가 작성자 (상대 슬롯 사용 불가)
 */
export type InviteOwnership = 'UNOWNED' | 'OWNED' | 'OWNED_BY_OTHER' | 'AUTHOR';

export interface InvitePreview {
  postId: string;
  userTitle: string;
  authorBodyPublished?: string | null;
  category: string;
  /** 포스트 soft-delete — FE는 삭제 페이지 */
  deleted?: boolean;
  partnerState?: PartnerState;
  ownership?: InviteOwnership;
  partnerBodyPublished?: string | null;
  canWrite?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  canClaim?: boolean;
}

/** 레거시 응답(필드 누락)을 상태 머신용으로 정규화 */
export function normalizeInvitePreview(data: InvitePreview): Required<
  Pick<
    InvitePreview,
    | 'deleted'
    | 'partnerState'
    | 'ownership'
    | 'canWrite'
    | 'canEdit'
    | 'canDelete'
    | 'canClaim'
  >
> & InvitePreview {
  const partnerState = data.partnerState ?? 'NONE';
  return {
    ...data,
    deleted: data.deleted ?? false,
    partnerState,
    ownership: data.ownership ?? 'UNOWNED',
    canWrite: data.canWrite ?? (partnerState === 'NONE' || partnerState === 'TOMBSTONE'),
    canEdit: data.canEdit ?? false,
    canDelete: data.canDelete ?? false,
    canClaim: data.canClaim ?? false,
  };
}

export interface SubmitAnswerRequest {
  userTitle?: string;
  bodyRaw: string;
}

export interface PatchAnswerRequest {
  bodyRaw: string;
}

export interface PublishModeRequest {
  mode: string;
  voteDurationHours: number;
}

export const DRAFT_KEY_PREFIX = 'invite-draft:';

export function inviteDraftKey(token: string): string {
  return `${DRAFT_KEY_PREFIX}${token}`;
}

export function saveInviteDraft(token: string, bodyRaw: string): void {
  if (typeof window === 'undefined') return;
  try {
    sessionStorage.setItem(inviteDraftKey(token), bodyRaw);
  } catch {
    /* sessionStorage 차단 환경 */
  }
}

export function loadInviteDraft(token: string): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return sessionStorage.getItem(inviteDraftKey(token));
  } catch {
    return null;
  }
}

export function clearInviteDraft(token: string): void {
  if (typeof window === 'undefined') return;
  try {
    sessionStorage.removeItem(inviteDraftKey(token));
  } catch {
    /* noop */
  }
}

/** Axios/API 오류 → 한국어 메시지 */
export function inviteApiErrorMessage(err: unknown, fallback: string): string {
  const ax = err as {
    response?: {
      status?: number;
      data?: { error?: { code?: string; message?: string }; message?: string };
    };
  };
  const status = ax.response?.status;
  const code = ax.response?.data?.error?.code;
  const msg = ax.response?.data?.error?.message || ax.response?.data?.message;

  if (code === 'AUTHOR_CANNOT_BE_PARTNER') {
    return '작성자는 상대방으로 답할 수 없습니다.';
  }
  if (status === 409) {
    return msg || '이미 답변이 등록되었거나 다른 계정이 연결했습니다.';
  }
  if (status === 403) {
    return msg || '이 작업을 할 권한이 없습니다.';
  }
  if (status === 404) {
    return msg || '초대 링크를 찾을 수 없습니다.';
  }
  if (status === 410) {
    return '삭제된 게시글입니다.';
  }
  return msg || fallback;
}

export const postInviteApi = {
  createInvite: (postId: string) =>
    api.post<InviteResponse>(`/api/community/posts/${postId}/invite`).then(r => r.data),

  getByToken: (token: string) =>
    api.get<InvitePreview>(`/api/s/${token}`).then(r => r.data),

  submitAnswer: (token: string, req: SubmitAnswerRequest) =>
    api.post(`/api/s/${token}/answer`, req),

  /** 미연결(unowned) 상대 글을 로그인 회원 계정에 연결 */
  claim: (token: string) =>
    api.post(`/api/s/${token}/claim`),

  /** 상대 본문 수정 (unowned=토큰, owned=소유 JWT) */
  patchAnswer: (token: string, req: PatchAnswerRequest) =>
    api.patch(`/api/s/${token}/answer`, req),

  /** 상대 본문 삭제 → tombstone (양쪽 tombstone이면 완전 삭제는 BE) */
  deleteAnswer: (token: string) =>
    api.delete(`/api/s/${token}/answer`),

  /** voteDurationHours deprecated — 서버에서 무시(시한부 투표 제거). 호환용 optional. */
  setPublishMode: (postId: string, mode: string, voteDurationHours?: number) =>
    api.patch(`/api/community/posts/${postId}/publish-mode`, { mode, voteDurationHours }),

  publishNow: (postId: string) =>
    api.post(`/api/community/posts/${postId}/publish-now`),
};
