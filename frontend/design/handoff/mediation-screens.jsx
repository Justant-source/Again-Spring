/* global React, Phone, PhoneHeader, Dashes, Logo */

// ────────────────────────────────────────────
// 6턴 중재 세션 — 3가지 variation
// ────────────────────────────────────────────

// Variation A — 편지지 풀화면 (한 턴 한 편지)
const MediationLetter = () => (
  <Phone tone="L" scroll>
    <PhoneHeader title="3 / 6 턴 · 중재자의 편지" tone="L" />
    <div style={{ padding: '8px 28px 40px' }}>
      <div className="quote-it" style={{ fontSize: 13, marginBottom: 14 }}>
        준호님께 — 중재자가
      </div>
      <div className="serif" style={{ fontSize: 16, lineHeight: 1.9, letterSpacing: '-0.005em' }}>
        서현님은 요즘, 집안일과 육아가
        <br />한쪽으로 기울고 있다고 느끼셨대요.
        <br />특히 주말에도 쉬지 못하는 날이
        <br />이어지면서, 곁에서 쉬고 있는 모습을
        <br />보면 서운함이 올라온다고 하셨어요.
        <br /><br />
        이건 책임을 가리려는 말이 아니에요.
        <br />준호님의 입장에서는 그 시간이
        <br />어떻게 보이고 있었는지,
        <br />차분히 들려주실 수 있을까요.
      </div>

      <hr className="hr-L" />

      <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>준호님의 답장</div>
      <textarea className="ta-L" placeholder="편한 말로 적어주세요" defaultValue={'저도 계속 신경 쓰고는 있었는데, 평일에 야근이 많아서 주말만큼은 진짜 잠깐이라도 쉬어야 다음 주를 버틸 수 있을 것 같았어요.'} />

      <div style={{ marginTop: 8, fontSize: 11, color: 'var(--L-sub)' }}>
        작성하신 글은 중재자가 정돈해 전달해요.
      </div>

      <div style={{ marginTop: 24, display: 'flex', gap: 8 }}>
        <button className="btn-L ghost" style={{ flex: 1 }}>초안 저장</button>
        <button className="btn-L" style={{ flex: 2 }}>중재자에게 보내기</button>
      </div>
    </div>
  </Phone>
);

// Variation B — 대화형 말풍선 (중재자 중심)
const MediationBubble = () => (
  <Phone tone="L" scroll>
    <div style={{ padding: '8px 20px 14px', height: 48, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <div style={{ fontSize: 18, color: 'var(--L-sub)' }}>‹</div>
      <div style={{ fontSize: 13, fontWeight: 500 }}>중재자</div>
      <div style={{ display: 'flex', gap: 3 }}>
        {[1, 2, 3, 4, 5, 6].map(i => (
          <span key={i} style={{
            width: 6, height: 6, borderRadius: '50%',
            background: i <= 3 ? 'var(--L-ink)' : 'var(--L-border)'
          }} />
        ))}
      </div>
    </div>

    <div style={{ padding: '8px 20px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Mediator message */}
      <div>
        <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 4, paddingLeft: 2 }}>중재자 · 턴 3</div>
        <div style={{
          background: 'var(--L-card)', border: '1px solid var(--L-border)',
          borderRadius: '3px 14px 14px 14px', padding: '14px 16px',
          fontSize: 14, lineHeight: 1.75, fontFamily: 'var(--font-serif)',
          maxWidth: '88%',
        }}>
          서현님은 주말에도 쉬는 시간이 거의 없이 집안일이 이어지는 상황에서 서운함을 느끼셨다고 해요. 준호님 입장에서는 그 시간이 어떻게 보이셨을까요.
        </div>
      </div>

      {/* User reply */}
      <div style={{ alignSelf: 'flex-end', maxWidth: '85%' }}>
        <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 4, textAlign: 'right', paddingRight: 2 }}>준호 · 나</div>
        <div style={{
          background: 'var(--L-ink)', color: 'var(--L-bg)',
          borderRadius: '14px 3px 14px 14px', padding: '14px 16px',
          fontSize: 14, lineHeight: 1.7,
        }}>
          평일에 야근이 이어지다 보니, 주말만큼은 잠깐이라도 쉬어야 다음 주를 버틸 수 있을 것 같았어요.
        </div>
      </div>

      {/* Mediator follow */}
      <div>
        <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 4, paddingLeft: 2 }}>중재자</div>
        <div style={{
          background: 'var(--L-card)', border: '1px solid var(--L-border)',
          borderRadius: '3px 14px 14px 14px', padding: '14px 16px',
          fontSize: 14, lineHeight: 1.75, fontFamily: 'var(--font-serif)',
          maxWidth: '88%',
        }}>
          "쉼"이 준호님께는 단순한 휴식이 아니라, 다음 주를 버티기 위한 회복의 시간이었군요. 서현님께도 비슷한 회복의 시간이 있다면 어떤 모습일지, 짐작되는 게 있으실까요.
        </div>
      </div>

      <div style={{
        border: '1px solid var(--L-border)', borderRadius: 3,
        padding: '12px 14px', fontSize: 13, color: 'var(--L-sub)',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <span>답장 적기…</span>
        <span style={{ fontSize: 12 }}>＋</span>
      </div>
    </div>
  </Phone>
);

// Variation C — 카드 스택 (한 턴 = 카드, 스와이프)
const MediationCards = () => (
  <Phone tone="L">
    <PhoneHeader title="중재자의 질문 · 3 / 6" tone="L" />
    <div style={{ padding: '8px 28px 28px', position: 'relative' }}>
      <Dashes n={6} done={3} />

      <div style={{ position: 'relative', marginTop: 20, height: 380 }}>
        {/* Back card */}
        <div className="letter-card" style={{
          position: 'absolute', inset: 0, transform: 'translateY(14px) scale(0.94)',
          opacity: 0.5, padding: 22,
        }}>
          <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>다음 질문</div>
        </div>
        {/* Mid */}
        <div className="letter-card" style={{
          position: 'absolute', inset: 0, transform: 'translateY(7px) scale(0.97)',
          opacity: 0.8,
        }} />
        {/* Front */}
        <div className="letter-card" style={{
          position: 'absolute', inset: 0, padding: 26,
          display: 'flex', flexDirection: 'column',
        }}>
          <div className="quote-it" style={{ fontSize: 12 }}>Q. 3</div>
          <div className="serif" style={{ fontSize: 18, lineHeight: 1.7, marginTop: 14, flex: 1 }}>
            서현님은 주말에도 쉬는 시간이 거의 없다고 느끼셨어요.
            <br /><br />
            준호님 입장에서는 그 시간이 어떻게 보이셨을까요.
          </div>
          <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 14 }}>
            답장은 200자 안이 읽기 좋아요
          </div>
        </div>
      </div>

      <div style={{ marginTop: 20, display: 'flex', gap: 8 }}>
        <button className="btn-L ghost" style={{ flex: 1 }}>넘기기</button>
        <button className="btn-L" style={{ flex: 2 }}>답장 쓰기</button>
      </div>
    </div>
  </Phone>
);

Object.assign(window, { MediationLetter, MediationBubble, MediationCards });
