const express = require('express');
const sharp = require('sharp');
const { buildReportSummaryHtml } = require('../templates/reportSummary');

const router = express.Router();

/**
 * POST /render-report-summary
 * body: {
 *   report: { needsMap:{labelA,labelB,distance}, contributionRatio:{a,b}, metaphor? },
 *   mode: 'needs' | 'ratio' | 'combined',
 *   viewport?: {w, h}
 * }
 * response: PNG bytes
 */
router.post('/', async (req, res) => {
  const { report = {}, mode = 'combined', viewport = { w: 1080, h: 1350 } } = req.body;

  try {
    const html = buildReportSummaryHtml({ report, mode });

    const b = req.app.get('browser') || (await req.app.get('getBrowser')());
    const page = await b.newPage();
    await page.setViewportSize({ width: viewport.w, height: viewport.h });
    await page.setContent(html, { waitUntil: 'networkidle' });
    const buf = await page.screenshot({ type: 'png', fullPage: false });
    await page.close();

    const cleaned = await sharp(buf).withMetadata(false).toBuffer();
    res.set('Content-Type', 'image/png');
    res.send(cleaned);
  } catch (err) {
    console.error('render-report-summary error:', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
