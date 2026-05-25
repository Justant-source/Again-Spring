// Smoke test — renders a simple HTML and verifies PNG output > 0 bytes
const http = require('http');

const html = '<html><body style="background:#fff;width:100px;height:100px;"></body></html>';

const postData = JSON.stringify({ html, viewport: { w: 100, h: 100 } });

const options = {
  hostname: 'localhost',
  port: process.env.PORT || 9000,
  path: '/render',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(postData),
  },
};

const req = http.request(options, (res) => {
  const chunks = [];
  res.on('data', (chunk) => chunks.push(chunk));
  res.on('end', () => {
    const buf = Buffer.concat(chunks);
    if (res.statusCode !== 200) {
      console.error('FAIL: status', res.statusCode);
      process.exit(1);
    }
    if (buf.length === 0) {
      console.error('FAIL: empty PNG');
      process.exit(1);
    }
    console.log('PASS: PNG bytes =', buf.length);
  });
});
req.on('error', (e) => { console.error('FAIL:', e.message); process.exit(1); });
req.write(postData);
req.end();
