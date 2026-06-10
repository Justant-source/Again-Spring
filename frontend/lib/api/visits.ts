interface VisitEventRequest {
  path: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmContent?: string;
  referrer?: string;
  sessionKey?: string;
}

export async function recordVisit(event: VisitEventRequest): Promise<void> {
  try {
    await fetch('/api/public/visits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: event.path,
        utm_source: event.utmSource,
        utm_medium: event.utmMedium,
        utm_campaign: event.utmCampaign,
        utm_content: event.utmContent,
        referrer: event.referrer,
        session_key: event.sessionKey,
      }),
    });
  } catch {
    // fire-and-forget: silently ignore errors
  }
}
