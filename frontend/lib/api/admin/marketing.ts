import { api } from '@/lib/api/client';

// ===== Types =====

export interface MarketingJob {
  id: number;
  remoteJobId: string | null;
  postId: string;
  status: string; // REQUESTED|QUEUED|RUNNING|READY|PUBLISHING|PUBLISHED|FAILED|STALE
  phase: string | null;
  progress: number;
  targets: string[];
  autoPublish: boolean;
  artifacts: Record<string, string> | null; // { video_mp4, thumbnail, blog_md, ... }
  publications: Array<{ platform: string; state: string; url: string }> | null;
  errorMessage: string | null;
  pollFailCount: number;
  lastPolledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMarketingJobRequest {
  postId: string;
  targets: string[];
  autoPublish: boolean;
}

// ===== API Functions =====

/**
 * @deprecated Manual admin job creation is being removed (holding board + scheduler).
 * Keep the client until another agent removes remaining UI callers; do not use in new UI.
 */
export async function createMarketingJob(
  postId: string,
  targets: string[],
  autoPublish: boolean
): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>('/api/admin/marketing/jobs', {
    postId,
    targets,
    autoPublish,
  });
  return res.data;
}

export async function listMarketingJobs(): Promise<MarketingJob[]> {
  const res = await api.get<MarketingJob[]>('/api/admin/marketing/jobs');
  return res.data;
}

export async function getMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.get<MarketingJob>(`/api/admin/marketing/jobs/${id}`);
  return res.data;
}

export async function publishMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>(`/api/admin/marketing/jobs/${id}/publish`);
  return res.data;
}

export async function republishMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>(`/api/admin/marketing/jobs/${id}/republish`);
  return res.data;
}

/**
 * Upload/replace a custom thumbnail for a job's YouTube Shorts / Instagram
 * Reels artifact. `platform` must be 'youtube_shorts' or 'instagram_reels'.
 */
