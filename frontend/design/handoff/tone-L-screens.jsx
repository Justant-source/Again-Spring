/* global React, Phone, PhoneHeader, Dashes, Logo, Strip */

// ────────────────────────────────────────────
// Tone L Screens
// ────────────────────────────────────────────

// 1. 랜딩
const LandingScreen = () => (
  <Phone tone="L">
    <div style={{ padding: '20px 28px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Logo />
        <span style={{ fontSize: 12, color: 'var(--L-sub)' }}>로그인</span>
      </div>

      <div style={{ marginTop: 100, flex: 1 }}>
        <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>관계 회복 AI 중재자</div>
        <div className="serif" style={{ fontSize: 32, lineHeight: 1.35, letterSpacing: '-0.01em' }}>
          지금, 누군가와<br />
          서운한 일이<br />
          있으신가요.
        </div>
        <div style={{ marginTop: 22, fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.7 }}>
          판결이 아니라, 중재입니다.<br />
          두 사람의 마음을 차분히 정리해드려요.
        </div>

        <div style={{ marginTop: 36, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <span className="chip-L">연인</span>
          <span className="chip-L">부부</span>
          <span className="chip-L">친구</span>
          <span className="chip-L">가족</span>
          <span className="chip-L">부모자식</span>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 16 }}>
        <button className="btn-L">이야기 시작하기</button>
        <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--L-sub)', marginTop: 4 }}>
          게스트로 둘러보기
        </div>
      </div>
    </div>
  </Phone>
);

// 2. 온보딩 10문항 — 슬라이더 방식
const OnboardingSlider = () => {
  const [val, setVal] = React.useState(3);
  return (
    <Phone tone="L">
      <PhoneHeader title="나의 대화 성향" tone="L" />
      <div style={{ padding: '8px 28px 28px' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={10} done={4} />
          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--L-sub)' }}>4 / 10</div>
        </div>

        <div className="quote-it" style={{ fontSize: 13, marginBottom: 14 }}>
          Question 4
        </div>
        <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, minHeight: 120 }}>
          상대가 내 감정을 먼저<br />알아주면, 문제는 저절로<br />풀린다고 느낀다.
        </div>

        <div style={{ marginTop: 44 }}>
          <div className="likert">
            {[1, 2, 3, 4, 5].map(n => (
              <button key={n} className={'likert-dot' + (n === val ? ' on' : '')} onClick={() => setVal(n)}>
                {n}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, fontSize: 11, color: 'var(--L-sub)' }}>
            <span>전혀 아니다</span>
            <span>매우 그렇다</span>
          </div>
        </div>

        <div style={{ marginTop: 48, display: 'flex', gap: 8 }}>
          <button className="btn-L ghost" style={{ flex: 1 }}>이전</button>
          <button className="btn-L" style={{ flex: 2 }}>다음</button>
        </div>
      </div>
    </Phone>
  );
};

// 2b. 온보딩 이모지 버전
const OnboardingEmoji = () => (
  <Phone tone="L">
    <PhoneHeader title="나의 대화 성향" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={10} done={7} />
        <div style={{ marginTop: 6, fontSize: 11, color: 'var(--L-sub)' }}>7 / 10</div>
      </div>

      <div className="quote-it" style={{ fontSize: 13, marginBottom: 14 }}>Question 7</div>
      <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, minHeight: 100 }}>
        사과할 때는 "내가 뭘<br />잘못했는지" 구체적으로<br />아는 게 중요하다.
      </div>

      <div style={{ marginTop: 36, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {[
          ['전혀 그렇지 않다', '◔'],
          ['별로 그렇지 않다', '◑'],
          ['보통이다', '◕'],
          ['그런 편이다', '●'],
          ['매우 그렇다', '⬤'],
        ].map(([label, mark], i) => (
          <div key={i} style={{
            padding: '14px 18px', border: '1px solid var(--L-border)', borderRadius: 3,
            display: 'flex', alignItems: 'center', gap: 14,
            background: i === 3 ? 'var(--L-ink)' : 'transparent',
            color: i === 3 ? 'var(--L-bg)' : 'var(--L-ink)',
          }}>
            <span style={{ fontSize: 16, opacity: 0.7 }}>{mark}</span>
            <span style={{ fontSize: 14 }}>{label}</span>
          </div>
        ))}
      </div>
    </div>
  </Phone>
);

// 2c. 문장형 (사용자가 문장을 골라 완성)
const OnboardingSentence = () => (
  <Phone tone="L">
    <PhoneHeader title="나의 대화 성향" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={10} done={2} />
        <div style={{ marginTop: 6, fontSize: 11, color: 'var(--L-sub)' }}>2 / 10</div>
      </div>

      <div className="quote-it" style={{ fontSize: 13, marginBottom: 14 }}>Question 2</div>
      <div className="serif" style={{ fontSize: 19, lineHeight: 1.7 }}>
        상대방이 감정적으로 격해지면,
      </div>

      <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {[
          '나도 같이 감정이 올라온다',
          '오히려 차분해진다',
          '대화를 멈추고 싶어진다',
          '상대를 진정시키려 애쓴다',
        ].map((t, i) => (
          <div key={i} style={{
            padding: '16px 18px', border: '1px solid var(--L-border)',
            borderRadius: 3, fontSize: 14, lineHeight: 1.5,
            fontFamily: 'var(--font-serif)',
            background: i === 0 ? 'var(--L-card)' : 'transparent',
            borderColor: i === 0 ? 'var(--L-ink)' : 'var(--L-border)',
          }}>
            {t}
          </div>
        ))}
      </div>
    </div>
  </Phone>
);

// 3. 대분류
const TreeBig = () => (
  <Phone tone="L">
    <PhoneHeader title="어떤 관계인가요" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={4} done={1} />
      </div>
      <div className="serif" style={{ fontSize: 22, lineHeight: 1.5, marginBottom: 28 }}>
        지금 이야기하실 분과의<br />관계를 알려주세요.
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {[
          ['연인 · 썸', '함께 알아가는 사이'],
          ['부부', '결혼으로 맺어진 사이'],
          ['친구', '오래 곁에 있는 사이'],
          ['가족', '형제자매 · 친척'],
          ['부모 · 자식', '키우고 자란 사이'],
        ].map(([name, desc], i) => (
          <div key={i} className="letter-card" style={{
            padding: '14px 18px',
            borderColor: i === 1 ? 'var(--L-ink)' : 'var(--L-border)',
            background: i === 1 ? 'var(--L-card)' : 'transparent',
          }}>
            <div style={{ fontSize: 15, fontWeight: 500 }}>{name}</div>
            <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 2 }}>{desc}</div>
          </div>
        ))}
      </div>
    </div>
  </Phone>
);

