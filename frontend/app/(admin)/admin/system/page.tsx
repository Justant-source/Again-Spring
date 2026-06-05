'use client';

import { useEffect, useState } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { SystemHealthPanel } from '@/components/admin/SystemHealthPanel';
import { LlmFailureRateChart } from '@/components/admin/LlmFailureRateChart';
import { reloadPrompts } from '@/lib/api/admin/system';

const KNOWN_FEATURE_FLAGS = [
  { key: 'app.admin.enabled', label: 'Admin Panel', description: '관리자 패널 활성화' },
  { key: 'app.features.marketing.enabled', label: 'Marketing', description: '마케팅 기능' },
  { key: 'app.features.crisis.enabled', label: 'Crisis Monitor', description: '위기 모니터링' },
];

export default function SystemPage() {
  const user = useUserStore((s) => s.user);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [reloadingPrompts, setReloadingPrompts] = useState(false);

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  async function handleReloadPrompts() {
    setReloadingPrompts(true);
    try {
      await reloadPrompts();
      alert('프롬프트가 재로드되었습니다.');
    } catch {
      alert('프롬프트 재로드 중 오류가 발생했습니다.');
    } finally {
      setReloadingPrompts(false);
    }
  }

  if (!isAuthorizedAdmin) {
    return <div style={{ padding: 40, fontFamily: 'sans-serif', color: '#e55' }}>권한이 없습니다.</div>;
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      {/* 헤더 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid #e7e3d8',
          padding: '12px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>시스템</div>
      </header>

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 60px' }}>
        {/* 시스템 헬스 */}
        <SystemHealthPanel refreshSignal={refreshSignal} />

        {/* LLM 실패율 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            LLM 호출 실패율 (최근 7일)
          </h2>
          <LlmFailureRateChart days={7} refreshSignal={refreshSignal} />
        </div>

        {/* 기능 플래그 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            활성화된 기능 (Feature Flags)
          </h2>
          <div style={{ display: 'grid', gap: 12 }}>
            {KNOWN_FEATURE_FLAGS.map((flag) => (
              <div
                key={flag.key}
                style={{
                  padding: 12,
                  background: '#f7f6f2',
                  border: '1px solid #e7e3d8',
                  borderRadius: 6,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>
                    {flag.label}
                  </div>
                  <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>
                    {flag.description}
                  </div>
                  <div style={{ fontSize: 10, color: '#aaa', marginTop: 2, fontFamily: 'ui-monospace' }}>
                    {flag.key}
                  </div>
                </div>
                <div
                  style={{
                    fontSize: 11,
                    padding: '2px 8px',
                    background: '#22a06b',
                    color: 'white',
                    borderRadius: 4,
                  }}
                >
                  활성화
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 프롬프트 재로드 */}
        <div
          style={{
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            LLM 프롬프트
          </h2>
          <p style={{ fontSize: 12, color: '#666', margin: '0 0 12px' }}>
            메모리에 로드된 프롬프트를 디스크에서 다시 읽어옵니다. 프롬프트 파일을 수정한 후 사용하세요.
          </p>
          <button
            onClick={handleReloadPrompts}
            disabled={reloadingPrompts}
            style={{
              padding: '10px 20px',
              background: '#1A1A2E',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: reloadingPrompts ? 'wait' : 'pointer',
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            {reloadingPrompts ? '재로드 중...' : '프롬프트 재로드'}
          </button>
        </div>
      </div>
    </div>
  );
}
