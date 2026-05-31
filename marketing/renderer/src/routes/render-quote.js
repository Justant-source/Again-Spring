const express = require('express');
const sharp = require('sharp');
const { buildQuoteHtml } = require('../templates/quoteCard');

const router = express.Router();

router.post('/', async (req, res) => {
  const { line1, line2 = '', attribution = '다시봄', variant = 'warm', viewport = { w: 1080, h: 1350 } } = req.body;

  if (!line1) {
    return res.status(400).json({ error: 'line1 is required' });
  }

  try {
    const html = buildQuoteHtml({ line1, line2, attribution, variant });

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
    console.error('render-quote error:', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
