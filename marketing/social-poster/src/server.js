const express = require('express');
const { chromium } = require('playwright');

const publishXRouter = require('./routes/publish-x');
const publishInstagramRouter = require('./routes/publish-instagram');
const publishNaverBlogRouter = require('./routes/publish-naver-blog');
const sessionHealthRouter = require('./routes/session-health');
const testLoginRouter = require('./routes/test-login');

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
  res.json({ status: 'ok', service: 'social-poster' });
});

app.use('/publish/x', publishXRouter);
app.use('/publish/instagram', publishInstagramRouter);
app.use('/publish/naver-blog', publishNaverBlogRouter);
app.use('/session', sessionHealthRouter);
app.use('/test-login', testLoginRouter);

const PORT = process.env.PORT || 9100;
app.listen(PORT, () => {
  console.log(`social-poster listening on port ${PORT}`);
});

process.on('SIGTERM', async () => {
  if (browser) await browser.close();
  process.exit(0);
});
