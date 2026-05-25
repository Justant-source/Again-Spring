'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createTemplate } from '@/lib/api/marketing/templateApi';

const PLATFORMS = [
  { value: 'X', label: 'X' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_BLOG', label: '네이버블로그' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'FACEBOOK', label: 'Facebook' },
];

export default function NewTemplatePage() {
  const router = useRouter();
  const [platform, setPlatform] = useState('X');
  const [name, setName] = useState('');
  const [bodyTemplate, setBodyTemplate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave() {
    if (!name.trim() || !bodyTemplate.trim()) { setError('이름과 템플릿 본문을 입력해주세요.'); return; }
    setSaving(true);
    setError('');
    try {
      await createTemplate({ platform, name, bodyTemplate });
      router.push('/admin/marketing/templates');
    } catch (e: any) {
      setError(e.response?.data?.message ?? '생성 실패');
    } finally { setSaving(false); }
  }

  return (
    <div>
      <div style={{ marginBottom: 20, padding: '20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
        <h1 style={{ fontSize: 16, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>새 템플릿 생성</h1>
      </div>

      <div style={{ padding: '20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
        {error && <p style={{ color: '#b33333', fontSize: 13, marginBottom: 12 }}>{error}</p>}

        <div style={{ marginBottom: 16 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 6 }}>플랫폼</label>
          <select value={platform} onChange={e => setPlatform(e.target.value)}
            style={{ width: '100%', padding: '9px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }}>
            {PLATFORMS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
          </select>
        </div>

        <div style={{ marginBottom: 16 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 6 }}>이름</label>
          <input type="text" value={name} onChange={e => setName(e.target.value)} maxLength={120}
            style={{ width: '100%', padding: '9px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
        </div>

        <div style={{ marginBottom: 16 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 4 }}>
            템플릿 본문
          </label>
          <p style={{ fontSize: 11, color: '#aaa', margin: '0 0 6px' }}>변수는 {'${변수명}'} 형식으로 입력하세요.</p>
          <textarea value={bodyTemplate} onChange={e => setBodyTemplate(e.target.value)}
            style={{ width: '100%', minHeight: 200, padding: '10px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, fontFamily: 'monospace', boxSizing: 'border-box', resize: 'vertical' }} />
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button onClick={() => router.back()}
            style={{ padding: '9px 18px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
            취소
          </button>
          <button onClick={handleSave} disabled={saving}
            style={{ padding: '9px 18px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: saving ? 'not-allowed' : 'pointer', fontSize: 13, opacity: saving ? 0.6 : 1 }}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}
