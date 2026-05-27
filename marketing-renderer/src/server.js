const express = require('express');
const { chromium } = require('playwright');
const sharp = require('sharp');
const { buildChatHtml } = require('./templates/chat');

const renderQuoteRouter = require('./routes/render-quote');
const renderCardNewsRouter = require('./routes/render-card-news');
const renderReportSummaryRouter = require('./routes/render-report-summary');

const app = express();
app.use(express.json({ limit: '20mb' }));

let browser = null;

async function getBrowser() {
  if (!browser) {
    browser = await chromium.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  }
  return browser;
}

// Make browser accessible from route handlers
app.set('getBrowser', getBrowser);
app.set('browser', null);
(async () => { app.set('browser', await getBrowser()); })();

app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'marketing-renderer' });
});

app.use('/render-quote', renderQuoteRouter);
app.use('/render-card-news', renderCardNewsRouter);
app.use('/render-report-summary', renderReportSummaryRouter);

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

  const maxMessages = typeof req.body.maxMessages === 'number' ? req.body.maxMessages : 5;
  const preview = maxMessages > 0 ? messages.slice(0, maxMessages) : messages;
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

const PORT = process.env.PORT || 9000;
app.listen(PORT, () => {
  console.log(`marketing-renderer listening on port ${PORT}`);
});

process.on('SIGTERM', async () => {
  if (browser) await browser.close();
  process.exit(0);
});
