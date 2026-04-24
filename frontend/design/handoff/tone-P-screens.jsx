/* global React, Phone, PhoneHeader */

// ────────────────────────────────────────────
// Tone P — Result Report Screens + Share images + Solo
// ────────────────────────────────────────────

// 욕구 차이 지도 — Variation 1 : 2D 좌표
const NeedsMap2D = ({ size = 280 }) => (
  <div style={{ position: 'relative', width: size, height: size, margin: '0 auto' }}>
    {/* Axes */}
    <div style={{ position: 'absolute', top: '50%', left: 0, right: 0, height: 1, background: 'var(--P-border)' }} />
    <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, width: 1, background: 'var(--P-border)' }} />
    {/* Tick grid */}
    {[0.25, 0.75].map(p => (
      <React.Fragment key={p}>
        <div style={{ position: 'absolute', top: `${p * 100}%`, left: 0, right: 0, height: 1, background: 'var(--P-border)', opacity: 0.5 }} />
        <div style={{ position: 'absolute', left: `${p * 100}%`, top: 0, bottom: 0, width: 1, background: 'var(--P-border)', opacity: 0.5 }} />
      </React.Fragment>
    ))}
    {/* Axis labels */}
    <div style={{ position: 'absolute', top: -14, left: '50%', transform: 'translateX(-50%)', fontFamily: 'var(--font-serif)', fontStyle: 'italic', fontSize: 13, color: 'var(--P-sub)' }}>자율</div>
    <div style={{ position: 'absolute', bottom: -14, left: '50%', transform: 'translateX(-50%)', fontFamily: 'var(--font-serif)', fontStyle: 'italic', fontSize: 13, color: 'var(--P-sub)' }}>연결</div>
    <div style={{ position: 'absolute', top: '50%', left: -6, transform: 'translate(-100%, -50%)', fontFamily: 'var(--font-serif)', fontStyle: 'italic', fontSize: 13, color: 'var(--P-sub)' }}>안정</div>
    <div style={{ position: 'absolute', top: '50%', right: -6, transform: 'translate(100%, -50%)', fontFamily: 'var(--font-serif)', fontStyle: 'italic', fontSize: 13, color: 'var(--P-sub)' }}>변화</div>

    {/* A dot */}
    <div style={{ position: 'absolute', top: '22%', left: '68%', transform: 'translate(-50%,-50%)' }}>
      <div style={{ width: 22, height: 22, borderRadius: '50%', background: 'var(--P-a)', boxShadow: '0 2px 10px rgba(244,168,150,0.5)' }} />
      <div style={{ position: 'absolute', top: -4, left: 28, fontSize: 12, color: 'var(--P-ink)', fontWeight: 500 }}>서현</div>
    </div>
    {/* B dot */}
    <div style={{ position: 'absolute', top: '72%', left: '32%', transform: 'translate(-50%,-50%)' }}>
      <div style={{ width: 22, height: 22, borderRadius: '50%', background: 'var(--P-b)', boxShadow: '0 2px 10px rgba(168,200,180,0.5)' }} />
      <div style={{ position: 'absolute', top: -4, right: 28, fontSize: 12, color: 'var(--P-ink)', fontWeight: 500 }}>준호</div>
    </div>
    {/* Connecting dashed line */}
    <svg style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }} viewBox={`0 0 ${size} ${size}`}>
      <line x1={size * 0.68} y1={size * 0.22} x2={size * 0.32} y2={size * 0.72}
        stroke="var(--P-sub)" strokeWidth="1" strokeDasharray="3 4" opacity="0.6" />
    </svg>
  </div>
);

// Variation 2 — Venn
const NeedsMapVenn = ({ size = 280 }) => (
  <div style={{ position: 'relative', width: size, height: size * 0.7, margin: '0 auto' }}>
    <svg viewBox="0 0 280 196" width={size} height={size * 0.7}>
      <circle cx="100" cy="98" r="78" fill="var(--P-a)" opacity="0.42" />
      <circle cx="180" cy="98" r="78" fill="var(--P-b)" opacity="0.42" />
      <text x="60" y="102" fontSize="14" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">자율</text>
      <text x="200" y="102" fontSize="14" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">연결</text>
      <text x="130" y="102" fontSize="11" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">함께</text>
      <text x="40" y="30" fontSize="12" fill="var(--P-ink)" fontWeight="500">서현</text>
      <text x="210" y="30" fontSize="12" fill="var(--P-ink)" fontWeight="500">준호</text>
    </svg>
  </div>
);

