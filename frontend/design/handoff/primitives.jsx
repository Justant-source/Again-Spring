/* global React */
// Shared primitives for 다시봄 mockup

const Dashes = ({ n = 4, done = 0 }) => (
  <div className="dash-row">
    {Array.from({ length: n }).map((_, i) => (
      <span key={i} className={'dash' + (i < done ? ' on' : '')} />
    ))}
  </div>
);

const Logo = ({ size = 14, color = 'var(--L-ink)' }) => (
  <span style={{ fontFamily: 'var(--font-serif)', fontSize: size, color, letterSpacing: '-0.02em', fontWeight: 500 }}>
    다시봄
  </span>
);

const PhoneHeader = ({ title, back = true, right = null, tone = 'L' }) => {
  const ink = tone === 'P' ? 'var(--P-ink)' : 'var(--L-ink)';
  const sub = tone === 'P' ? 'var(--P-sub)' : 'var(--L-sub)';
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '8px 20px 14px', height: 48
    }}>
      <div style={{ width: 24, color: sub, fontSize: 18 }}>{back ? '‹' : ''}</div>
      <div style={{ fontSize: 13, color: ink, fontWeight: 500 }}>{title}</div>
      <div style={{ width: 24, textAlign: 'right' }}>{right}</div>
    </div>
  );
};

const Phone = ({ children, tone = 'L', scroll = false }) => (
  <div className={'phone' + (tone === 'P' ? ' P' : '')}>
    <div className="notch" />
    <div className="status">
      <span>9:41</span>
      <span style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
        <span style={{ fontSize: 11 }}>●●●</span>
        <span style={{ fontSize: 11 }}>⌁</span>
        <span style={{
          display: 'inline-block', width: 22, height: 11,
          border: '1px solid currentColor', borderRadius: 2, position: 'relative',
          opacity: 0.7
        }}>
          <span style={{ position: 'absolute', inset: 1, background: 'currentColor', width: 16, opacity: 0.8 }} />
        </span>
      </span>
    </div>
    <div style={{
      position: 'absolute', top: 44, left: 0, right: 0, bottom: 20,
      overflow: scroll ? 'auto' : 'hidden',
    }} className="artboard-inner">
      {children}
    </div>
    <div className="home-indicator" />
  </div>
);

// StripePlaceholder for imagery
const Strip = ({ w = '100%', h = 80, label = 'image' }) => (
  <div className="stripe-ph" style={{ width: w, height: h, borderRadius: 3 }}>
    {label}
  </div>
);

// Expose
Object.assign(window, { Dashes, Logo, PhoneHeader, Phone, Strip });
