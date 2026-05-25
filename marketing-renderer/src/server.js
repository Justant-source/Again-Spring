const express = require('express');
const { chromium } = require('playwright');
const sharp = require('sharp');

const app = express();
app.use(express.json({ limit: '10mb' }));

let browser = null;

async function getBrowser() {
  if (!browser) {
    browser = await chromium.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  }
  return browser;
}

app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'marketing-renderer' });
});

// Generic HTML → PNG renderer (used for Instagram image cards)
app.post('/render', async (req, res) => {
  const { html, viewport = { w: 1080, h: 1350 }, format = 'png', strip_exif = true } = req.body;

  if (!html) {
    return res.status(400).json({ error: 'html is required' });
  }

  try {
    const b = await getBrowser();
    const page = await b.newPage();
    await page.setViewportSize({ width: viewport.w, height: viewport.h });
    await page.setContent(html, { waitUntil: 'networkidle' });

    const screenshotBuffer = await page.screenshot({ type: 'png', fullPage: false });
    await page.close();

    if (strip_exif) {
      const cleaned = await sharp(screenshotBuffer)
        .withMetadata(false)
        .toBuffer();
      res.set('Content-Type', 'image/png');
      return res.send(cleaned);
    }

    res.set('Content-Type', 'image/png');
    res.send(screenshotBuffer);
  } catch (err) {
    console.error('Render error:', err);
    res.status(500).json({ error: err.message });
  }
});

// Chat UI screenshot renderer — mimics actual Dasibom chat design
// Body: { messages: [{sender, content, createdAt}], title?, subtitle?, viewport? }
app.post('/render-chat', async (req, res) => {
  const {
    messages = [],
    title = '다시봄',
    subtitle = 'AI 갈등 중재',
    viewport = { w: 390, h: 720 },
  } = req.body;

  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: 'messages array is required' });
  }

  // Show up to 5 messages for a clean marketing shot
  const preview = messages.slice(0, 5);
  const html = buildChatHtml(preview, title, subtitle);

  try {
    const b = await getBrowser();
    const page = await b.newPage();
    await page.setViewportSize({ width: viewport.w, height: viewport.h });
    await page.setContent(html, { waitUntil: 'networkidle' });

    const screenshotBuffer = await page.screenshot({ type: 'png', fullPage: false });
    await page.close();

    const cleaned = await sharp(screenshotBuffer)
      .withMetadata(false)
      .toBuffer();

    res.set('Content-Type', 'image/png');
    res.send(cleaned);
  } catch (err) {
    console.error('Chat render error:', err);
    res.status(500).json({ error: err.message });
  }
});

function buildChatHtml(messages, title, subtitle) {
  const msgHtml = messages.map(msg => {
    const isMine = msg.sender === 'USER_A';
    const time = msg.createdAt
      ? new Date(msg.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
      : '';
    const senderLabel = isMine ? '나' : '다시봄 AI';

    return `
      <div style="
        display:flex;
        flex-direction:column;
        align-items:${isMine ? 'flex-end' : 'flex-start'};
        margin-bottom:16px;
      ">
        <div style="
          font-size:10px;
          color:#A08670;
          margin-bottom:4px;
          padding: 0 4px;
        ">${senderLabel}</div>
        <div style="
          max-width:78%;
          padding:10px 14px;
          border-radius:${isMine ? '14px 14px 4px 14px' : '14px 14px 14px 4px'};
          background:${isMine ? '#F4A896' : '#FFF8F0'};
          color:#5C4030;
          font-size:14px;
          line-height:1.6;
          word-break:break-word;
          border:${isMine ? 'none' : '1px solid #EADFD0'};
          box-shadow:0 1px 3px rgba(92,64,48,0.08);
        ">${escapeHtml(msg.content)}</div>
        ${time ? `<div style="font-size:10px;color:#A08670;margin-top:4px;padding:0 4px;">${time}</div>` : ''}
      </div>
    `;
  }).join('');

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif;
    background: #FBF3EC;
    height: 100vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
</style>
</head>
<body>
  <!-- App header -->
  <div style="
    background:#FFF8F0;
    border-bottom:1px solid #EADFD0;
    padding:14px 20px 12px;
    display:flex;
    align-items:center;
    gap:10px;
    flex-shrink:0;
  ">
    <div style="
      width:36px;height:36px;
      border-radius:50%;
      background:linear-gradient(135deg,#F4A896,#A8C8B4);
      display:flex;align-items:center;justify-content:center;
      font-size:16px;font-weight:700;color:#FFF;
    ">다</div>
    <div>
      <div style="font-size:14px;font-weight:700;color:#5C4030;">${escapeHtml(title)}</div>
      <div style="font-size:11px;color:#A08670;">${escapeHtml(subtitle)}</div>
    </div>
  </div>

  <!-- Chat messages -->
  <div style="
    flex:1;
    overflow:hidden;
    padding:16px 16px 0;
    display:flex;
    flex-direction:column;
    justify-content:flex-end;
  ">
    ${msgHtml}
  </div>

  <!-- Input bar (decorative, not interactive) -->
  <div style="
    background:#FFF8F0;
    border-top:1px solid #EADFD0;
    padding:10px 16px;
    display:flex;
    align-items:center;
    gap:8px;
    flex-shrink:0;
  ">
    <div style="
      flex:1;
      background:#FBF3EC;
      border:1px solid #EADFD0;
      border-radius:20px;
      padding:8px 14px;
      font-size:13px;
      color:#A08670;
    ">메시지를 입력하세요...</div>
    <div style="
      width:36px;height:36px;
      border-radius:50%;
      background:#F4A896;
      display:flex;align-items:center;justify-content:center;
      font-size:18px;color:#FFF;
      cursor:pointer;
    ">&#9650;</div>
  </div>

  <!-- Watermark -->
  <div style="
    position:absolute;bottom:56px;right:16px;
    font-size:10px;color:#EADFD0;
    pointer-events:none;
    user-select:none;
  ">again-spring.net</div>
</body>
</html>`;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const PORT = process.env.PORT || 9000;
app.listen(PORT, () => {
  console.log(`marketing-renderer listening on port ${PORT}`);
});

process.on('SIGTERM', async () => {
  if (browser) await browser.close();
  process.exit(0);
});
