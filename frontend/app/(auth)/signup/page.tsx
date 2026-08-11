'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { oauthRedirect, safeRedirect, authHref } from '@/lib/auth/oauth';
import { generateGuestNickname } from '@/lib/utils/guestNickname';

export default function SignupPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setUser = useUserStore((s) => s.setUser);
  const guestUser = useUserStore((s) => s.user);
  const nextPath = safeRedirect(searchParams.get('redirect') || searchParams.get('next'));

  const [nickname, setNickname] = useState('');
  const [nicknameShuffling, setNicknameShuffling] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [sentToEmail, setSentToEmail] = useState('');
  const [termsAgreed] = useState(true);
  const [privacyAgreed] = useState(true);
  const [disclaimerAgreed] = useState(true);
  const [marketingAgreed] = useState(false);
  const [termsModalUrl, setTermsModalUrl] = useState<string | null>(null);
  const [duplicateEmailModal, setDuplicateEmailModal] = useState(false);

  useEffect(() => {
    if (guestUser?.isGuest && guestUser?.nickname) {
      setNickname(guestUser.nickname);
    }
  }, [guestUser]);

  // 메일 딥링크(/signup?email=...&code=...)로 진입 시 이메일·인증코드 자동 입력
  useEffect(() => {
    const urlEmail = searchParams.get('email');
    const urlCode = searchParams.get('code');
    if (urlEmail) {
      setEmail(urlEmail);
      setSentToEmail(urlEmail);
      setCodeSent(true);
    }
    if (urlCode) {
      setVerificationCode(urlCode.replace(/\D/g, '').slice(0, 4));
    }
  }, [searchParams]);

  const handleShuffleNickname = async () => {
    setNicknameShuffling(true);
    try {
      for (let i = 0; i < 10; i++) {
        const candidate = generateGuestNickname();
        try {
          const res = await api.get(`/api/auth/check-nickname?nickname=${encodeURIComponent(candidate)}`);
          if (res.data.available) {
            setNickname(candidate);
            return;
          }
        } catch {
          setNickname(candidate);
          return;
        }
      }
      setNickname(generateGuestNickname());
    } finally {
      setNicknameShuffling(false);
    }
  };

  const inputStyle = {
    width: '100%',
    borderBottom: '1px solid var(--L-border)',
    background: 'transparent',
    color: 'var(--L-ink)',
    fontSize: 15,
    padding: '8px 0',
    outline: 'none',
    fontFamily: 'var(--font-sans)',
  };

  const handleSendCode = async () => {
    if (!email) {
      setError('이메일을 입력해주세요');
      return;
    }
    setError('');
    setSendingCode(true);
    try {
      await api.post('/api/auth/send-verification', { email });
      setCodeSent(true);
      setSentToEmail(email);
    } catch (err: any) {
      if (err.response?.data?.error?.code === 'USER_ALREADY_EXISTS') {
        setDuplicateEmailModal(true);
      } else {
        setError(err.response?.data?.error?.message || '인증코드 발송에 실패했어요');
      }
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (nickname.length < 3 || nickname.length > 12) {
      setError('이름은 3자 이상 12자 이하여야 해요');
      return;
    }
    if (!email) {
      setError('이메일을 입력해주세요');
      return;
    }
    if (!codeSent) {
      setError('이메일 인증코드를 먼저 전송해주세요');
      return;
    }
    if (email !== sentToEmail) {
      setError('이메일이 변경되었어요. 새 주소로 인증코드를 다시 전송해주세요');
      return;
    }
    if (verificationCode.length !== 4) {
      setError('인증코드 4자리를 입력해주세요');
      return;
    }
    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 해요');
      return;
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않아요');
      return;
    }


    setLoading(true);
    try {
      const response = await api.post('/api/auth/signup', {
        nickname,
        email,
        password,
        verificationCode,
        termsAgreed,
        privacyAgreed,
        disclaimerAgreed,
        marketingAgreed,
        migrateFromGuestId: guestUser?.isGuest ? guestUser.id : undefined,
      });
      const { user, token } = response.data;
      if (token?.accessToken) {
        localStorage.setItem('again-spring-token', token.accessToken);
      }
      setUser(user);
      router.push(nextPath);
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '회원가입에 실패했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="회원가입"
        onBack={() => window.history.length > 1 ? router.back() : router.replace('/login')}
      />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>
            다시 봄을
          </div>
          <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
            시작할 이름을<br />알려주세요
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <input
                  type="text"
                  placeholder="닉네임"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  style={{ ...inputStyle, flex: 1 }}
                  onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                  onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
                />
                <button
                  type="button"
                  onClick={handleShuffleNickname}
                  disabled={nicknameShuffling}
                  style={{ background: 'none', border: 'none', color: 'var(--L-ink)', fontSize: 11, textDecoration: 'underline', cursor: 'pointer', padding: '0 0 8px 0', whiteSpace: 'nowrap', opacity: nicknameShuffling ? 0.5 : 1 }}
                >
                  {nicknameShuffling ? '...' : '다른 이름'}
                </button>
              </div>
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>3~12자</div>
            </div>

            <div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <input
                  type="email"
                  placeholder="이메일"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  style={{ ...inputStyle, flex: 1 }}
                  onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                  onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
                />
                <button
                  type="button"
                  onClick={handleSendCode}
                  disabled={sendingCode || !email}
                  style={{
                    padding: '6px 12px',
                    background: 'var(--L-ink)',
                    color: 'var(--L-bg)',
                    border: 'none',
                    borderRadius: 6,
                    fontSize: 12,
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                    opacity: sendingCode || !email ? 0.5 : 1,
                  }}
                >
                  {sendingCode ? '전송 중...' : codeSent ? '재전송' : '인증코드 전송'}
                </button>
              </div>
              {codeSent && (
                <div style={{ fontSize: 11, color: '#4caf50', marginTop: 4 }}>
                  {sentToEmail}로 인증코드를 발송했어요
                </div>
              )}
            </div>

            {codeSent && (
              <div>
                <input
                  type="text"
                  placeholder="인증코드 4자리"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 4))}
                  maxLength={4}
                  inputMode="numeric"
                  style={{ ...inputStyle, letterSpacing: '0.3em', fontSize: 18, textAlign: 'center' }}
                  onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                  onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
                />
              </div>
            )}

            <div>
              <input
                type="password"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={inputStyle}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>8자 이상</div>
            </div>

            <div>
              <input
                type="password"
                placeholder="비밀번호 확인"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                style={inputStyle}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
            </div>


            {error && (
              <div style={{ fontSize: 13, color: 'var(--L-point)' }}>{error}</div>
            )}

            <button
              type="submit"
              disabled={loading || !termsAgreed || !privacyAgreed || !disclaimerAgreed}
              className="btn-L"
              style={{
                marginTop: 12,
                opacity: (!termsAgreed || !privacyAgreed || !disclaimerAgreed) ? 0.5 : 1,
              }}
            >
              {loading ? '가입 중...' : '가입하기'}
            </button>
          </form>
        </div>

        {/* 소셜 회원가입 */}
        <div style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
            <span style={{ fontSize: 11, color: 'var(--L-sub)', whiteSpace: 'nowrap' }}>또는 소셜 계정으로</span>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button
              onClick={() => oauthRedirect('google', nextPath)}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '10px 0', border: '1px solid var(--L-border)', borderRadius: 8, background: 'white', fontSize: 13, cursor: 'pointer', color: '#333' }}
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z" fill="#4285F4"/>
                <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18Z" fill="#34A853"/>
                <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z" fill="#FBBC05"/>
                <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z" fill="#EA4335"/>
              </svg>
              Google로 가입하기
            </button>
          </div>
        </div>

        <div style={{ marginTop: 20, textAlign: 'center', fontSize: 12, color: 'var(--L-sub)' }}>
          <Link href={authHref('/login', nextPath)} style={{ color: 'var(--L-ink)', textDecoration: 'underline' }}>
            이미 계정이 있어요
          </Link>
        </div>
      </div>

      {/* 이메일 중복 모달 */}
      {duplicateEmailModal && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999,
          }}
        >
          <div
            style={{
              background: 'white', borderRadius: 14, width: '85%', maxWidth: 360,
              padding: '28px 24px', display: 'flex', flexDirection: 'column', gap: 16, textAlign: 'center',
            }}
          >
            <div style={{ fontSize: 22 }}>이미 가입된 이메일이에요</div>
            <div style={{ fontSize: 14, color: '#555', lineHeight: 1.7 }}>
              <strong>{email}</strong>은 이미 가입된 계정이에요.<br />
              다른 이메일로 인증하거나, 로그인 화면에서 기존 계정으로 입장해 주세요.
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 4 }}>
              <button
                onClick={() => { setDuplicateEmailModal(false); setEmail(''); }}
                style={{
                  padding: '12px 0', background: 'var(--L-ink)', color: 'var(--L-bg)',
                  border: 'none', borderRadius: 8, fontSize: 14, cursor: 'pointer', fontWeight: 600,
                }}
              >
                다른 이메일로 인증하기
              </button>
              <button
                onClick={() => { setDuplicateEmailModal(false); router.push(authHref('/login', nextPath)); }}
                style={{
                  padding: '12px 0', background: 'transparent', color: 'var(--L-ink)',
                  border: '1px solid var(--L-border)', borderRadius: 8, fontSize: 14, cursor: 'pointer',
                }}
              >
                로그인 화면으로 가기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 약관 전문 보기 모달 */}
      {termsModalUrl && (
        <div
          role="dialog"
          aria-modal="true"
          onClick={() => setTermsModalUrl(null)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'white', borderRadius: 12, width: '90%', maxWidth: 480,
              height: '75vh', display: 'flex', flexDirection: 'column', overflow: 'hidden',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '8px 12px' }}>
              <button
                onClick={() => setTermsModalUrl(null)}
                style={{ background: 'none', border: 'none', fontSize: 20, cursor: 'pointer', color: '#333' }}
              >×</button>
            </div>
            <iframe src={termsModalUrl} style={{ flex: 1, border: 'none' }} title="약관 전문" />
          </div>
        </div>
      )}
    </PhoneFrame>
  );
}

function ConsentRow({
  checked, onChange, label, required, onViewClick,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  required: boolean;
  onViewClick?: () => void;
}) {
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, color: 'var(--L-ink)' }}>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        style={{ width: 15, height: 15, flexShrink: 0 }}
      />
      <span style={{ flex: 1 }}>
        {required && <span style={{ color: 'var(--L-point)', marginRight: 3 }}>*</span>}
        {label}
      </span>
      {onViewClick && (
        <button
          type="button"
          onClick={onViewClick}
          style={{
            background: 'none', border: 'none', padding: 0,
            fontSize: 11, color: 'var(--L-sub)', textDecoration: 'underline', cursor: 'pointer',
            flexShrink: 0,
          }}
        >
          전문 보기
        </button>
      )}
    </label>
  );
}
