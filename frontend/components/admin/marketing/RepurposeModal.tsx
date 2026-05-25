'use client';

import { useState } from 'react';
import { repurposeContent } from '@/lib/api/marketing/repurposeApi';

interface Props {
  sourceId: number;
  sourcePlatform: string;
  onClose: () => void;
  onRepurposed: () => void;
}

const ALL_PLATFORMS = ['X', 'INSTAGRAM', 'NAVER_BLOG', 'THREADS', 'FACEBOOK'];
const PLATFORM_LABELS: Record<string, string> = { X: 'X', INSTAGRAM: 'Instagram', NAVER_BLOG: '네이버블로그', THREADS: 'Threads', FACEBOOK: 'Facebook' };

export function RepurposeModal({ sourceId, sourcePlatform, onClose, onRepurposed }: Props) {
  const available = ALL_PLATFORMS.filter(p => p !== sourcePlatform.toUpperCase());
  const [targetPlatform, setTargetPlatform] = useState(available[0] ?? 'X');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  async function handleRepurpose() {
    setSubmitting(true);
    setError('');
    try {
      await repurposeContent(sourceId, targetPlatform.toLowerCase());
      onRepurposed();
      onClose();
    } catch (e: any) {
      setError(e.response?.data?.message ?? '리퍼포징 실패');
    } finally { setSubmitting(false); }
  }

  return (
    <div
      style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'white', borderRadius: 12, padding: 24, maxWidth: 380, width: '90%', boxShadow: '0 10px 40px rgba(0,0,0,0.2)' }}
        onClick={e => e.stopPropagation()}
      >
        <h3 style={{ margin: '0 0 8px', fontSize: 16, fontWeight: 600, color: '#1A1A2E' }}>콘텐츠 리퍼포징</h3>
        <p style={{ margin: '0 0 16px', fontSize: 13, color: '#666' }}>
          콘텐츠 #{sourceId}을 다른 플랫폼 형식으로 재가공합니다.
        </p>

        {error && <p style={{ color: '#b33333', fontSize: 13, marginBottom: 12 }}>{error}</p>}

        <div style={{ marginBottom: 20 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 8 }}>타겟 플랫폼</label>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {available.map(p => (
              <button
                key={p}
                onClick={() => setTargetPlatform(p)}
                style={{
                  padding: '8px 14px',
                  border: '1px solid',
                  borderColor: targetPlatform === p ? '#1A1A2E' : '#ddd',
                  background: targetPlatform === p ? '#1A1A2E' : 'white',
                  color: targetPlatform === p ? 'white' : '#555',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                }}
              >
                {PLATFORM_LABELS[p] ?? p}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button onClick={onClose}
            style={{ padding: '8px 16px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
            취소
          </button>
          <button onClick={handleRepurpose} disabled={submitting}
            style={{ padding: '8px 16px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: submitting ? 'not-allowed' : 'pointer', fontSize: 13, opacity: submitting ? 0.6 : 1 }}>
            {submitting ? '처리 중...' : '리퍼포징 시작'}
          </button>
        </div>
      </div>
    </div>
  );
}
