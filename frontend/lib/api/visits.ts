interface VisitEventRequest {
  path: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmContent?: string;
  referrer?: string;
  sessionKey?: string;
  visitorKey?: string;
}

/**
 * 방문 1건 기록. fire-and-forget.
 *
 * 🔴 필드명은 반드시 camelCase — 백엔드 VisitRequest DTO가 Jackson 기본(camelCase)으로
 * 역직렬화한다. 2026-08-29까지 이 함수가 snake_case(utm_source, session_key)로 보내는
 * 바람에 path/referrer를 제외한 모든 값이 조용히 버려졌다. 그래서 visit_events의
 * session_key는 100% NULL이었고 UTM은 캠페인 귀속에 전혀 쓰이지 못했다.
 * 필드를 추가할 때 DTO 필드명과 철자가 같은지 반드시 확인할 것.
 */
export async function recordVisit(event: VisitEventRequest): Promise<void> {
  try {
    await fetch('/api/public/visits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: event.path,
        utmSource: event.utmSource,
        utmMedium: event.utmMedium,
        utmCampaign: event.utmCampaign,
        utmContent: event.utmContent,
        referrer: event.referrer,
        sessionKey: event.sessionKey,
        visitorKey: event.visitorKey,
      }),
    });
  } catch {
    // fire-and-forget: silently ignore errors
  }
}