// 4. 중분류 (부부 · 육아 분담 맥락)
const TreeMid = () => (
  <Phone tone="L">
    <PhoneHeader title="어떤 일로 마음이 무거우신가요" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={4} done={2} />
      </div>
      <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>부부 →</div>
      <div className="serif" style={{ fontSize: 19, lineHeight: 1.5, marginBottom: 22 }}>
        마음에 걸리시는 일의<br />큰 갈래를 골라주세요.
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {[
          '육아 · 집안일 분담',
          '연락 · 관심',
          '시간 · 우선순위',
          '돈 · 경제',
          '가족 · 주변 관계',
          '애정 표현 차이',
          '생활 습관',
          '직접 입력',
        ].map((n, i) => (
          <div key={i} style={{
            padding: '14px 16px', borderBottom: '1px solid var(--L-border)',
            fontSize: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            color: i === 0 ? 'var(--L-ink)' : 'var(--L-sub)',
            fontWeight: i === 0 ? 500 : 400,
          }}>
            <span>{n}</span>
            <span style={{ fontSize: 14, color: 'var(--L-sub)' }}>›</span>
          </div>
        ))}
      </div>
    </div>
  </Phone>
);

// 5. 소분류
const TreeSmall = () => (
  <Phone tone="L">
    <PhoneHeader title="조금 더 구체적으로" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={4} done={3} />
      </div>
      <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>부부 · 육아 분담 →</div>
      <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 22 }}>
        가장 가까운 상황을<br />골라주세요.
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {[
          '밤중 수유 · 돌봄 분담',
          '집안일이 한쪽에 쏠림',
          '주말 육아 시간 배분',
          '부모님 도움 받는 방식',
          '육아 방식 자체가 다름',
          '직접 입력',
        ].map((n, i) => (
          <div key={i} style={{
            padding: '14px 16px', border: '1px solid',
            borderColor: i === 1 ? 'var(--L-ink)' : 'var(--L-border)',
            borderRadius: 3, fontSize: 14,
            background: i === 1 ? 'var(--L-card)' : 'transparent',
          }}>
            {n}
          </div>
        ))}
      </div>
    </div>
  </Phone>
);

