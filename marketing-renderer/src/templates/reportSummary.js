const t = require('../styles/tokens');

/**
 * 리포트 요약 다이어그램 (1080×1350)
 * mode: 'needs' | 'ratio' | 'combined'
 * report: { needsMap:{x,y,labelA,labelB,distance}, contributionRatio:{a,b}, metaphor? }
 */
function buildReportSummaryHtml({ report = {}, mode = 'combined' }) {
  const { needsMap = {}, contributionRatio = {}, metaphor } = report;

  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<style>
  * { box-sizing:border-box;margin:0;padding:0; }
  body {
    width:1080px;height:1350px;
    font-family:${t.fontStack};
    background:${t.bgWarm};
    display:flex;flex-direction:column;
    align-items:center;
    padding:80px 60px;
    gap:48px;
    position:relative;overflow:hidden;
  }
  h2 {
    font-size:42px;font-weight:700;color:${t.inkDark};text-align:center;
  }
  .sub { font-size:26px;color:${t.inkSub};text-align:center;margin-top:8px; }
  .card {
    background:${t.bgCard};border-radius:24px;padding:48px;width:100%;
    box-shadow:0 4px 20px rgba(92,64,48,0.06);
    border:1px solid ${t.border};
  }
  .watermark {
    position:absolute;bottom:32px;right:40px;
    font-size:18px;color:${t.border};
  }
</style>
</head>
<body>
  <div style="text-align:center;">
    <h2>두 사람의 이야기</h2>
    ${metaphor ? `<div class="sub">&ldquo;${escapeHtml(metaphor)}&rdquo;</div>` : ''}
  </div>

  ${(mode === 'needs' || mode === 'combined') ? buildNeedsMap(needsMap) : ''}
  ${(mode === 'ratio' || mode === 'combined') ? buildRatioCard(contributionRatio) : ''}

  <div class="watermark">${t.watermark}</div>
</body>
</html>`;
}

function buildNeedsMap({ labelA = 'A님', labelB = 'B님', distance }) {
  return `
    <div class="card">
      <div style="font-size:28px;font-weight:600;color:${t.inkDark};margin-bottom:32px;">필요와 감정 지도</div>
      <svg width="960" height="340" viewBox="0 0 960 340" style="display:block;margin:0 auto;">
        <!-- 축 -->
        <line x1="80" y1="270" x2="880" y2="270" stroke="${t.border}" stroke-width="2"/>
        <line x1="480" y1="40" x2="480" y2="290" stroke="${t.border}" stroke-width="2"/>
        <!-- 축 라벨 -->
        <text x="480" y="32" text-anchor="middle" font-size="18" fill="${t.inkSub}">연결 욕구</text>
        <text x="480" y="308" text-anchor="middle" font-size="18" fill="${t.inkSub}">자율 욕구</text>
        <text x="72" y="276" text-anchor="end" font-size="18" fill="${t.inkSub}">회피</text>
        <text x="888" y="276" text-anchor="start" font-size="18" fill="${t.inkSub}">접근</text>
        <!-- A 포인트 -->
        <circle cx="320" cy="130" r="22" fill="${t.bubbleA}" opacity="0.9"/>
        <text x="320" y="135" text-anchor="middle" font-size="16" fill="#FFF" font-weight="700">A</text>
        <text x="320" y="168" text-anchor="middle" font-size="18" fill="${t.inkDark}">${escapeHtml(labelA)}</text>
        <!-- B 포인트 -->
        <circle cx="640" cy="190" r="22" fill="${t.gradientEnd}" opacity="0.9"/>
        <text x="640" y="195" text-anchor="middle" font-size="16" fill="#FFF" font-weight="700">B</text>
        <text x="640" y="228" text-anchor="middle" font-size="18" fill="${t.inkDark}">${escapeHtml(labelB)}</text>
        <!-- 거리선 -->
        <line x1="320" y1="130" x2="640" y2="190" stroke="${t.border}" stroke-width="2" stroke-dasharray="8 4"/>
        ${distance ? `<text x="480" y="152" text-anchor="middle" font-size="16" fill="${t.inkSub}">거리: ${escapeHtml(String(distance))}</text>` : ''}
      </svg>
      <div style="font-size:22px;color:${t.inkSub};margin-top:20px;text-align:center;">
        두 사람이 원하는 것이 조금 달랐어요
      </div>
    </div>`;
}

function buildRatioCard({ a, b }) {
  const ratioA = typeof a === 'number' ? a : 50;
  const ratioB = typeof b === 'number' ? b : 50;

  return `
    <div class="card">
      <div style="font-size:28px;font-weight:600;color:${t.inkDark};margin-bottom:32px;">화해 기여도</div>
      <div style="display:flex;gap:24px;align-items:center;justify-content:center;">
        <div style="text-align:center;">
          <div style="
            width:180px;height:180px;border-radius:50%;
            background:linear-gradient(135deg,${t.bubbleA},${t.gradientStart});
            display:flex;align-items:center;justify-content:center;
            font-size:22px;font-weight:700;color:#FFF;
          ">A님</div>
        </div>
        <div style="
          flex:1;height:24px;border-radius:12px;
          background:linear-gradient(90deg,${t.bubbleA} ${ratioA}%,${t.gradientEnd} ${ratioA}%);
        "></div>
        <div style="text-align:center;">
          <div style="
            width:180px;height:180px;border-radius:50%;
            background:linear-gradient(135deg,${t.gradientEnd},${t.gradientStart});
            display:flex;align-items:center;justify-content:center;
            font-size:22px;font-weight:700;color:#FFF;
          ">B님</div>
        </div>
      </div>
      <div style="font-size:22px;color:${t.inkSub};margin-top:28px;text-align:center;">
        갈등은 한 사람만의 것이 아니에요
      </div>
      <div style="font-size:18px;color:${t.inkSub};margin-top:12px;text-align:center;opacity:0.7;">
        ※ 법적 과실비율과 무관합니다
      </div>
    </div>`;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

module.exports = { buildReportSummaryHtml };
