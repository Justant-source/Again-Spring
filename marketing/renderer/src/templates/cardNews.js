const t = require('../styles/tokens');

/**
 * 인스타 카드뉴스 슬라이드 HTML (1080×1350)
 * role: COVER | SCENE | FEELING | NVC | RATIO | CTA | BONUS
 */
function buildCardSlideHtml({ role, title, body, visualHint, slideNumber, totalSlides }) {
  const layout = layoutByRole(role, { title, body, visualHint });
  const indicator = buildSlideIndicator(slideNumber, totalSlides);

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<style>
  * { box-sizing:border-box;margin:0;padding:0; }
  body {
    width:1080px;height:1080px;
    font-family:${t.fontStack};
    background:${t.bgWarm};
    display:flex;flex-direction:column;
    position:relative;overflow:hidden;
  }
  .header {
    background:linear-gradient(135deg,${t.gradientStart},${t.gradientEnd});
    padding:32px 48px 22px;flex-shrink:0;
  }
  .header-logo {
    font-size:20px;font-weight:700;color:#FFF;letter-spacing:1px;opacity:0.9;
  }
  .header-role {
    font-size:14px;color:rgba(255,255,255,0.7);margin-top:4px;
  }
  .content {
    flex:1;display:flex;flex-direction:column;
    align-items:center;justify-content:center;
    padding:40px 60px;gap:24px;
  }
  .slide-title {
    font-size:48px;font-weight:700;color:${t.inkDark};
    text-align:center;line-height:1.35;word-break:keep-all;
  }
  .slide-body {
    font-size:28px;color:${t.inkSub};
    text-align:center;line-height:1.7;word-break:keep-all;
    white-space:pre-line;
  }
  .footer {
    padding:20px 48px;
    display:flex;align-items:center;justify-content:space-between;
    border-top:1px solid ${t.border};flex-shrink:0;
  }
  .watermark { font-size:16px;color:${t.inkSub}; }
</style>
</head>
<body>
  <div class="header">
    <div class="header-logo">다시봄</div>
    <div class="header-role">${escapeHtml(roleLabel(role))}</div>
  </div>
  <div class="content">
    ${layout}
  </div>
  <div class="footer">
    ${indicator}
    <div class="watermark">${t.watermark}</div>
  </div>
</body>
</html>`;
}

function layoutByRole(role, { title, body }) {
  const titleHtml = `<div class="slide-title">${escapeHtml(title)}</div>`;
  const bodyHtml = body ? `<div class="slide-body">${escapeHtml(body)}</div>` : '';

  switch (role) {
    case 'COVER':
      return `
        <div style="
          width:80px;height:6px;border-radius:3px;margin-bottom:28px;
          background:linear-gradient(90deg,${t.gradientStart},${t.gradientEnd});
        "></div>
        <div class="slide-title" style="font-size:54px;">${escapeHtml(title)}</div>
        ${bodyHtml}
      `;
    case 'SCENE':
      return `
        <div style="
          background:${t.bgCard};border:1px solid ${t.border};
          border-radius:20px;padding:48px 56px;
          font-size:38px;color:${t.inkDark};line-height:1.6;
          text-align:center;word-break:keep-all;
          box-shadow:0 4px 16px rgba(92,64,48,0.08);
        ">&ldquo;${escapeHtml(body || title)}&rdquo;</div>
      `;
    case 'FEELING':
      return `
        ${titleHtml}
        <div style="display:flex;gap:24px;flex-wrap:wrap;justify-content:center;margin-top:8px;">
          ${(body || '').split('·').map(e => `
            <div style="
              background:${t.bubbleA};border-radius:40px;
              padding:16px 36px;font-size:30px;color:#FFF;font-weight:600;
            ">${escapeHtml(e.trim())}</div>
          `).join('')}
        </div>
      `;
    case 'NVC':
      return `
        ${titleHtml}
        <div style="
          background:${t.bgCard};border-radius:20px;padding:48px 56px;width:100%;
          font-size:30px;color:${t.inkDark};line-height:1.8;
          border-left:6px solid ${t.bubbleA};
        ">${escapeHtml(body || '').replace(/\n/g, '<br>')}</div>
      `;
    case 'RATIO':
      return `
        ${titleHtml}
        <div style="
          width:100%;height:48px;border-radius:24px;
          background:linear-gradient(90deg,${t.bubbleA} 0%,${t.bubbleA} 50%,${t.gradientEnd} 50%,${t.gradientEnd} 100%);
          margin-top:16px;
        "></div>
        ${bodyHtml}
      `;
    case 'CTA':
      return `
        <div style="
          width:100px;height:100px;border-radius:50%;
          background:linear-gradient(135deg,${t.gradientStart},${t.gradientEnd});
          display:flex;align-items:center;justify-content:center;
          font-size:44px;font-weight:700;color:#FFF;margin-bottom:8px;
        ">다</div>
        ${titleHtml}
        ${bodyHtml}
        <div style="
          background:${t.bubbleA};border-radius:40px;
          padding:20px 56px;font-size:28px;font-weight:700;color:#FFF;margin-top:16px;
        ">지금 시작하기</div>
      `;
    default:
      return `${titleHtml}${bodyHtml}`;
  }
}

function buildSlideIndicator(current, total) {
  const dots = Array.from({ length: total }, (_, i) => {
    const active = i + 1 === current;
    return `<div style="
      width:${active ? 28 : 10}px;height:10px;border-radius:5px;
      background:${active ? t.bubbleA : t.border};
      transition:width 0.2s;
    "></div>`;
  }).join('');
  return `<div style="display:flex;gap:8px;align-items:center;">${dots}</div>`;
}

function roleLabel(role) {
  const map = { COVER:'커버', SCENE:'갈등 장면', FEELING:'감정', NVC:'관찰과 욕구', RATIO:'기여도', CTA:'시작하기', BONUS:'인사이트' };
  return map[role] || role;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

module.exports = { buildCardSlideHtml };