// Variation 3 — 거리 막대 (축별 갭)
const NeedsMapBars = ({ w = 280 }) => (
  <div style={{ width: w, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
    {[
      ['자율', '연결', 0.68, 0.32],
      ['안정', '변화', 0.22, 0.72],
      ['계획', '즉흥', 0.45, 0.55],
    ].map(([l, r, a, b], i) => (
      <div key={i}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--P-sub)', fontFamily: 'var(--font-serif)', fontStyle: 'italic', marginBottom: 6 }}>
          <span>{l}</span><span>{r}</span>
        </div>
        <div style={{ position: 'relative', height: 10, background: 'var(--P-card)', border: '1px solid var(--P-border)', borderRadius: 5 }}>
          <div style={{ position: 'absolute', top: '50%', left: `${a * 100}%`, transform: 'translate(-50%,-50%)', width: 14, height: 14, borderRadius: '50%', background: 'var(--P-a)' }} />
          <div style={{ position: 'absolute', top: '50%', left: `${b * 100}%`, transform: 'translate(-50%,-50%)', width: 14, height: 14, borderRadius: '50%', background: 'var(--P-b)' }} />
        </div>
      </div>
    ))}
  </div>
);

// Signature screen — 욕구 차이 지도 full
const SignatureMap = ({ variant = '2d' }) => (
  <Phone tone="P" scroll>
    <PhoneHeader title="욕구 차이 지도" tone="P" />
    <div style={{ padding: '8px 22px 28px', textAlign: 'center' }}>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 6 }}>다시봄 · 부부</div>
      <div style={{ fontSize: 18, color: 'var(--P-ink)', fontWeight: 500, marginBottom: 24, fontFamily: 'var(--font-serif)' }}>
        두 사람의 마음 풍경
      </div>

      <div style={{ padding: '28px 8px', background: 'var(--P-card)', border: '1px solid var(--P-border)', borderRadius: 20, marginBottom: 16 }}>
        {variant === '2d' && <NeedsMap2D size={260} />}
        {variant === 'venn' && <NeedsMapVenn size={260} />}
        {variant === 'bars' && <NeedsMapBars w={240} />}
      </div>

      <div style={{ fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.8, padding: '0 6px', textAlign: 'left' }}>
        <span style={{ fontFamily: 'var(--font-serif)' }}>두 분은 </span>
        <span style={{ fontWeight: 500 }}>"자율 – 연결" 축</span>
        <span style={{ fontFamily: 'var(--font-serif)' }}>에서 조금 거리가 있어요. 서로 다른 방식으로 쉼을 찾고 계시는 것 같습니다.</span>
      </div>

      <div style={{ marginTop: 18, display: 'flex', gap: 8, justifyContent: 'center' }}>
        <span className="chip-P"><span className="dot dot-a" /> 서현 · 파도형</span>
        <span className="chip-P"><span className="dot dot-b" /> 준호 · 산형</span>
      </div>
    </div>
  </Phone>
);

