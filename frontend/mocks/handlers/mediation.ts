import { http, HttpResponse, delay } from 'msw';
import { pickReport } from '../fixtures/mockReports';

export const mediationHandlers = [
  http.post('/api/sessions/:id/report', async ({ params }) => {
    await delay(2400);
    const report = pickReport(String(params.id));
    return HttpResponse.json(report);
  }),

  http.get('/api/sessions/:id/report', async ({ params }) => {
    await delay(200);
    const report = pickReport(String(params.id));
    return HttpResponse.json(report);
  }),

  // Developer override: pick a specific scenario via query param
  http.get('/api/mock/report', async ({ request }) => {
    const url = new URL(request.url);
    const scenario = url.searchParams.get('scenario') ?? 'difference';
    await delay(200);
    return HttpResponse.json(pickReport(`force_${scenario}`));
  }),
];
