'use client';

import { useEffect, useState } from 'react';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getCredentialStatus,
  getSessionStatus,
  saveCredentials,
  seedSession,
  testLogin,
  type SocialPlatform,
  type CredentialStatus,
  type SessionStatusInfo,
} from '@/lib/api/marketing/socialApi';

export default function MarketingSettingsPage() {
  const [credentialStatus, setCredentialStatus] = useState<CredentialStatus[]>([]);
  const [sessionStatus, setSessionStatus] = useState<SessionStatusInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Credential form state
  const [xEmail, setXEmail] = useState('');
  const [xPassword, setXPassword] = useState('');
  const [igEmail, setIgEmail] = useState('');
  const [igPassword, setIgPassword] = useState('');

  // Session seeding state
  const [xStorageState, setXStorageState] = useState('');
  const [igStorageState, setIgStorageState] = useState('');

  // Loading states
  const [savingX, setSavingX] = useState(false);
  const [savingIG, setSavingIG] = useState(false);
  const [seedingX, setSeedingX] = useState(false);
  const [seedingIG, setSeedingIG] = useState(false);
  const [testingX, setTestingX] = useState(false);
  const [testingIG, setTestingIG] = useState(false);

  // Toast messages
  const [saveSuccess, setSaveSuccess] = useState('');

  async function handleTestLogin(platform: SocialPlatform) {
    const setter = platform === 'X' ? setTestingX : setTestingIG;
    setter(true);
    setError('');
    try {
      const result = await testLogin(platform);
      if (result.ok) {
        setSaveSuccess(`${platform} 로그인 성공`);
      } else {
        setError(`${platform} 로그인 실패: ${result.error || '알 수 없는 오류'}`);
      }
      setTimeout(() => setSaveSuccess(''), 4000);
    } catch (e: any) {
      setError(`${platform} 테스트 오류: ${e.message || '알 수 없는 오류'}`);
    } finally {
      setter(false);
    }
  }

  useEffect(() => {
    loadStatus();
  }, []);

  async function loadStatus() {
    try {
      const [creds, sessions] = await Promise.all([
        getCredentialStatus(),
        getSessionStatus(),
      ]);
      setCredentialStatus(creds);
      setSessionStatus(sessions);
      setError('');
    } catch (e: any) {
      setError('설정 상태를 불러오지 못했습니다.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveCredentials(platform: SocialPlatform) {
    const [email, password] =
      platform === 'X' ? [xEmail, xPassword] : [igEmail, igPassword];

    if (!email.trim() || !password.trim()) {
      setError('이메일과 비밀번호를 입력해주세요.');
      return;
    }

    const setter = platform === 'X' ? setSavingX : setSavingIG;
    setter(true);
    setError('');

    try {
      await saveCredentials(platform, email, password);
      setSaveSuccess(`${platform} 자격증명이 저장되었습니다.`);
      setTimeout(() => setSaveSuccess(''), 3000);

      // Reset form
      if (platform === 'X') {
        setXEmail('');
        setXPassword('');
      } else {
        setIgEmail('');
        setIgPassword('');
      }

      await loadStatus();
    } catch (e: any) {
      const errMsg = e.response?.data?.error?.message || e.response?.data?.error || e.message || '알 수 없는 오류';
      setError(`저장 실패: ${typeof errMsg === 'string' ? errMsg : JSON.stringify(errMsg)}`);
      console.error(e);
    } finally {
      setter(false);
    }
  }

  async function handleSeedSession(platform: SocialPlatform) {
    const storageState = platform === 'X' ? xStorageState : igStorageState;

    if (!storageState.trim()) {
      setError('storageState JSON을 붙여넣어주세요.');
      return;
    }

    const setter = platform === 'X' ? setSeedingX : setSeedingIG;
    setter(true);
    setError('');

    try {
      await seedSession(platform, storageState);
      setSaveSuccess(`${platform} 세션이 등록되었습니다.`);
      setTimeout(() => setSaveSuccess(''), 3000);

      // Reset form
      if (platform === 'X') {
        setXStorageState('');
      } else {
        setIgStorageState('');
      }

      await loadStatus();
    } catch (e: any) {
      setError(`세션 등록 실패: ${e.response?.data?.error || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setter(false);
    }
  }

  if (loading) {
    return (
      <AdminSection
        title="마케팅 설정"
        subtitle="소셜 미디어 자동 포스팅 설정"
      >
        <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중…</p>
      </AdminSection>
    );
  }

  return (
    <AdminSection
      title="마케팅 설정"
      subtitle="소셜 미디어 자동 포스팅 설정"
    >
      {error && (
        <div
          style={{
            padding: 12,
            background: '#fee2e2',
            color: '#b33333',
            borderRadius: 6,
            marginBottom: 16,
            fontSize: 13,
          }}
        >
          {error}
        </div>
      )}

      {saveSuccess && (
        <div
          style={{
            padding: 12,
            background: '#d1fae5',
            color: '#065f46',
            borderRadius: 6,
            marginBottom: 16,
            fontSize: 13,
          }}
        >
          {saveSuccess}
        </div>
      )}

      {/* X (Twitter) Settings */}
      <div style={{ marginBottom: 24, padding: 16, background: '#f9f9f9', borderRadius: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>X (Twitter)</h3>
          <span
            style={{
              padding: '3px 8px',
              borderRadius: 4,
              fontSize: 11,
              fontWeight: 600,
              background: credentialStatus.find(c => c.platform === 'X')?.configured ? '#d1fae5' : '#f3f4f6',
              color: credentialStatus.find(c => c.platform === 'X')?.configured ? '#065f46' : '#6b7280',
            }}
          >
            {credentialStatus.find(c => c.platform === 'X')?.configured ? '설정됨' : '미설정'}
          </span>
        </div>

        {/* Credential Form */}
        <div style={{ marginBottom: 16, paddingBottom: 16, borderBottom: '1px solid #e5e7eb' }}>
          <h4 style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', margin: '0 0 10px' }}>
            계정 자격증명
          </h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 500, display: 'block', marginBottom: 4 }}>
                이메일
              </label>
              <input
                type="email"
                placeholder="account@example.com"
                value={xEmail}
                onChange={(e) => setXEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 500, display: 'block', marginBottom: 4 }}>
                비밀번호
              </label>
              <input
                type="password"
                placeholder="••••••••"
                value={xPassword}
                onChange={(e) => setXPassword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button
              onClick={() => handleSaveCredentials('X')}
              disabled={savingX}
              style={{
                padding: '7px 16px',
                background: savingX ? '#ccc' : '#1A1A2E',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: savingX ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              {savingX ? '저장 중...' : '자격증명 저장'}
            </button>
            <button
              onClick={() => handleTestLogin('X')}
              disabled={testingX || !credentialStatus.find(c => c.platform === 'X')?.configured}
              style={{
                padding: '7px 16px',
                background: testingX ? '#ccc' : '#2563eb',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: (testingX || !credentialStatus.find(c => c.platform === 'X')?.configured) ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              {testingX ? '테스트 중...' : '로그인 테스트'}
            </button>
          </div>
          <p style={{ fontSize: 11, color: '#888', margin: '8px 0 0' }}>
            비밀번호는 저장 후 다시 표시되지 않습니다.
          </p>
        </div>

        {/* Session Seeding */}
        <div>
          <h4 style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', margin: '0 0 10px' }}>
            브라우저 세션 시드
          </h4>
          <div style={{ marginBottom: 12 }}>
            <span
              style={{
                padding: '3px 8px',
                borderRadius: 4,
                fontSize: 11,
                fontWeight: 600,
                background:
                  sessionStatus.find(s => s.platform === 'X')?.status === 'SEEDED' ? '#d1fae5' :
                  sessionStatus.find(s => s.platform === 'X')?.status === 'EXPIRED' ? '#fee2e2' :
                  '#f3f4f6',
                color:
                  sessionStatus.find(s => s.platform === 'X')?.status === 'SEEDED' ? '#065f46' :
                  sessionStatus.find(s => s.platform === 'X')?.status === 'EXPIRED' ? '#b33333' :
                  '#6b7280',
              }}
            >
              {sessionStatus.find(s => s.platform === 'X')?.status === 'SEEDED' ? '세션 시드됨' :
               sessionStatus.find(s => s.platform === 'X')?.status === 'EXPIRED' ? '세션 만료' :
               '미시딩'}
            </span>
          </div>
          <textarea
            placeholder="로컬에서 node seed-cli.js --platform x 실행 후 출력된 JSON을 붙여넣으세요"
            value={xStorageState}
            onChange={(e) => setXStorageState(e.target.value)}
            style={{
              width: '100%',
              minHeight: 120,
              padding: '10px 10px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 12,
              fontFamily: 'monospace',
              marginBottom: 10,
              boxSizing: 'border-box',
            }}
          />
          <button
            onClick={() => handleSeedSession('X')}
            disabled={seedingX}
            style={{
              padding: '7px 16px',
              background: seedingX ? '#ccc' : '#446620',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: seedingX ? 'not-allowed' : 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            {seedingX ? '등록 중...' : '세션 등록'}
          </button>
        </div>
      </div>

      {/* Instagram Settings */}
      <div style={{ marginBottom: 24, padding: 16, background: '#f9f9f9', borderRadius: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>Instagram</h3>
          <span
            style={{
              padding: '3px 8px',
              borderRadius: 4,
              fontSize: 11,
              fontWeight: 600,
              background: credentialStatus.find(c => c.platform === 'INSTAGRAM')?.configured ? '#d1fae5' : '#f3f4f6',
              color: credentialStatus.find(c => c.platform === 'INSTAGRAM')?.configured ? '#065f46' : '#6b7280',
            }}
          >
            {credentialStatus.find(c => c.platform === 'INSTAGRAM')?.configured ? '설정됨' : '미설정'}
          </span>
        </div>

        {/* Credential Form */}
        <div style={{ marginBottom: 16, paddingBottom: 16, borderBottom: '1px solid #e5e7eb' }}>
          <h4 style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', margin: '0 0 10px' }}>
            계정 자격증명
          </h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 500, display: 'block', marginBottom: 4 }}>
                이메일
              </label>
              <input
                type="email"
                placeholder="account@example.com"
                value={igEmail}
                onChange={(e) => setIgEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 500, display: 'block', marginBottom: 4 }}>
                비밀번호
              </label>
              <input
                type="password"
                placeholder="••••••••"
                value={igPassword}
                onChange={(e) => setIgPassword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button
              onClick={() => handleSaveCredentials('INSTAGRAM')}
              disabled={savingIG}
              style={{
                padding: '7px 16px',
                background: savingIG ? '#ccc' : '#1A1A2E',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: savingIG ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              {savingIG ? '저장 중...' : '자격증명 저장'}
            </button>
            <button
              onClick={() => handleTestLogin('INSTAGRAM')}
              disabled={testingIG || !credentialStatus.find(c => c.platform === 'INSTAGRAM')?.configured}
              style={{
                padding: '7px 16px',
                background: testingIG ? '#ccc' : '#2563eb',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: (testingIG || !credentialStatus.find(c => c.platform === 'INSTAGRAM')?.configured) ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              {testingIG ? '테스트 중...' : '로그인 테스트'}
            </button>
          </div>
          <p style={{ fontSize: 11, color: '#888', margin: '8px 0 0' }}>
            비밀번호는 저장 후 다시 표시되지 않습니다.
          </p>
        </div>

        {/* Session Seeding */}
        <div>
          <h4 style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', margin: '0 0 10px' }}>
            브라우저 세션 시드
          </h4>
          <div style={{ marginBottom: 12 }}>
            <span
              style={{
                padding: '3px 8px',
                borderRadius: 4,
                fontSize: 11,
                fontWeight: 600,
                background:
                  sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'SEEDED' ? '#d1fae5' :
                  sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'EXPIRED' ? '#fee2e2' :
                  '#f3f4f6',
                color:
                  sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'SEEDED' ? '#065f46' :
                  sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'EXPIRED' ? '#b33333' :
                  '#6b7280',
              }}
            >
              {sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'SEEDED' ? '세션 시드됨' :
               sessionStatus.find(s => s.platform === 'INSTAGRAM')?.status === 'EXPIRED' ? '세션 만료' :
               '미시딩'}
            </span>
          </div>
          <textarea
            placeholder="로컬에서 node seed-cli.js --platform instagram 실행 후 출력된 JSON을 붙여넣으세요"
            value={igStorageState}
            onChange={(e) => setIgStorageState(e.target.value)}
            style={{
              width: '100%',
              minHeight: 120,
              padding: '10px 10px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 12,
              fontFamily: 'monospace',
              marginBottom: 10,
              boxSizing: 'border-box',
            }}
          />
          <button
            onClick={() => handleSeedSession('INSTAGRAM')}
            disabled={seedingIG}
            style={{
              padding: '7px 16px',
              background: seedingIG ? '#ccc' : '#446620',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: seedingIG ? 'not-allowed' : 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            {seedingIG ? '등록 중...' : '세션 등록'}
          </button>
        </div>
      </div>
    </AdminSection>
  );
}
