const t = require('../styles/tokens');

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function buildChatHtml(messages, title, subtitle) {
  const msgHtml = messages.map(msg => {
    const isMine = msg.sender === 'USER_A';
    const time = msg.createdAt
      ? new Date(msg.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
      : '';
    const senderLabel = isMine ? '나' : '다시봄 AI';

    return `
      <div style="
        display:flex;flex-direction:column;
        align-items:${isMine ? 'flex-end' : 'flex-start'};
        margin-bottom:16px;
      ">
        <div style="font-size:10px;color:${t.inkSub};margin-bottom:4px;padding:0 4px;">${senderLabel}</div>
        <div style="
          max-width:78%;padding:10px 14px;
          border-radius:${isMine ? '14px 14px 4px 14px' : '14px 14px 14px 4px'};
          background:${isMine ? t.bubbleA : t.bubbleAI};
          color:${t.inkDark};font-size:14px;line-height:1.6;word-break:break-word;
          border:${isMine ? 'none' : `1px solid ${t.border}`};
          box-shadow:0 1px 3px rgba(92,64,48,0.08);
        ">${escapeHtml(msg.content)}</div>
        ${time ? `<div style="font-size:10px;color:${t.inkSub};margin-top:4px;padding:0 4px;">${time}</div>` : ''}
      </div>
    `;
  }).join('');

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  * { box-sizing:border-box;margin:0;padding:0; }
  body {
    font-family:${t.fontStack};
    background:${t.bgWarm};height:100vh;
    display:flex;flex-direction:column;overflow:hidden;
  }
</style>
</head>
<body>
  <div style="
    background:${t.bgCard};border-bottom:1px solid ${t.border};
    padding:14px 20px 12px;display:flex;align-items:center;gap:10px;flex-shrink:0;
  ">
    <div style="
      width:36px;height:36px;border-radius:50%;
      background:linear-gradient(135deg,${t.gradientStart},${t.gradientEnd});
      display:flex;align-items:center;justify-content:center;
      font-size:16px;font-weight:700;color:#FFF;
    ">다</div>
    <div>
      <div style="font-size:14px;font-weight:700;color:${t.inkDark};">${escapeHtml(title)}</div>
      <div style="font-size:11px;color:${t.inkSub};">${escapeHtml(subtitle)}</div>
    </div>
  </div>
  <div style="
    flex:1;overflow:hidden;padding:16px 16px 0;
    display:flex;flex-direction:column;justify-content:flex-end;
  ">
    ${msgHtml}
  </div>
  <div style="
    background:${t.bgCard};border-top:1px solid ${t.border};
    padding:10px 16px;display:flex;align-items:center;gap:8px;flex-shrink:0;
  ">
    <div style="
      flex:1;background:${t.bgWarm};border:1px solid ${t.border};
      border-radius:20px;padding:8px 14px;font-size:13px;color:${t.inkSub};
    ">메시지를 입력하세요...</div>
    <div style="
      width:36px;height:36px;border-radius:50%;background:${t.bubbleA};
      display:flex;align-items:center;justify-content:center;font-size:18px;color:#FFF;
    ">&#9650;</div>
  </div>
  <div style="
    position:absolute;bottom:56px;right:16px;
    font-size:10px;color:${t.border};pointer-events:none;user-select:none;
  ">${t.watermark}</div>
</body>
</html>`;
}

module.exports = { buildChatHtml, escapeHtml };