// 6. 상황 서술
const InputDescribe = () => (
  <Phone tone="L">
    <PhoneHeader title="당신의 마음을 들려주세요" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ marginBottom: 28 }}>
        <Dashes n={4} done={4} />
      </div>
      <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>부부 · 육아 분담 · 집안일이 한쪽에 쏠림</div>
      <div className="serif" style={{ fontSize: 19, lineHeight: 1.5, marginTop: 12, marginBottom: 18 }}>
        어떤 일이 있었는지<br />편한 말로 적어주세요.
      </div>

      <div style={{ position: 'relative' }}>
        <div style={{ fontSize: 14, lineHeight: 1.8, color: 'var(--L-ink)', minHeight: 160, paddingBottom: 10, borderBottom: '1px solid var(--L-ink)' }}>
          둘 다 맞벌이인데 아이 하원,
          저녁 준비, 설거지, 목욕까지
          대부분 제가 하고 있어요. 주말에도
          저는 쉬는 시간이 거의 없는데 남편은
          누워서 쉬는 모습을 보면 서운하고
          화가 날 때가 많아요<span style={{ borderRight: '1.5px solid var(--L-ink)', marginLeft: 1, animation: 'blink 1s infinite' }}>&nbsp;</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: 11, color: 'var(--L-sub)' }}>
          <span>상대의 이름은 쓰지 않으셔도 괜찮아요</span>
          <span>142 / 600</span>
        </div>
      </div>

      <div style={{ marginTop: 28, fontSize: 12, color: 'var(--L-sub)', lineHeight: 1.6 }}>
        · 적으신 내용은 상대방에게 그대로 보이지 않아요.<br />
        · 중재자가 따뜻한 언어로 정리해 전달합니다.
      </div>

      <button className="btn-L" style={{ width: '100%', marginTop: 24 }}>다음 — 상대에게 초대 보내기</button>
    </div>
  </Phone>
);

// 7. 초대 링크 생성
const InvitePick = () => {
  const [tone, setTone] = React.useState(0);
  const tones = [
    { k: '부드럽게', t: '우리 얘기 좀 정리해보고 싶어서\n다시봄에 내 생각을 적어봤어.\n너 생각도 듣고 싶은데, 같이 해볼래?' },
    { k: '가볍게', t: '요즘 관계 AI 중재자 같은 게 있더라고.\n내가 먼저 써봤어. 너도 해볼래?\n둘 다 입력해야 결과가 나온대.' },
    { k: '진지하게', t: '우리가 최근에 부딪혔던 일에 대해\n서로 정리할 시간이 필요한 것 같아.\n중재자가 도와주는 앱인데, 같이 해줄 수 있어?' },
  ];
  return (
    <Phone tone="L">
      <PhoneHeader title="상대에게 어떻게 보낼까요" tone="L" />
      <div style={{ padding: '8px 28px 28px' }}>
        <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 20 }}>
          말투 하나에도<br />마음이 실리니까요.
        </div>

        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          {tones.map((t, i) => (
            <button key={i} onClick={() => setTone(i)} style={{
              flex: 1, padding: '8px 0', fontSize: 12, cursor: 'pointer',
              background: i === tone ? 'var(--L-ink)' : 'transparent',
              color: i === tone ? 'var(--L-bg)' : 'var(--L-sub)',
              border: '1px solid ' + (i === tone ? 'var(--L-ink)' : 'var(--L-border)'),
              borderRadius: 3,
            }}>{t.k}</button>
          ))}
        </div>

        <div className="letter-card" style={{ padding: 22 }}>
          <div style={{ fontSize: 14, lineHeight: 1.8, whiteSpace: 'pre-line', fontFamily: 'var(--font-serif)' }}>
            {tones[tone].t}
          </div>
          <div style={{ marginTop: 14, fontSize: 12, color: 'var(--L-sub)', borderTop: '1px solid var(--L-border)', paddingTop: 10 }}>
            again-spring.com/s/9k2f
          </div>
        </div>

        <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <button className="btn-L" style={{ background: '#FEE500', color: '#3C1E1E' }}>카카오톡으로 보내기</button>
          <button className="btn-L ghost">문자 · 링크 복사</button>
        </div>
      </div>
    </Phone>
  );
};

