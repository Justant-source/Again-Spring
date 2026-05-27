const express = require('express');
const sharp = require('sharp');
const { buildCardSlideHtml } = require('../templates/cardNews');

const router = express.Router();

/**
 * POST /render-card-news
 * body: { slides: [{role, title, body, visualHint}], theme?: 'warm', contentId? }
 * response: { slides: [{filename, base64}] }
 */
router.post('/', async (req, res) => {
  const { slides = [], theme = 'warm', contentId = 0, viewport = { w: 1080, h: 1080 } } = req.body;

  if (!Array.isArray(slides) || slides.length === 0) {
    return res.status(400).json({ error: 'slides array is required' });
  }

  try {
    const b = req.app.get('browser') || (await req.app.get('getBrowser')());
    const page = await b.newPage();
    await page.setViewportSize({ width: viewport.w, height: viewport.h });

    const results = [];
    const total = slides.length;

    for (let i = 0; i < total; i++) {
      const slide = slides[i];
      const html = buildCardSlideHtml({
        role: slide.role || 'BONUS',
        title: slide.title || '',
        body: slide.body || '',
        visualHint: slide.visualHint || '',
        slideNumber: i + 1,
        totalSlides: total,
      });

      await page.setContent(html, { waitUntil: 'networkidle' });
      const buf = await page.screenshot({ type: 'png', fullPage: false });
      const cleaned = await sharp(buf).withMetadata(false).toBuffer();

      const idx = String(i + 1).padStart(2, '0');
      const filename = `card_${contentId}_${idx}.png`;
      results.push({ filename, base64: cleaned.toString('base64') });

      // Reset page between slides to avoid content bleed
      await page.goto('about:blank');
    }

    await page.close();
    res.json({ slides: results });
  } catch (err) {
    console.error('render-card-news error:', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
