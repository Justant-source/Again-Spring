import { api } from '../client';

/**
 * 크롤 신선도 상태 응답 DTO
 * - savedBySource24h: 각 소스별 최근 24시간 저장 건수
 * - lastSuccessfulAt: 각 소스의 마지막 성공 크롤 시각 (ISO-8601 UTC)
 * - failureCount24h: 최근 24시간 내 실패 크롤 건수 합계
 * - stale: 최근 24시간 내 성공 크롤이 0건이면 true
 * - checkedAt: 조회 시각 (ISO-8601 UTC)
 * - errorMessage: 조회 오류 시 메시지 (정상이면 null)
 */
export interface CrawlStatusResponse {
  savedBySource24h: Record<string, number>;
  lastSuccessfulAt: Record<string, string>;
  failureCount24h: number;
  stale: boolean;
  checkedAt: string;
  errorMessage?: string | null;
}

/**
 * 크롤 신선도 상태 조회
 */
export async function getCrawlStatus(): Promise<CrawlStatusResponse> {
  const res = await api.get<CrawlStatusResponse>('/api/admin/crawl-status');
  return res.data;
}