// 관계 온도 & 화해 기여도 + Four Horsemen — 카드형 리포트
const ReportCards = () => (
  <Phone tone="P" scroll>
    <PhoneHeader title="우리의 오늘 리포트" tone="P" />
    <div style={{ padding: '8px 22px 40px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Temp card */}
      <div className="p-card" style={{ textAlign: 'center', padding: 24 }}>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}><IconTemp size={13} /> 관계 온도</div>
        <div style={{ fontSize: 56, fontWeight: 500, fontFamily: 'var(--font-serif)', letterSpacing: '-0.03em', marginTop: 6 }}>
          36.2<span style={{ fontSize: 24 }}>°C</span>
        </div>
        <div style={{ marginTop: 10, position: 'relative', height: 6, background: 'var(--P-bg)', borderRadius: 4 }}>
          <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '55%', background: 'linear-gradient(90deg, var(--P-b), var(--P-a))', borderRadius: 4 }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: 'var(--P-sub)', marginTop: 6 }}>
          <span>35.0</span><span>평균 36.5</span><span>37.5</span>
        </div>
        <div style={{ marginTop: 14, fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.6 }}>
          살짝 내려가 있지만, 회복의 범위 안에 있어요.
        </div>
      </div>

      {/* Ratio */}
      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>화해 기여도</div>
        <div style={{ display: 'flex', height: 44, borderRadius: 10, overflow: 'hidden' }}>
          <div style={{ flex: 55, background: 'var(--P-a)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#5C4030', fontWeight: 500, fontSize: 14 }}>서현 · 55</div>
          <div style={{ flex: 45, background: 'var(--P-b)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#3F4F45', fontWeight: 500, fontSize: 14 }}>준호 · 45</div>
        </div>
        <div style={{ marginTop: 14, fontSize: 13, lineHeight: 1.7 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-a)', fontWeight: 500, minWidth: 56 }}>서현</span>
            <span>먼저 다가가면 좋은 쪽</span>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginTop: 6 }}>
            <span style={{ color: '#6B9080', fontWeight: 500, minWidth: 56 }}>준호</span>
            <span>마음을 열고 기다려주면 좋은 쪽</span>
          </div>
        </div>
      </div>

      {/* Four Horsemen */}
      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>대화 패턴 살펴보기</div>
        {[
          ['비판보다는 관찰', 0.8, '구체적인 상황을 잘 말씀하셨어요'],
          ['방어보다는 이해', 0.55, '조금 더 들어봐도 좋을 것 같아요'],
          ['경멸은 보이지 않음', 0.95, '존중이 잘 지켜졌어요'],
          ['담쌓기보다는 머무름', 0.7, '대화에 계속 함께하셨어요'],
        ].map(([label, v, desc], i) => (
          <div key={i} style={{ marginBottom: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
              <span>{label}</span>
              <span style={{ color: 'var(--P-sub)', fontSize: 11 }}>{desc}</span>
            </div>
            <div style={{ height: 4, background: 'var(--P-bg)', borderRadius: 2, marginTop: 6 }}>
              <div style={{ height: '100%', width: `${v * 100}%`, background: 'var(--P-a)', opacity: 0.7, borderRadius: 2 }} />
            </div>
          </div>
        ))}
      </div>

      {/* Repair suggestions */}
      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>오늘 건넬 수 있는 말</div>
        {[
          ['서현 → 준호', '평일에 네가 야근으로 지친 건 알았어. 나도 말 꺼내기가 어려웠어.'],
          ['준호 → 서현', '주말마다 네가 혼자 떠안고 있는 줄 몰랐어. 미안하고, 같이 정리하자.'],
        ].map(([who, t], i) => (
          <div key={i} style={{ marginBottom: 10 }}>
            <div style={{ fontSize: 11, color: 'var(--P-sub)', marginBottom: 4 }}>{who}</div>
            <div style={{ padding: '12px 14px', border: '1px dashed var(--P-border)', borderRadius: 10, fontFamily: 'var(--font-serif)', fontSize: 14, lineHeight: 1.7 }}>
              "{t}"
            </div>
          </div>
        ))}
      </div>

      <button className="btn-P" style={{ marginTop: 4 }}>카톡으로 리포트 공유</button>
    </div>
  </Phone>
);

// 리포트 — 스토리형 (수직 스크롤, 큰 블록)
const ReportStory = () => (
  <Phone tone="P" scroll>
    <div style={{ padding: '40px 26px 28px' }}>
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>2026.04.24</div>
      <div className="serif" style={{ fontSize: 28, lineHeight: 1.35, marginTop: 10, fontWeight: 500 }}>
        오늘,<br />두 분의 이야기는<br />따뜻하게 마무리되었어요.
      </div>
    </div>

    <div style={{ padding: '20px 26px', background: 'var(--P-card)', margin: '0 18px', borderRadius: 20 }}>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', gap: 6 }}><IconTemp size={13} /> 관계 온도</div>
      <div style={{ fontSize: 72, fontFamily: 'var(--font-serif)', fontWeight: 500, letterSpacing: '-0.04em', lineHeight: 1 }}>36.2°</div>
      <div style={{ fontSize: 13, color: 'var(--P-ink)', marginTop: 6 }}>지난번 36.0°보다 0.2° 올랐어요.</div>
    </div>

    <div style={{ padding: '28px 26px' }}>
      <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>갈등 유형</div>
      <div className="serif" style={{ fontSize: 22, marginTop: 8 }}>차이형 · 혼합형</div>
      <div style={{ fontSize: 14, color: 'var(--P-ink)', lineHeight: 1.8, marginTop: 10 }}>
        누구의 잘못이라기보다, 두 분이 "쉼"을 서로 다른 방식으로 필요로 하고 계신다는 것이 뚜렷하게 보였어요.
      </div>
    </div>

    <div style={{ padding: '20px 26px 40px' }}>
      <NeedsMap2D size={260} />
    </div>

    <div style={{ padding: '0 26px 28px', display: 'flex', gap: 10 }}>
      <button className="btn-P" style={{ flex: 1 }}>카톡 공유</button>
      <button className="btn-P ghost" style={{ flex: 1 }}>PDF로 보관</button>
    </div>
  </Phone>
);

// 9:16 카톡 공유 이미지 3종 (실제 크기 축소)
const ShareImage = ({ variant = 'map' }) => {
  const W = 270, H = 480;
  if (variant === 'map') {
    return (
      <div style={{
        width: W, height: H, background: 'var(--P-bg)',
        borderRadius: 18, padding: '36px 28px', position: 'relative',
        border: '1px solid var(--P-border)',
        display: 'flex', flexDirection: 'column', fontFamily: 'var(--font-sans)', color: 'var(--P-ink)',
      }}>
        <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · 부부</div>
        <div className="serif" style={{ fontSize: 20, marginTop: 8, lineHeight: 1.4 }}>우리의<br />마음 풍경</div>
        <div style={{ flex: 1, display: 'flex', alignItems: 'center' }}>
          <NeedsMap2D size={200} />
        </div>
        <div style={{ fontSize: 11, color: 'var(--P-sub)', textAlign: 'center' }}>
          again-spring.com
        </div>
      </div>
    );
  }
  if (variant === 'temp') {
    return (
      <div style={{
        width: W, height: H, background: 'var(--P-card)',
        borderRadius: 18, padding: '48px 28px', position: 'relative',
        border: '1px solid var(--P-border)',
        display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center',
        color: 'var(--P-ink)',
      }}>
        <div style={{ fontSize: 11, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}><IconTemp size={12} /> 관계 온도</div>
        <div className="serif" style={{ fontSize: 96, fontWeight: 500, lineHeight: 1, letterSpacing: '-0.04em', marginTop: 30 }}>
          36.2°
        </div>
        <div style={{ marginTop: 18, width: '100%', height: 6, background: 'var(--P-bg)', borderRadius: 4 }}>
          <div style={{ width: '55%', height: '100%', background: 'linear-gradient(90deg, var(--P-b), var(--P-a))', borderRadius: 4 }} />
        </div>
        <div style={{ marginTop: 30, fontFamily: 'var(--font-serif)', fontSize: 14, lineHeight: 1.7 }}>
          살짝 내려가 있지만,<br />회복의 범위 안에 있어요.
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · again-spring.com</div>
      </div>
    );
  }
  // style card
  return (
    <div style={{
      width: W, height: H, background: 'var(--P-bg)',
      borderRadius: 18, padding: '36px 28px',
      border: '1px solid var(--P-border)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center',
      color: 'var(--P-ink)',
    }}>
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>우리의 대화 스타일</div>

      <div style={{ display: 'flex', gap: 14, marginTop: 30, alignItems: 'center' }}>
        <div>
          <div style={{ width: 64, height: 64, borderRadius: '50%', background: 'var(--P-a)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#5C4030' }}><MotifWave size={30} /></div>
          <div className="serif" style={{ marginTop: 8, fontSize: 14 }}>파도형</div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>서현</div>
        </div>
        <div style={{ fontFamily: 'var(--font-serif)', fontSize: 18, color: 'var(--P-sub)' }}>×</div>
        <div>
          <div style={{ width: 64, height: 64, borderRadius: '50%', background: 'var(--P-b)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#3F4F45' }}><MotifMountain size={30} /></div>
          <div className="serif" style={{ marginTop: 8, fontSize: 14 }}>산형</div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>준호</div>
        </div>
      </div>

      <div style={{ marginTop: 28, fontFamily: 'var(--font-serif)', fontSize: 13, lineHeight: 1.8 }}>
        감정을 파도처럼 표현하는 사람과<br />조용히 산처럼 받아내는 사람
      </div>

      <div style={{ flex: 1 }} />
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · again-spring.com</div>
    </div>
  );
};

// Solo 모드 결과
const SoloResult = () => (
  <Phone tone="P" scroll>
    <PhoneHeader title="혼자 정리한 이야기" tone="P" />
    <div style={{ padding: '8px 22px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{
        padding: '10px 14px', background: 'var(--P-card)',
        border: '1px solid var(--P-border)', borderRadius: 12,
        fontSize: 12, color: 'var(--P-sub)',
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--P-a)' }} />
        한쪽 분석 · 완전한 리포트는 상대가 참여하면 완성돼요
      </div>

      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>서현님 입장에서의 정리</div>
        <div style={{ fontFamily: 'var(--font-serif)', fontSize: 14, lineHeight: 1.9, marginTop: 12, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}><span style={{ color: 'var(--P-sub)', marginTop: 2 }}><IconEye size={15} /></span><div><b style={{ fontWeight: 500 }}>관찰</b> · 주말에도 집안일과 육아가 한쪽에 쏠리는 상황이 반복되었어요.</div></div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}><span style={{ color: 'var(--P-sub)', marginTop: 2 }}><IconDrop size={15} /></span><div><b style={{ fontWeight: 500 }}>느낌</b> · 서운함과 지침이 함께 올라왔어요.</div></div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}><span style={{ color: 'var(--P-sub)', marginTop: 2 }}><IconNeed size={15} /></span><div><b style={{ fontWeight: 500 }}>욕구</b> · 함께 돌본다는 감각과, 회복할 시간.</div></div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}><span style={{ color: 'var(--P-sub)', marginTop: 2 }}><IconAsk size={15} /></span><div><b style={{ fontWeight: 500 }}>부탁</b> · 주말에 한두 시간만이라도 먼저 움직여 주기를 바라요.</div></div>
        </div>
      </div>

      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8 }}>당신의 대화 스타일</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'var(--P-a)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#5C4030' }}><MotifWave size={24} /></div>
          <div>
            <div className="serif" style={{ fontSize: 16 }}>파도형</div>
            <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>감정이 풍부하고 즉각적인 편</div>
          </div>
        </div>
      </div>

      <div className="p-card" style={{ opacity: 0.55 }}>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', gap: 6 }}><IconMap size={13} /> 욕구 차이 지도</div>
        <div style={{ textAlign: 'center', padding: '36px 0', fontFamily: 'var(--font-serif)', fontSize: 13, color: 'var(--P-sub)' }}>
          두 분이 함께 해야 그려져요
        </div>
      </div>

      <div style={{
        padding: '18px', background: 'var(--P-a)', color: 'var(--P-ink)',
        borderRadius: 14, textAlign: 'center',
      }}>
        <div style={{ fontSize: 13, lineHeight: 1.6, fontFamily: 'var(--font-serif)', marginBottom: 12 }}>
          지금이라도 준호님을 초대하면<br />두 분의 리포트가 완성돼요.
        </div>
        <button style={{
          background: 'var(--P-ink)', color: 'var(--P-card)',
          border: 'none', borderRadius: 10, padding: '12px 22px',
          fontSize: 14, fontWeight: 500, cursor: 'pointer',
        }}>초대 링크 다시 보내기</button>
      </div>
    </div>
  </Phone>
);

Object.assign(window, {
  NeedsMap2D, NeedsMapVenn, NeedsMapBars,
  SignatureMap, ReportCards, ReportStory, ShareImage, SoloResult,
});
