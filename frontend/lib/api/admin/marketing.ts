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
  renderProfile?: string | null; // 'marketing_v2'|'marketing_fast'|null
  artifacts: Record<string, string> | null; // { video_mp4, thumbnail, blog_md, ... }
  publications: Array<{ platform: string; state: string; url: string }> | null;
  errorMessage: string | null;
  failureCode?: string | null;
  failureStage?: string | null;
  retryable?: boolean | null;
  errorSummary?: string | null;
  generationDiagnostics?: Record<string, unknown> | null;
  actualDurationMs?: number | null;
  retryOfJobId?: number | null;
  generationAttempt?: number | null;
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

/**
 * Create a render-only test job (테스트 탭 전용). Always autoPublish=false — this
 * never posts to a real platform, only renders artifacts (video/cards/etc.) for
 * preview via {@link ArtifactSection}. Distinct from the deprecated
 * {@link createMarketingJob} (manual *publish* job creation being removed).
 *
 * @param renderProfile - Optional profile hint ('marketing_v2'|'marketing_fast').
 *   If not provided, backend uses its default. Sent only when explicitly specified.
 *   Backend may not yet support this field, so graceful null/undefined handling required.
 */
export async function createMarketingTestJob(
  postId: string,
  targets: string[],
  renderProfile?: string | null
): Promise<MarketingJob> {
  const body: { postId: string; targets: string[]; autoPublish: boolean; renderProfile?: string | null } = {
    postId,
    targets,
    autoPublish: false,
  };
  // renderProfile이 있을 때만 본문에 포함 (백엔드가 아직 이 필드를 안 받을 수 있으므로)
  if (renderProfile != null) {
    body.renderProfile = renderProfile;
  }
  const res = await api.post<MarketingJob>('/api/admin/marketing/jobs', body);
  return res.data;
}

/**
 * Resolve renderProfile from a MarketingJob, falling back to 'marketing_fast'
 * if not provided or null. This handles the case where the backend doesn't yet
 * populate the field in responses.
 */
