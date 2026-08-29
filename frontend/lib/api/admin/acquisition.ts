import { api } from '@/lib/api/client';

/**
 * 유입 퍼널 (방문 → 고유 방문자 → 가입) — 어드민 마케팅 통계 탭.
 *
 * 배경(2026-08-29): 발행 성공·플랫폼 지표(뷰·도달)까지만 보이던 화면에
 * "그 다음 칸"을 채운다. 서버(BE) 응답 형태는
 * `backend/.../marketing/AcquisitionFunnelService.FunnelDto`가 SSOT다.
 */

export interface AcquisitionChannelRow {
  source: string;
  visits: number;
  visitors: number;
  sessions: number;
  signups: number;
}

export interface AcquisitionDailyRow {
  date: string;
  visits: number;
  visitors: number;
  signups: number;
}

export interface AcquisitionBotSplit {
  human: number;
  bot: number;
}

export interface AcquisitionReferrerRow {
  host: string;
  visits: number;
}

export interface AcquisitionPathRow {
  path: string;
  visits: number;
  visitors: number;
}

export interface AcquisitionFunnel {
  days: number;
  totalVisits: number;
  totalVisitors: number;
  totalSignups: number;
  botSplit: AcquisitionBotSplit;
  byChannel: AcquisitionChannelRow[];
  daily: AcquisitionDailyRow[];
  topReferrers: AcquisitionReferrerRow[];
  topPaths: AcquisitionPathRow[];
}

/**
 * GET /api/admin/marketing/stats/acquisition?days=N
 * 봇 제외 · 채널별/일별 방문·가입 집계.
 */
export async function getAcquisitionFunnel(days: number = 30): Promise<AcquisitionFunnel> {
  const res = await api.get<AcquisitionFunnel>('/api/admin/marketing/stats/acquisition', {
    params: { days },
  });
  return res.data;
}
