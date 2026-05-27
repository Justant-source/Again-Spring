const t = require('../styles/tokens');

/**
 * 인용 카드 HTML (1080×1350)
 * line1: 메타포 또는 핵심 문장 (30자 이내)
 * line2: 감정 또는 인사이트 (40자 이내)
 * attribution: 어트리뷰션 (예: "다시봄")
 * variant: 'warm' (기본) | 'calm'
 */
function buildQuoteHtml({ line1, line2, attribution = '다시봄', variant = 'warm' }) {
  const bgColor = variant === 'calm' ? '#EAF4EF' : t.bgWarm;
  const accentColor = variant === 'calm' ? t.gradientEnd : t.bubbleA;

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<style>
  * { box-sizing:border-box;margin:0;padding:0; }
  body {
    width:1080px;height:1350px;
    font-family:${t.fontStack};
    background:${bgColor};
    display:flex;flex-direction:column;
    align-items:center;justify-content:center;
    padding:80px;
    position:relative;
    overflow:hidden;
  }
  .deco-circle {
    position:absolute;border-radius:50%;opacity:0.12;
  }
  .quote-mark {
    font-size:120px;line-height:1;
    color:${accentColor};
    font-family:Georgia,serif;
    margin-bottom:32px;
    align-self:flex-start;
    opacity:0.6;
  }
  .line1 {
    font-size:52px;font-weight:700;color:${t.inkDark};
    line-height:1.4;text-align:center;
    margin-bottom:28px;
    word-break:keep-all;
  }
  .line2 {
    font-size:32px;color:${t.inkSub};
    line-height:1.6;text-align:center;
    word-break:keep-all;
    margin-bottom:60px;
  }
  .divider {
    width:60px;height:3px;
    background:linear-gradient(90deg,${t.gradientStart},${t.gradientEnd});
    border-radius:2px;
    margin-bottom:36px;
  }
  .attribution {
    font-size:24px;font-weight:600;
    color:${accentColor};letter-spacing:2px;
  }
  .watermark {
    position:absolute;bottom:32px;right:40px;
    font-size:18px;color:${t.border};
  }
</style>
</head>
<body>
  <div class="deco-circle" style="width:400px;height:400px;background:${t.gradientStart};top:-100px;right:-80px;"></div>
  <div class="deco-circle" style="width:280px;height:280px;background:${t.gradientEnd};bottom:-60px;left:-40px;"></div>
  <div class="quote-mark">&ldquo;</div>
  <div class="line1">${escapeHtml(line1)}</div>
  ${line2 ? `<div class="line2">${escapeHtml(line2)}</div>` : ''}
  <div class="divider"></div>
  <div class="attribution">${escapeHtml(attribution)}</div>
  <div class="watermark">${t.watermark}</div>
</body>
</html>`;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

module.exports = { buildQuoteHtml };