// 8. B 참여 대기
const WaitingB = () => (
  <Phone tone="L">
    <PhoneHeader title="" tone="L" back={false} />
    <div style={{ padding: '40px 28px 28px', textAlign: 'center' }}>
      <div style={{
        width: 72, height: 72, borderRadius: '50%',
        border: '1px solid var(--L-border)', margin: '0 auto',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative',
      }}>
        <div style={{
          position: 'absolute', inset: -6, border: '1px solid var(--L-border)',
          borderRadius: '50%', opacity: 0.5,
        }} />
        <div className="serif" style={{ fontSize: 13, color: 'var(--L-sub)' }}>기다림</div>
      </div>

      <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginTop: 28 }}>
        상대방의 도착을<br />기다리고 있어요.
      </div>

      <div style={{ marginTop: 16, fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7 }}>
        초대를 보낸 지 <span style={{ color: 'var(--L-ink)' }}>4시간 12분</span> 지났어요.<br />
        24시간 안에 도착하지 않으면<br />혼자 정리하는 모드로 바꿀 수 있어요.
      </div>

      <div className="letter-card" style={{ marginTop: 36, textAlign: 'left' }}>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 6 }}>한 줄 안내</div>
        <div className="quote-it" style={{ fontSize: 14, lineHeight: 1.7 }}>
          지금은 아무것도 하지 않으셔도 괜찮아요. 숨을 한 번 고르는 시간일지도 몰라요.
        </div>
      </div>

      <div style={{ marginTop: 32, display: 'flex', gap: 8 }}>
        <button className="btn-L ghost" style={{ flex: 1 }}>더 기다리기</button>
        <button className="btn-L" style={{ flex: 1 }}>혼자 정리</button>
      </div>
    </div>
  </Phone>
);

// 9. B 도착 — 요약 열람
const BSummary = () => (
  <Phone tone="L">
    <PhoneHeader title="서현님이 보내온 마음" tone="L" />
    <div style={{ padding: '8px 28px 28px' }}>
      <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
        중재자의 요약 · 원문은 서로의 답변 후 공개돼요
      </div>

      <div className="letter-card" style={{ padding: 24 }}>
        <div className="quote-it" style={{ fontSize: 12, marginBottom: 14 }}>서현님의 이야기</div>
        <div className="serif" style={{ fontSize: 15, lineHeight: 1.8 }}>
          서현님은 맞벌이 상황에서 집안일과 육아가 한쪽으로 기울고 있다고 느끼셨어요. 특히 주말에도 쉬지 못하는 날이 반복되면서, 곁에서 쉬고 있는 모습을 볼 때 서운함과 지침이 함께 올라온다고 하셨습니다.
        </div>
      </div>

      <div style={{ marginTop: 22, fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7 }}>
        이제 준호님의 이야기도 들려주세요.<br />
        두 사람의 이야기가 모이면 중재가 시작돼요.
      </div>

      <button className="btn-L" style={{ width: '100%', marginTop: 24 }}>내 이야기 적기</button>
    </div>
  </Phone>
);

Object.assign(window, {
  LandingScreen, OnboardingSlider, OnboardingEmoji, OnboardingSentence,
  TreeBig, TreeMid, TreeSmall, InputDescribe, InvitePick,
  WaitingB, BSummary,
});