export function resolveRenderProfile(job: MarketingJob | null | undefined): string {
  if (!job) return 'marketing_fast';
  return job.renderProfile ?? 'marketing_fast';
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

export async function regenerateMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>(`/api/admin/marketing/jobs/${id}/regenerate`);
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

// ===== WaggleBot BGM tracks (background music editor) =====

export interface BgmTrack {
  emotion: string;
  file: string;
  path: string;
  durationSec?: number;
}

export interface BgmCatalog {
  tracks: BgmTrack[];
}

export async function listBgmTracks(): Promise<BgmCatalog> {
  const res = await api.get<BgmCatalog>('/api/admin/marketing/bgm/tracks');
  return res.data;
}

/**
 * Fetch sample audio bytes with admin auth (for audio preview via blob URL).
 * {@code samplePath} is the WaggleBot BGM media path from the catalog (`/api/media/bgm/...`).
 */
export async function fetchBgmSampleBlob(samplePath: string): Promise<Blob> {
  const res = await api.get<Blob>('/api/admin/marketing/bgm/sample', {
    params: { path: samplePath },
    responseType: 'blob',
  });
  return res.data;
}

/**
 * 효과음 미리듣기 바이트. `path` 는 매핑 카탈로그가 준 라이브러리 상대경로다
 * (`_library/click/click_1109.wav` 또는 `hook_in.wav`).
 * 소리를 들어보지 않고는 고를 수 없으므로 매핑 화면의 핵심 기능이다.
 */
export async function fetchSfxSampleBlob(path: string): Promise<Blob> {
  const res = await api.get<Blob>('/api/admin/marketing/sfx/sample', {
    params: { path },
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

// ===== Daily auto-publish quota (Phase 2 per-platform) =====

export interface PlatformQuota {
  cap: number;
  usedToday: number;
  remaining: number;
}

export interface MarketingQuota {
  /** @deprecated Phase 1 — sum of text platform caps */
  dailyTextCap: number;
  /** @deprecated Phase 1 — sum of video platform caps */
  dailyVideoCap: number;
  videosToday: number;
  textsToday: number;
  remainingPool: number;
  platforms?: Record<string, PlatformQuota>;
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

export async function updateMarketingPlatformQuota(caps: {
  xThread?: number;
  instagramFeed?: number;
  instagramReels?: number;
  youtubeShorts?: number;
}): Promise<MarketingQuota> {
  const res = await api.put<MarketingQuota>('/api/admin/marketing/quota', caps);
  return res.data;
}

// ===== Admin Posts for Picker (별도 타입 — content.ts의 AdminPost와 구분) =====

export interface PickerPost {
  id: string;
  title: string;
  category: string | null;
  commentCount: number;
  synthetic: boolean;
  createdAt: string;
}

interface AdminPostViewRaw {
  id: string;
  title: string | null;
  category?: string | null;
  commentCount?: number;
  synthetic?: boolean;
  createdAt: string;
}

/**
 * GET /api/admin/content/posts?page=&size= — 최근 순 페이지네이션.
 * 응답은 Spring `Page<AdminPostView>`(래핑 객체)이므로 `content`를 풀어 PickerPost로 매핑한다.
 */
export async function listAdminPostsForPicker(page = 0, size = 20): Promise<PickerPost[]> {
  const params = new URLSearchParams();
  params.append('page', String(page));
  params.append('size', String(size));
  const res = await api.get<{ content: AdminPostViewRaw[] }>(
    `/api/admin/content/posts?${params.toString()}`
  );
  return (res.data.content ?? []).map((p) => ({
    id: p.id,
    title: p.title ?? '(제목 없음)',
    category: p.category ?? null,
    commentCount: p.commentCount ?? 0,
    synthetic: p.synthetic ?? false,
    createdAt: p.createdAt,
  }));
}

// ===== Score weights (marketing redesign) =====

export interface MarketingScoreWeights {
  weightViews: number;
  weightComments: number;
  weightVotes: number;
  platforms?: Record<string, Record<string, number>>;
  /** Phase 2.7 — weekly nudge from platform stats. Default false. */
  autoAdjust?: boolean;
}

export async function getMarketingScoreWeights(): Promise<MarketingScoreWeights> {
  const res = await api.get<MarketingScoreWeights>('/api/admin/marketing/score-weights');
  return res.data;
}

export async function updateMarketingScoreWeights(
  weights: Partial<MarketingScoreWeights> & {
    weightViews?: number;
    weightComments?: number;
    weightVotes?: number;
  }
): Promise<MarketingScoreWeights> {
  const res = await api.put<MarketingScoreWeights>('/api/admin/marketing/score-weights', weights);
  return res.data;
}

// ===== Phase 2.6–2.7 platform stats + weekly report =====

export interface MarketingStatsCollectSummary {
  requested: number;
  stored: number;
  partial: number;
  errors: number;
}

export interface MarketingStatsCollectRun {
  runId: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt?: string;
  finishedAt?: string;
  error?: string;
  summary?: MarketingStatsCollectSummary;
}

/** Start async collect and poll until done (avoids CF/nginx 60s proxy kill). */
export async function collectMarketingPlatformStats(opts?: {
  jobIds?: number[];
  lookbackDays?: number;
  limit?: number;
  onTick?: (elapsedSec: number, status: string) => void;
}): Promise<MarketingStatsCollectSummary> {
  const params = new URLSearchParams();
  if (opts?.lookbackDays != null) params.set('lookbackDays', String(opts.lookbackDays));
  if (opts?.limit != null) params.set('limit', String(opts.limit));
  if (opts?.jobIds?.length) {
    for (const id of opts.jobIds) params.append('jobIds', String(id));
  }
  const qs = params.size > 0 ? `?${params}` : '';
  const started = await api.post<MarketingStatsCollectRun>(
    `/api/admin/marketing/stats/collect${qs}`,
    null,
    { timeout: 30_000 }
  );
  const runId = started.data.runId;
  if (!runId) {
    throw new Error('collect runId missing');
  }
  const t0 = Date.now();
  const deadline = t0 + 300_000;
  while (Date.now() < deadline) {
    const elapsedSec = Math.floor((Date.now() - t0) / 1000);
    opts?.onTick?.(elapsedSec, 'RUNNING');
    await new Promise((r) => setTimeout(r, 2000));
    const poll = await api.get<MarketingStatsCollectRun>(
      `/api/admin/marketing/stats/collect/${runId}`,
      { timeout: 15_000 }
    );
    const data = poll.data;
    opts?.onTick?.(Math.floor((Date.now() - t0) / 1000), data.status);
    if (data.status === 'COMPLETED') {
      if (!data.summary) {
        throw new Error('collect completed without summary');
      }
      return data.summary;
    }
    if (data.status === 'FAILED') {
      throw new Error(data.error || 'collect failed');
    }
  }
  throw new Error('collect timed out (5분). 잠시 후 다시 시도하세요.');
}

export interface MarketingWeeklyReport {
  weekStart: string;
  weekEnd: string;
  topStories: Array<{
    postId: string;
    title: string | null;
    hookEmotion: string | null;
    category: string | null;
    score: number;
    views: number;
    likes: number;
    comments: number;
    utmVisits: number;
    platforms: string[];
  }>;
  bottomStories: Array<{
    postId: string;
    title: string | null;
    hookEmotion: string | null;
    category: string | null;
    score: number;
    views: number;
    likes: number;
    comments: number;
    utmVisits: number;
    platforms: string[];
  }>;
  byEmotion: Array<{
    emotion: string;
    stories: number;
    views: number;
    comments: number;
    avgScore: number;
  }>;
  byCategory: Array<{
    category: string;
    stories: number;
    views: number;
    utmVisits: number;
    avgScore: number;
  }>;
  utmInflow: {
    visits: number;
    uniqueSessions: number;
    bySource: Array<{ source: string; visits: number }>;
  };
  snapshotRows: number;
  storyCount: number;
}

export async function getMarketingWeeklyReport(weeksAgo = 0): Promise<MarketingWeeklyReport> {
  const res = await api.get<MarketingWeeklyReport>(
    `/api/admin/marketing/weekly-report?weeksAgo=${weeksAgo}`
  );
  return res.data;
}

// ===== Phase 3 stats dashboard (Sprint 3.1) =====

export const MARKETING_STATS_PLATFORMS = [
  'x_thread',
  'instagram_feed',
  'instagram_reels',
  'youtube_shorts',
] as const;

export type MarketingStatsPlatform = (typeof MARKETING_STATS_PLATFORMS)[number];

export const MARKETING_STATS_PLATFORM_LABELS: Record<MarketingStatsPlatform, string> = {
  x_thread: 'X 스레드',
  instagram_feed: '인스타 피드',
  instagram_reels: '인스타 릴스',
  youtube_shorts: 'YouTube Shorts',
};

/** Default primary metric per platform (plan S5). */
export const MARKETING_STATS_DEFAULT_METRICS: Record<MarketingStatsPlatform, string> = {
  x_thread: 'impressions',
  instagram_feed: 'reach',
  // Graph API v21 dropped plays for reels; reach is what the collector returns.
  instagram_reels: 'reach',
  youtube_shorts: 'views',
};

export type MarketingStatsChannelStatus = 'ok' | 'partial' | 'error' | 'unknown' | string;

export interface MarketingStatsSeriesPoint {
  day: string;
  value: number;
}

export interface MarketingStatsPlatformKpi {
  platform: string;
  /** Selected / default primary metric key. */
  primaryMetric: string;
  /** Current-window aggregate for primary metric. */
  value: number;
  /** Previous-window aggregate. */
  prevValue: number;
  /** Week-over-week % change (null if prev is 0 / undefined). */
  deltaPct: number | null;
  series: MarketingStatsSeriesPoint[];
  /** Optional alias fields some BE drafts may use. */
  kpi?: number;
  delta?: number | null;
}

export interface MarketingStatsUtmSummary {
  visits: number;
  uniqueSessions: number;
  bySource: Array<{ source: string; visits: number }>;
}

export interface MarketingStatsChannelHealth {
  platform: string;
  status: MarketingStatsChannelStatus;
  message?: string | null;
}

export interface MarketingStatsHealth {
  lastCollectAt: string | null;
  partialCount: number;
  errorCount: number;
  channels: MarketingStatsChannelHealth[];
}

export interface MarketingStatsUnknownCounts {
  missingEmotion: number;
  missingCategory: number;
}

export interface MarketingStatsDashboard {
  weekStart: string;
  weekEnd: string;
  prevWeekStart: string;
  prevWeekEnd: string;
  platforms: MarketingStatsPlatformKpi[];
  utm: MarketingStatsUtmSummary;
  health: MarketingStatsHealth;
  unknownCounts: MarketingStatsUnknownCounts;
  /** Soft hints for todo strip (sibling may enrich). */
  todoHints: string[];
  todoStrip?: unknown;
}

export interface MarketingStatsDashboardParams {
  platform?: string;
  weeksAgo?: number;
  rangeDays?: number;
  primaryMetric?: string;
}

function emptyStatsDashboard(): MarketingStatsDashboard {
  return {
    weekStart: '',
    weekEnd: '',
    prevWeekStart: '',
    prevWeekEnd: '',
    platforms: MARKETING_STATS_PLATFORMS.map((platform) => ({
      platform,
      primaryMetric: MARKETING_STATS_DEFAULT_METRICS[platform],
      value: 0,
      prevValue: 0,
      deltaPct: null,
      series: [],
    })),
    utm: { visits: 0, uniqueSessions: 0, bySource: [] },
    health: {
      lastCollectAt: null,
      partialCount: 0,
      errorCount: 0,
      channels: MARKETING_STATS_PLATFORMS.map((platform) => ({
        platform,
        status: 'unknown',
        message: null,
      })),
    },
    unknownCounts: { missingEmotion: 0, missingCategory: 0 },
    todoHints: [],
  };
}

type MarketingStatsDashboardRaw = Partial<MarketingStatsDashboard> & {
  week?: { start?: string; end?: string };
  prevWeek?: { start?: string; end?: string };
  platforms?: Array<Partial<MarketingStatsPlatformKpi> & { platform?: string }>;
  utm?: Partial<MarketingStatsUtmSummary>;
  health?: Partial<MarketingStatsHealth>;
  unknownCounts?: Partial<MarketingStatsUnknownCounts>;
  todoHints?: string[];
};

function normalizeStatsDashboard(raw: MarketingStatsDashboardRaw | null | undefined): MarketingStatsDashboard {
  if (!raw || typeof raw !== 'object') return emptyStatsDashboard();

  const weekStart = raw.weekStart ?? raw.week?.start ?? '';
  const weekEnd = raw.weekEnd ?? raw.week?.end ?? '';
  const prevWeekStart = raw.prevWeekStart ?? raw.prevWeek?.start ?? '';
  const prevWeekEnd = raw.prevWeekEnd ?? raw.prevWeek?.end ?? '';

  type PlatformRow = Partial<MarketingStatsPlatformKpi> & { platform?: string };
  const platformsRaw: PlatformRow[] = Array.isArray(raw.platforms) ? raw.platforms : [];
  const byPlatform = new Map<string, PlatformRow>();
  for (const p of platformsRaw) {
    if (p?.platform) byPlatform.set(p.platform, p);
  }

  const platforms: MarketingStatsPlatformKpi[] = MARKETING_STATS_PLATFORMS.map((platform) => {
    const row = byPlatform.get(platform);
    const primaryMetric =
      row?.primaryMetric ?? MARKETING_STATS_DEFAULT_METRICS[platform];
    const value = Number(row?.value ?? row?.kpi ?? 0);
    const prevValue = Number(row?.prevValue ?? 0);
    let deltaPct: number | null =
      row?.deltaPct != null
        ? Number(row.deltaPct)
        : row?.delta != null
          ? Number(row.delta)
          : null;
    if (deltaPct == null && prevValue > 0) {
      deltaPct = ((value - prevValue) / prevValue) * 100;
    }
    return {
      platform,
      primaryMetric,
      value,
      prevValue,
      deltaPct: Number.isFinite(deltaPct as number) ? deltaPct : null,
      series: Array.isArray(row?.series)
        ? row.series.map((s) => ({
            day: String(s.day ?? ''),
            value: Number(s.value ?? 0),
          }))
        : [],
    };
  });

  const utmRaw: Partial<MarketingStatsUtmSummary> = raw.utm ?? {};
  const healthRaw: Partial<MarketingStatsHealth> = raw.health ?? {};
  const channelsRaw: MarketingStatsChannelHealth[] = Array.isArray(healthRaw.channels)
    ? healthRaw.channels
    : [];
  const channelMap = new Map<string, MarketingStatsChannelHealth>();
  for (const c of channelsRaw) {
    if (c?.platform) channelMap.set(c.platform, c);
  }

  return {
    weekStart,
    weekEnd,
    prevWeekStart,
    prevWeekEnd,
    platforms,
    utm: {
      visits: Number(utmRaw.visits ?? 0),
      uniqueSessions: Number(utmRaw.uniqueSessions ?? 0),
      bySource: Array.isArray(utmRaw.bySource)
        ? utmRaw.bySource.map((s) => ({
            source: String(s.source ?? ''),
            visits: Number(s.visits ?? 0),
          }))
        : [],
    },
    health: {
      lastCollectAt: healthRaw.lastCollectAt ?? null,
      partialCount: Number(healthRaw.partialCount ?? 0),
      errorCount: Number(healthRaw.errorCount ?? 0),
      channels: MARKETING_STATS_PLATFORMS.map((platform) => {
        const ch = channelMap.get(platform);
        return {
          platform,
          status: ch?.status ?? 'unknown',
          message: ch?.message ?? null,
        };
      }),
    },
    unknownCounts: {
      missingEmotion: Number(raw.unknownCounts?.missingEmotion ?? 0),
      missingCategory: Number(raw.unknownCounts?.missingCategory ?? 0),
    },
    todoHints: Array.isArray(raw.todoHints) ? raw.todoHints.map(String) : [],
    todoStrip: raw.todoStrip,
  };
}

/**
 * Phase 3 stats dashboard. Returns an empty shell on 404 so the UI can render
 * before the BE endpoint ships.
 */
export async function getMarketingStatsDashboard(
  params?: MarketingStatsDashboardParams
): Promise<MarketingStatsDashboard> {
  const q = new URLSearchParams();
  if (params?.platform) q.set('platform', params.platform);
  if (params?.weeksAgo != null) q.set('weeksAgo', String(params.weeksAgo));
  if (params?.rangeDays != null) q.set('rangeDays', String(params.rangeDays));
  if (params?.primaryMetric) q.set('primaryMetric', params.primaryMetric);
  const qs = q.size > 0 ? `?${q}` : '';
  try {
    const res = await api.get<MarketingStatsDashboardRaw>(
      `/api/admin/marketing/stats/dashboard${qs}`
    );
    return normalizeStatsDashboard(res.data);
  } catch (err: unknown) {
    const status =
      typeof err === 'object' && err !== null && 'response' in err
        ? (err as { response?: { status?: number } }).response?.status
        : undefined;
    if (status === 404) {
      return emptyStatsDashboard();
    }
    throw err;
  }
}

// ===== Phase 3 theme matrix / boosts / events =====

export const MARKETING_THEME_EMOTIONS = [
  'shock',
  'anger',
  'tension',
  'sad',
  'hype',
] as const;

export const MARKETING_THEME_CATEGORIES = [
  'COUPLE',
  'MARRIED',
  'FRIEND',
  'FAMILY',
  'WORK',
  'OTHER',
] as const;

export const MARKETING_THEME_EMOTION_LABELS: Record<string, string> = {
  shock: '충격',
  anger: '분노',
  tension: '긴장',
  sad: '슬픔',
  hype: '하이프',
};

export const MARKETING_THEME_CATEGORY_LABELS: Record<string, string> = {
  COUPLE: '연인',
  MARRIED: '부부',
  FRIEND: '친구',
  FAMILY: '가족',
  WORK: '직장',
  OTHER: '기타',
};

export interface MarketingThemeMatrixCell {
  emotion: string;
  category: string;
  n: number;
  score: number;
  /** Week-over-week delta (absolute or ratio — display as-is). */
  delta: number | null;
  /** Current stored boost. */
  boost: number;
  locked: boolean;
}

export interface MarketingThemeProposal {
  emotion?: string | null;
  category?: string | null;
  n?: number;
  score?: number;
  prevScore?: number;
  delta?: number | null;
  proposalScore?: number;
  currentBoost?: number;
  /** Suggested boost (preferred). */
  suggestedBoost?: number;
  /** Alias some BE drafts may use instead of suggestedBoost. */
  boost?: number;
  reason?: string | null;
  axis?: 'cell' | 'emotion' | 'category' | string;
  direction?: 'up' | 'down' | 'flat' | string;
  rolled?: boolean;
}

export interface MarketingThemeMatrix {
  platform: string;
  emotions: string[];
  categories: string[];
  cells: MarketingThemeMatrixCell[];
  proposals: MarketingThemeProposal[];
  rolledProposals: MarketingThemeProposal[];
  unknownHints?: MarketingStatsUnknownCounts | null;
  cooldownUntil?: string | null;
  canApply?: boolean;
  shadow?: boolean;
}

export interface MarketingThemeBoostChange {
  emotion?: string | null;
  category?: string | null;
  boost: number;
}

export interface MarketingThemeApplyRequest {
  platform: string;
  changes: MarketingThemeBoostChange[];
  confirm: true;
}

export interface MarketingThemeApplyResult {
  applied: number;
  before: Record<string, Record<string, number>>;
  after: Record<string, Record<string, number>>;
  cooldownUntil: string | null;
}

/** Nested emotion → category → boost. */
export type MarketingThemeBoostsMap = Record<string, Record<string, number>>;

export interface MarketingStatsEventDto {
  id: number;
  eventType: string;
  platform: string | null;
  payloadJson: string | null;
  createdAt: string;
}

function isNotFound(err: unknown): boolean {
  return (
    typeof err === 'object' &&
    err !== null &&
    'response' in err &&
    (err as { response?: { status?: number } }).response?.status === 404
  );
}

function emptyThemeMatrix(platform: string): MarketingThemeMatrix {
  return {
    platform,
    emotions: [...MARKETING_THEME_EMOTIONS],
    categories: [...MARKETING_THEME_CATEGORIES],
    cells: [],
    proposals: [],
    rolledProposals: [],
    unknownHints: null,
    cooldownUntil: null,
    canApply: true,
    shadow: true,
  };
}

function normalizeProposal(raw: Partial<MarketingThemeProposal> | null | undefined): MarketingThemeProposal {
  if (!raw || typeof raw !== 'object') return {};
  const suggested =
    raw.suggestedBoost != null
      ? Number(raw.suggestedBoost)
      : raw.boost != null
        ? Number(raw.boost)
        : undefined;
  return {
    emotion: raw.emotion ?? null,
    category: raw.category ?? null,
    n: raw.n != null ? Number(raw.n) : undefined,
    score: raw.score != null ? Number(raw.score) : undefined,
    prevScore: raw.prevScore != null ? Number(raw.prevScore) : undefined,
    delta: raw.delta != null ? Number(raw.delta) : null,
    proposalScore: raw.proposalScore != null ? Number(raw.proposalScore) : undefined,
    currentBoost: raw.currentBoost != null ? Number(raw.currentBoost) : undefined,
    suggestedBoost: suggested != null && Number.isFinite(suggested) ? suggested : undefined,
    boost: suggested != null && Number.isFinite(suggested) ? suggested : undefined,
    reason: raw.reason ?? null,
    axis: raw.axis,
    direction: raw.direction,
    rolled: raw.rolled,
  };
}

function normalizeThemeMatrix(
  raw: Partial<MarketingThemeMatrix> | null | undefined,
  fallbackPlatform: string
): MarketingThemeMatrix {
  const platform = raw?.platform || fallbackPlatform;
  if (!raw || typeof raw !== 'object') return emptyThemeMatrix(platform);
  return {
    platform,
    emotions:
      Array.isArray(raw.emotions) && raw.emotions.length > 0
        ? raw.emotions.map(String)
        : [...MARKETING_THEME_EMOTIONS],
    categories:
      Array.isArray(raw.categories) && raw.categories.length > 0
        ? raw.categories.map(String)
        : [...MARKETING_THEME_CATEGORIES],
    cells: Array.isArray(raw.cells)
      ? raw.cells.map((c) => ({
          emotion: String(c.emotion ?? ''),
          category: String(c.category ?? ''),
          n: Number(c.n ?? 0),
          score: Number(c.score ?? 0),
          delta: c.delta != null ? Number(c.delta) : null,
          boost: Number(c.boost ?? 1),
          locked: Boolean(c.locked),
        }))
      : [],
    proposals: Array.isArray(raw.proposals) ? raw.proposals.map(normalizeProposal) : [],
    rolledProposals: Array.isArray(raw.rolledProposals)
      ? raw.rolledProposals.map(normalizeProposal)
      : [],
    unknownHints: raw.unknownHints ?? null,
    cooldownUntil: raw.cooldownUntil ?? null,
    canApply: raw.canApply ?? true,
    shadow: raw.shadow ?? true,
  };
}

/** GET emotion×category heatmap + proposals. Empty shell on 404. */
export async function getMarketingThemeMatrix(opts?: {
  platform?: string;
  weeksAgo?: number;
}): Promise<MarketingThemeMatrix> {
  const platform = opts?.platform || 'x_thread';
  const q = new URLSearchParams();
  q.set('platform', platform);
  if (opts?.weeksAgo != null) q.set('weeksAgo', String(opts.weeksAgo));
  try {
    const res = await api.get<Partial<MarketingThemeMatrix>>(
      `/api/admin/marketing/stats/theme-matrix?${q}`
    );
    return normalizeThemeMatrix(res.data, platform);
  } catch (err: unknown) {
    if (isNotFound(err)) return emptyThemeMatrix(platform);
    throw err;
  }
}

/** POST propose — recalculate suggestions (no persist). */
export async function proposeMarketingThemeMatrix(opts?: {
  platform?: string;
  weeksAgo?: number;
}): Promise<MarketingThemeMatrix | MarketingThemeProposal[]> {
  const platform = opts?.platform || 'x_thread';
  const q = new URLSearchParams();
  q.set('platform', platform);
  if (opts?.weeksAgo != null) q.set('weeksAgo', String(opts.weeksAgo));
  const res = await api.post<MarketingThemeMatrix | MarketingThemeProposal[]>(
    `/api/admin/marketing/stats/theme-matrix/propose?${q}`,
    null
  );
  const data = res.data;
  if (Array.isArray(data)) {
    return data.map(normalizeProposal);
  }
  return normalizeThemeMatrix(data, platform);
}

/** POST apply confirmed boost changes. */
export async function applyMarketingThemeMatrix(
  body: MarketingThemeApplyRequest
): Promise<MarketingThemeApplyResult> {
  const res = await api.post<MarketingThemeApplyResult>(
    '/api/admin/marketing/stats/theme-matrix/apply',
    body
  );
  const data = res.data;
  return {
    applied: Number(data?.applied ?? 0),
    before: data?.before ?? {},
    after: data?.after ?? {},
    cooldownUntil: data?.cooldownUntil ?? null,
  };
}

/** GET stored boost matrix for a platform. */
export async function getMarketingThemeBoosts(
  platform?: string
): Promise<MarketingThemeBoostsMap> {
  const q = new URLSearchParams();
  if (platform) q.set('platform', platform);
  const qs = q.size > 0 ? `?${q}` : '';
  try {
    const res = await api.get<MarketingThemeBoostsMap | { matrix?: MarketingThemeBoostsMap }>(
      `/api/admin/marketing/stats/theme-boosts${qs}`
    );
    const data = res.data;
    if (data && typeof data === 'object' && 'matrix' in data && data.matrix) {
      return data.matrix as MarketingThemeBoostsMap;
    }
    return (data as MarketingThemeBoostsMap) ?? {};
  } catch (err: unknown) {
    if (isNotFound(err)) return {};
    throw err;
  }
}

/** GET marketing stats activity timeline. */
export async function getMarketingStatsEvents(limit = 50): Promise<MarketingStatsEventDto[]> {
  const q = new URLSearchParams();
  q.set('limit', String(limit));
  try {
    const res = await api.get<
      MarketingStatsEventDto[] | { items?: MarketingStatsEventDto[]; events?: MarketingStatsEventDto[] }
    >(`/api/admin/marketing/stats/events?${q}`);
    const data = res.data;
    if (Array.isArray(data)) return data.map(normalizeStatsEvent);
    if (data && typeof data === 'object') {
      const list = data.items ?? data.events ?? [];
      return list.map(normalizeStatsEvent);
    }
    return [];
  } catch (err: unknown) {
    if (isNotFound(err)) return [];
    throw err;
  }
}

function normalizeStatsEvent(
  raw: Partial<MarketingStatsEventDto> & {
    event_type?: string;
    payload_json?: string;
    created_at?: string;
  }
): MarketingStatsEventDto {
  return {
    id: Number(raw.id ?? 0),
    eventType: String(raw.eventType ?? raw.event_type ?? ''),
    platform: raw.platform ?? null,
    payloadJson: raw.payloadJson ?? raw.payload_json ?? null,
    createdAt: String(raw.createdAt ?? raw.created_at ?? ''),
  };
}

/** Holding-tab deep link for a theme cell (pin recommendation v1). */
export function marketingHoldingThemeDeepLink(emotion?: string | null, category?: string | null): string {
  const q = new URLSearchParams();
  q.set('tab', 'holding');
  if (emotion) q.set('emotion', emotion);
  if (category) q.set('category', category);
  return `/admin/marketing?${q}`;
}

/** Resolve suggested boost from a proposal row. */
export function themeProposalSuggestedBoost(p: MarketingThemeProposal): number | null {
  const v = p.suggestedBoost ?? p.boost;
  return v != null && Number.isFinite(v) ? Number(v) : null;
}

// ===== Sound Effects (SFX) Mapping =====

export interface SfxEvent {
  key: string;
  file: string;
  volume: number;
  offset: number;
}

export interface SfxLibraryFile {
  name: string;
  path: string;
}

export interface SfxLibraryCategory {
  category: string;
  files: SfxLibraryFile[];
}

export interface SfxMapping {
  events: SfxEvent[];
  maxPerVideo: number;
  library: SfxLibraryCategory[];
}

export async function getSfxMapping(): Promise<SfxMapping> {
  const res = await api.get<SfxMapping>('/api/admin/marketing/sfx/mapping');
  return res.data;
}

export async function putSfxMapping(body: { events: SfxEvent[]; maxPerVideo: number }): Promise<SfxMapping> {
  const res = await api.put<SfxMapping>('/api/admin/marketing/sfx/mapping', body);
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
  /** Actual 1-based rank per platform at automatic T+24h selection. */
  platformRankSnapshot: Record<string, number>;
  viewCount: number;
  commentCount: number;
  voteCount: number;
  projectedFormat: MarketingProjectedFormat;
  draft: MarketingHoldingDraft | null;
  lockedAt: string | null;
  postCreatedAt: string;
  createdAt: string;
  updatedAt: string;
  /** T+24h elapsed; commit tick will retry instead of dropping from the waiting board. */
  overdue?: boolean;
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
    platformRankSnapshot: raw.platformRankSnapshot ?? {},
    viewCount: Number(raw.viewCount ?? 0),
    commentCount: Number(raw.commentCount ?? 0),
    voteCount: Number(raw.voteCount ?? 0),
    projectedFormat: normalizeProjectedFormat(raw.projectedFormat),
    draft: normalizeHoldingDraft(raw.draft),
    lockedAt: raw.lockedAt ?? null,
    postCreatedAt: String(raw.postCreatedAt ?? ''),
    createdAt: String(raw.createdAt ?? ''),
    updatedAt: String(raw.updatedAt ?? ''),
    overdue: raw.overdue === true,
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
  /** Actual 1-based rank per selected platform; empty for manual/pinned commits. */
  platformRankSnapshot?: Record<string, number>;
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

// ===== Job redrive (failed jobs bulk redrive) =====

export interface RedriveJobIdRequest {
  jobIds: number[];
  skipExisting?: boolean;
}

export interface RedriveJobFilterRequest {
  filter: {
    status: string;
    since?: string;
  };
  skipExisting?: boolean;
}

export type RedriveRequest = RedriveJobIdRequest | RedriveJobFilterRequest;

export interface RedriveResult {
  sourceId: number;
  targetId: number | null;
  action: 'REGENERATED' | 'RECREATED' | 'SKIPPED' | 'ERROR';
  reason: string | null;
  platformStates: Record<string, string> | null;
}

export interface RedriveResponse {
  requested: number;
  results: RedriveResult[];
}

export async function redriveMarketingJobs(request: RedriveRequest): Promise<RedriveResponse> {
  const res = await api.post<RedriveResponse>('/api/admin/marketing/jobs/redrive', request);
  return res.data;
}

/** Redesign note aliases (S6 wiring). Prefer the Marketing* names above. */
export const getScoreWeights = getMarketingScoreWeights;
export const updateScoreWeights = updateMarketingScoreWeights;
export const listMarketingHoldings = getMarketingHoldingBoard;
