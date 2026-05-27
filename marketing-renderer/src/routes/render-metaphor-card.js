const express = require('express');
const sharp = require('sharp');
const t = require('../styles/tokens');

const router = express.Router();

const FRONTEND_BASE = process.env.FRONTEND_BASE_URL || 'http://againspring-frontend-dev:3000';

/**
 * POST /render-metaphor-card
 * body: { svgFilename, hookText, contentId, slideNumber?, totalSlides? }
 * Renders a 1080×1080 metaphor hook card:
 *   - background: warm cream (#FFF8F0)
 *   - top ~55%: SVG illustration centered
 *   - bottom ~40%: hook text (large, dark brown) + 다시봄 brand
 * response: PNG bytes (image/png)
 */
router.post('/', async (req, res) => {
  const {
    svgFilename = '09-overflowing-cup.svg',
    hookText = '이런 갈등, 겪어보셨나요?',
    contentId = 0,
    slideNumber = 1,
    totalSlides = 1,
  } = req.body;

  const svgUrl = `${FRONTEND_BASE}/illustrations/metaphors/${encodeURIComponent(svgFilename)}`;
  const indicator = buildIndicator(slideNumber, totalSlides);

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body {
    width:1080px; height:1080px;
    background:${t.bgCard};
    font-family:${t.fontStack};
    display:flex; flex-direction:column;
    align-items:center; justify-content:space-between;
    overflow:hidden;
  }

  /* top brand bar */
  .top-bar {
    width:100%; padding:28px 48px 0;
    display:flex; align-items:center; justify-content:space-between;
    flex-shrink:0;
  }
  .top-brand { font-size:20px; font-weight:700; color:${t.inkSub}; letter-spacing:1px; }
  .top-indicator { display:flex; gap:8px; align-items:center; }

  /* illustration area */
  .illust-wrap {
    flex:1;
    display:flex; align-items:center; justify-content:center;
    padding:40px 80px 0;
    width:100%;
  }
  .illust-wrap img {
    max-width:560px; max-height:520px;
    width:100%; height:100%;
    object-fit:contain;
  }

  /* hook text block */
  .hook-block {
    flex-shrink:0;
    width:100%;
    padding:40px 80px 48px;
    text-align:center;
  }
  .hook-label {
    font-size:18px; color:${t.inkSub}; letter-spacing:1.5px;
    margin-bottom:16px; text-transform:uppercase; font-weight:600;
  }
  .hook-text {
    font-size:44px; font-weight:700; color:${t.inkDark};
    line-height:1.45; word-break:keep-all;
  }
  .hook-divider {
    width:60px; height:4px; border-radius:2px;
    background:linear-gradient(90deg,${t.gradientStart},${t.gradientEnd});
    margin:28px auto 0;
  }
</style>
</head>
<body>
  <div class="top-bar">
    <div class="top-brand">다시봄</div>
    <div class="top-indicator">${indicator}</div>
  </div>

  <div class="illust-wrap">
    <img src="${svgUrl}" alt="관계 메타포" />
  </div>

  <div class="hook-block">
    <div class="hook-label">관계 이야기</div>
    <div class="hook-text">${escapeHtml(hookText)}</div>
    <div class="hook-divider"></div>
  </div>
</body>
</html>`;

  try {
    const b = req.app.get('browser') || (await req.app.get('getBrowser')());
    const page = await b.newPage();
    await page.setViewportSize({ width: 1080, height: 1080 });
    await page.setContent(html, { waitUntil: 'networkidle' });

    const buf = await page.screenshot({ type: 'png', fullPage: false });
    await page.close();

    const cleaned = await sharp(buf).withMetadata(false).toBuffer();
    res.set('Content-Type', 'image/png');
    res.send(cleaned);
  } catch (err) {
    console.error('render-metaphor-card error:', err);
    res.status(500).json({ error: err.message });
  }
});

function buildIndicator(current, total) {
  return Array.from({ length: total }, (_, i) => {
    const active = i + 1 === current;
    return `<div style="
      width:${active ? 28 : 10}px; height:10px; border-radius:5px;
      background:${active ? t.bubbleA : t.border};
    "></div>`;
  }).join('');
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

module.exports = router;
