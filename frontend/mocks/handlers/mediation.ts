import { http, HttpResponse, delay } from 'msw';
import { getMediatorTurn } from '../fixtures/mockMediations';
import { pickReport } from '../fixtures/mockReports';

export const mediationHandlers = [
  http.post('/api/sessions/:id/turns', async ({ request }) => {
    const body: any = await request.json();
    // Simulate AI response latency
    await delay(1400);

    const next = getMediatorTurn(body.turnNumber + 1);
    return HttpResponse.json({
      ack: {
        turnNumber: body.turnNumber,
        role: body.role,
        content: body.content,
        createdAt: new Date().toISOString(),
      },
      nextTurn: next ?? null,
      completed: !next,
    });
  }),

  http.get('/api/sessions/:id/first-turn', async () => {
    await delay(500);
    return HttpResponse.json(getMediatorTurn(1));
  }),

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