export async function uploadJobThumbnail(
  id: number,
  platform: string,
  file: File
): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  // The shared `api` client hardcodes Content-Type: application/json — must
  // override here or the multipart boundary never gets attached and the
  // backend rejects the request.
  await api.put(`/api/admin/marketing/jobs/${id}/artifacts/${platform}/thumbnail`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// ===== Platform credentials =====
// NOTE: this payload is proxied verbatim from ASM (FastAPI) → snake_case keys,
// unlike the camelCase MarketingJob above. ASM is the single source of truth for
// the per-platform `fields` schema; secrets are never returned (only secret_set).

export interface CredentialFieldSpec {
  key: string;
  secret: boolean;
  required: boolean;
}

export interface PlatformCredentialStatus {
  platform: string;
  fields: CredentialFieldSpec[];
  configured: boolean;
  values: Record<string, string>; // public (non-secret) values only
  secret_set: Record<string, boolean>; // secret key -> whether a value is stored
  updated_at: string | null;
}

export async function listPlatformCredentials(): Promise<PlatformCredentialStatus[]> {
  const res = await api.get<PlatformCredentialStatus[]>('/api/admin/marketing/credentials');
  return res.data;
}

export async function upsertPlatformCredential(
  platform: string,
  values: Record<string, string>
): Promise<PlatformCredentialStatus> {
  const res = await api.put<PlatformCredentialStatus>(
    `/api/admin/marketing/credentials/${platform}`,
    { values }
  );
  return res.data;
}

export async function deletePlatformCredential(platform: string): Promise<void> {
  await api.delete(`/api/admin/marketing/credentials/${platform}`);
}

// ===== YouTube Shorts OAuth 2.0 =====

/**
 * OAuth start — Google 인증 URL 생성.
 * redirectUri: 팝업이 리다이렉트될 콜백 URL (등록된 허용 호스트여야 함).
 */
export async function startYoutubeOauth(redirectUri: string): Promise<{ auth_url: string }> {
  const res = await api.post<{ auth_url: string }>(
    '/api/admin/marketing/credentials/youtube_shorts/oauth/start',
    { redirect_uri: redirectUri }
  );
  return res.data;
}

/**
 * OAuth exchange — authorization code → refresh_token 저장.
 * code, state: 콜백 URL 쿼리파라미터에서 추출.
 */
export async function exchangeYoutubeOauth(
  code: string,
  state: string
): Promise<PlatformCredentialStatus> {
  const res = await api.post<PlatformCredentialStatus>(
    '/api/admin/marketing/credentials/youtube_shorts/oauth/exchange',
    { code, state }
  );
  return res.data;
}

// ===== WaggleBot TTS voices (platform account editor) =====

export interface TtsVoice {
  key: string;
  label: string;
  gender?: string;
  age_range?: number[];
  sampleUrl?: string | null;
  hasSample?: boolean;
}

export interface TtsVoiceCatalog {
  defaultVoice: string;
  voices: TtsVoice[];
}

export async function listTtsVoices(): Promise<TtsVoiceCatalog> {
  const res = await api.get<TtsVoiceCatalog>('/api/admin/marketing/tts/voices');
  return res.data;
}

/**
 * Fetch sample audio bytes with admin auth (for audio preview via blob URL).
 * {@code samplePath} is the WaggleBot media path from the catalog (`/api/media/voices/...`).
 */
export async function fetchTtsVoiceSampleBlob(samplePath: string): Promise<Blob> {
  const res = await api.get<Blob>('/api/admin/marketing/tts/voice-sample', {
    params: { path: samplePath },
    responseType: 'blob',
  });
  return res.data;
}

// ===== Marketing Analytics =====

export interface PlatformStatsDto {
  platform: string;
  attempted: number;
  published: number;
  failed: number;
  successRate: number;
  lastPublishedUrl: string | null;
  lastPublishedAt: string | null;
}

export async function getMarketingPerformance(days?: number): Promise<PlatformStatsDto[]> {
  const params = new URLSearchParams();
  if (days !== undefined) params.append('days', String(days));
  const res = await api.get<PlatformStatsDto[]>(
    `/api/admin/marketing/performance${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}

// ===== Publication Timeline =====

export interface TimelineEventDto {
  jobId: number;
  postId: string;
  platform: string;
  url: string | null;
  state: string;
  publishedAt: string | null;
}

export async function getPublicationTimeline(limit?: number): Promise<TimelineEventDto[]> {
  const params = new URLSearchParams();
  if (limit !== undefined) params.append('limit', String(limit));
  const res = await api.get<TimelineEventDto[]>(
    `/api/admin/marketing/timeline${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}

// ===== Job Traffic =====

export interface JobTrafficDto {
  jobId: number;
  visits: number;
  uniqueSessions: number;
  bySources: Array<{ source: string; visits: number }>;
}

export async function getJobTraffic(id: number): Promise<JobTrafficDto> {
  const res = await api.get<JobTrafficDto>(`/api/admin/marketing/jobs/${id}/traffic`);
  return res.data;
}

// ===== Daily auto-publish quota =====

export interface MarketingQuota {
  dailyTextCap: number;
  dailyVideoCap: number;
  videosToday: number;
  textsToday: number;
  remainingPool: number;
}

export async function getMarketingQuota(): Promise<MarketingQuota> {
  const res = await api.get<MarketingQuota>('/api/admin/marketing/quota');
  return res.data;
}

export async function updateMarketingQuota(
  dailyTextCap: number,
  dailyVideoCap: number
): Promise<MarketingQuota> {
  const res = await api.put<MarketingQuota>('/api/admin/marketing/quota', {
    dailyTextCap,
    dailyVideoCap,
  });
  return res.data;
}

// ===== Admin Posts for Picker (별도 타입 — content.ts의 AdminPost와 구분) =====

export interface PickerPost {
  id: string;
  title: string;
  authorNickname: string | null;
  voteCount: number;
  commentCount: number;
  createdAt: string;
}

export async function listAdminPostsForPicker(page?: number): Promise<PickerPost[]> {
  const params = new URLSearchParams();
  if (page !== undefined) params.append('page', String(page));
  params.append('size', '20');
  const res = await api.get<PickerPost[]>(
    `/api/admin/content/posts${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}

// ===== Score weights (marketing redesign) =====

export interface MarketingScoreWeights {
  weightViews: number;
  weightComments: number;
  weightVotes: number;
}

export async function getMarketingScoreWeights(): Promise<MarketingScoreWeights> {
  const res = await api.get<MarketingScoreWeights>('/api/admin/marketing/score-weights');
  return res.data;
}

export async function updateMarketingScoreWeights(
  weights: MarketingScoreWeights
): Promise<MarketingScoreWeights> {
  const res = await api.put<MarketingScoreWeights>('/api/admin/marketing/score-weights', weights);
  return res.data;
}

// ===== Holding board (marketing redesign) =====

export type MarketingHoldingStatus =
  | 'IN_POOL'
  | 'PINNED'
  | 'OUT_OF_CUT'
  | 'COMMITTED'
  | 'DROPPED';

export type MarketingPinFormat = 'VIDEO' | 'TEXT';

/** Projected slot format for display (cutline-relative). */
export type MarketingProjectedFormat = 'VIDEO' | 'TEXT' | 'OUT_OF_CUT';

export interface MarketingHoldingTopComment {
  author?: string | null;
  authorId?: string | null;
  body?: string | null;
  likeCount?: number | null;
  createdAt?: string | null;
  side?: string | null;
}

/**
 * Unified marketing draft (BriefDto superspace). Stored as draft_json on BE;
 * admin API uses camelCase like other Marketing* DTOs.
 */
export interface MarketingHoldingDraft {
  title?: string | null;
  promoTitle?: string | null;
  neutralSummary?: string | null;
  authorBody?: string | null;
  partnerBody?: string | null;
  sideA?: string | null;
  sideB?: string | null;
  tags?: string[] | null;
  topComments?: MarketingHoldingTopComment[] | null;
  firstComment?: string | null;
  juryGist?: string | null;
  juryOpinions?: string[] | null;
  empathyRatio?: { a: number; b: number } | null;
  metaphorId?: string | null;
  postUrl?: string | null;
  voteLabels?: Record<string, number> | null;
}

export interface MarketingHoldingRow {
  postId: string;
  title: string | null;
  status: MarketingHoldingStatus;
  pinFormat: MarketingPinFormat | null;
  /** Live / last projected weighted score. */
  scoreSnapshot: number;
  /** 1-based projected rank on the board (null if unranked). */
  rankSnapshot: number | null;
  viewCount: number;
  commentCount: number;
  voteCount: number;
  projectedFormat: MarketingProjectedFormat;
  draft: MarketingHoldingDraft | null;
  lockedAt: string | null;
  postCreatedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface MarketingHoldingMeta {
  remainingPool: number;
  /** Auto cutline N (= remaining shared pool). */
  cutline: number;
  dailyTextCap: number;
  dailyVideoCap: number;
  videosToday?: number;
  textsToday?: number;
  weights: MarketingScoreWeights;
}

export interface MarketingHoldingBoard {
  items: MarketingHoldingRow[];
  meta: MarketingHoldingMeta;
}

/** Raw BE board payload (cutlineN + flat weights; item counts optional). */
type MarketingHoldingRowRaw = Partial<MarketingHoldingRow> & {
  postId: string;
  draft?: MarketingHoldingDraft | Record<string, unknown> | null;
};

interface MarketingHoldingBoardRaw {
  items?: MarketingHoldingRowRaw[];
  meta?: {
    remainingPool?: number;
    cutline?: number;
    cutlineN?: number;
    dailyTextCap?: number;
    dailyVideoCap?: number;
    videosToday?: number;
    textsToday?: number;
    weightViews?: number;
    weightComments?: number;
    weightVotes?: number;
    weights?: MarketingScoreWeights;
  };
}

function normalizeHoldingDraft(
  draft: MarketingHoldingDraft | Record<string, unknown> | null | undefined
): MarketingHoldingDraft | null {
  if (!draft || typeof draft !== 'object') return null;
  const d = draft as Record<string, unknown>;
  // Accept snake_case brief fields from draft_json.
  return {
    title: (d.title as string) ?? null,
    promoTitle: (d.promoTitle as string) ?? (d.promo_title as string) ?? null,
    neutralSummary:
      (d.neutralSummary as string) ?? (d.neutral_summary as string) ?? null,
    authorBody: (d.authorBody as string) ?? (d.author_body as string) ?? null,
    partnerBody: (d.partnerBody as string) ?? (d.partner_body as string) ?? null,
    sideA: (d.sideA as string) ?? (d.side_a as string) ?? null,
    sideB: (d.sideB as string) ?? (d.side_b as string) ?? null,
    tags: (d.tags as string[]) ?? null,
    topComments:
      (d.topComments as MarketingHoldingTopComment[]) ??
      (d.top_comments as MarketingHoldingTopComment[]) ??
      null,
    firstComment:
      (d.firstComment as string) ?? (d.first_comment as string) ?? null,
    juryGist: (d.juryGist as string) ?? (d.jury_gist as string) ?? null,
    juryOpinions:
      (d.juryOpinions as string[]) ?? (d.jury_opinions as string[]) ?? null,
    metaphorId: (d.metaphorId as string) ?? (d.metaphor_id as string) ?? null,
    postUrl: (d.postUrl as string) ?? (d.post_url as string) ?? null,
    voteLabels:
      (d.voteLabels as Record<string, number>) ??
      (d.vote_labels as Record<string, number>) ??
      null,
  };
}

function normalizeProjectedFormat(
  raw: string | null | undefined
): MarketingProjectedFormat {
  if (raw === 'VIDEO' || raw === 'TEXT' || raw === 'OUT_OF_CUT') return raw;
  if (raw === 'OUT') return 'OUT_OF_CUT';
  return 'OUT_OF_CUT';
}

function normalizeHoldingRow(raw: MarketingHoldingRowRaw): MarketingHoldingRow {
  return {
    postId: raw.postId,
    title: raw.title ?? null,
    status: (raw.status ?? 'IN_POOL') as MarketingHoldingStatus,
    pinFormat: (raw.pinFormat as MarketingPinFormat | null) ?? null,
    scoreSnapshot: Number(raw.scoreSnapshot ?? 0),
    rankSnapshot: raw.rankSnapshot ?? null,
    viewCount: Number(raw.viewCount ?? 0),
    commentCount: Number(raw.commentCount ?? 0),
    voteCount: Number(raw.voteCount ?? 0),
    projectedFormat: normalizeProjectedFormat(raw.projectedFormat),
    draft: normalizeHoldingDraft(raw.draft),
    lockedAt: raw.lockedAt ?? null,
    postCreatedAt: String(raw.postCreatedAt ?? ''),
    createdAt: String(raw.createdAt ?? ''),
    updatedAt: String(raw.updatedAt ?? ''),
  };
}

export async function getMarketingHoldingBoard(): Promise<MarketingHoldingBoard> {
  const res = await api.get<MarketingHoldingBoardRaw>('/api/admin/marketing/holding');
  const raw = res.data;
  const meta = raw.meta ?? {};
  const weights = meta.weights ?? {
    weightViews: meta.weightViews ?? 0.1,
    weightComments: meta.weightComments ?? 1,
    weightVotes: meta.weightVotes ?? 0.5,
  };
  return {
    items: (raw.items ?? []).map(normalizeHoldingRow),
    meta: {
      remainingPool: meta.remainingPool ?? 0,
      cutline: meta.cutline ?? meta.cutlineN ?? 0,
      dailyTextCap: meta.dailyTextCap ?? 6,
      dailyVideoCap: meta.dailyVideoCap ?? 3,
      videosToday: meta.videosToday,
      textsToday: meta.textsToday,
      weights,
    },
  };
}

export async function updateMarketingHoldingDraft(
  postId: string,
  draft: MarketingHoldingDraft
): Promise<MarketingHoldingRow> {
  const res = await api.patch<MarketingHoldingRowRaw>(
    `/api/admin/marketing/holding/${postId}/draft`,
    { draft }
  );
  return normalizeHoldingRow(res.data);
}

export async function pinMarketingHolding(
  postId: string,
  format: MarketingPinFormat
): Promise<MarketingHoldingRow> {
  const res = await api.post<MarketingHoldingRowRaw>(
    `/api/admin/marketing/holding/${postId}/pin`,
    { format }
  );
  return normalizeHoldingRow(res.data);
}

export async function unpinMarketingHolding(
  postId: string
): Promise<MarketingHoldingRow> {
  const res = await api.delete<MarketingHoldingRowRaw>(
    `/api/admin/marketing/holding/${postId}/pin`
  );
  return normalizeHoldingRow(res.data);
}

// ===== Platform auto on/off (marketing redesign) =====

export interface MarketingPlatformAuto {
  platform: string;
  autoEnabled: boolean;
  runtimeSupported: boolean;
  /** Set when enabling an unsupported platform (PUT may still succeed). */
  warning?: string | null;
}

export async function listMarketingPlatforms(): Promise<MarketingPlatformAuto[]> {
  const res = await api.get<MarketingPlatformAuto[]>('/api/admin/marketing/platforms');
  return res.data;
}

export async function updateMarketingPlatformAuto(
  platform: string,
  enabled: boolean
): Promise<MarketingPlatformAuto> {
  const res = await api.put<MarketingPlatformAuto>(
    `/api/admin/marketing/platforms/${platform}/auto`,
    { enabled }
  );
  return res.data;
}

// ===== Completed holdings + force (S4) =====

export type MarketingForceMode = 'VIDEO_AND_TEXT' | 'TEXT_ONLY';

/** Per-platform publication outcome for a job (완료 탭 redesign — additive, optional). */
export interface MarketingCompletedPublication {
  platform: string;
  state: string;
  url?: string | null;
}

export interface MarketingCompletedJobSummary {
  id: number;
  status: string;
  targets: string[];
  createdAt: string | null;
  /**
   * Additive (완료 탭 redesign): per-platform publication states/urls for this job.
   * Optional — BE may not populate yet; UI must handle undefined/empty.
   */
  publications?: MarketingCompletedPublication[];
}

export interface MarketingCompletedItem {
  postId: string;
  status: MarketingHoldingStatus;
  pinFormat: MarketingPinFormat | null;
  scoreSnapshot: number | null;
  lockedAt: string | null;
  createdAt: string;
  updatedAt: string;
  jobs: MarketingCompletedJobSummary[];
  /**
   * Additive (완료 탭 redesign): sourced from the holding row's draft title.
   * Optional — BE may not populate yet; UI must fall back to postId.
   */
  title?: string | null;
  /**
   * Additive (완료 탭 redesign): actual committed/forced format ('VIDEO' | 'TEXT'),
   * distinct from `pinFormat` (which only reflects an explicit pin, not the projected
   * or force-deployed format). Optional — UI should fall back to `pinFormat` if absent.
   * BE JSON field name is `committedFormat`; normalized to `format` below.
   */
  format?: string | null;
}

type MarketingCompletedItemRaw = Omit<MarketingCompletedItem, 'format'> & {
  committedFormat?: string | null;
  format?: string | null;
};

function normalizeCompletedItem(raw: MarketingCompletedItemRaw): MarketingCompletedItem {
  return {
    ...raw,
    format: raw.format ?? raw.committedFormat ?? null,
    jobs: (raw.jobs ?? []).map((job) => ({
      ...job,
      publications: job.publications ?? [],
    })),
  };
}

export async function listMarketingCompleted(params?: {
  status?: MarketingHoldingStatus;
  limit?: number;
}): Promise<MarketingCompletedItem[]> {
  const q = new URLSearchParams();
  if (params?.status) q.append('status', params.status);
  if (params?.limit != null) q.append('limit', String(params.limit));
  const res = await api.get<{ items: MarketingCompletedItemRaw[] }>(
    `/api/admin/marketing/completed${q.size > 0 ? `?${q}` : ''}`
  );
  return (res.data.items ?? []).map(normalizeCompletedItem);
}

export async function forceMarketingCompleted(
  postId: string,
  mode: MarketingForceMode
): Promise<{
  postId: string;
  status: MarketingHoldingStatus;
  format: string;
  jobIds: number[];
  targets: string[];
}> {
  const res = await api.post(`/api/admin/marketing/completed/${postId}/force`, {
    mode,
  });
  return res.data;
}

/** Redesign note aliases (S6 wiring). Prefer the Marketing* names above. */
export const getScoreWeights = getMarketingScoreWeights;
export const updateScoreWeights = updateMarketingScoreWeights;
export const listMarketingHoldings = getMarketingHoldingBoard;
