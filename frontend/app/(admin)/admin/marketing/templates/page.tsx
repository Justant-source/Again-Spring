'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getTemplates, toggleTemplateActive, deleteTemplate, type Template } from '@/lib/api/marketing/templateApi';

const PLATFORM_LABELS: Record<string, string> = {
  X: 'X', INSTAGRAM: 'Instagram', NAVER_BLOG: '네이버블로그', THREADS: 'Threads', FACEBOOK: 'Facebook',
};

export default function TemplatesPage() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [platform, setPlatform] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    try {
      const data = await getTemplates(platform || undefined);
      setTemplates(data);
      setError('');
    } catch {
      setError('템플릿을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [platform]);

  async function handleToggle(id: number) {
    setTogglingId(id);
    try {
      const updated = await toggleTemplateActive(id);
      setTemplates(prev => prev.map(t => t.id === id ? updated : t));
    } catch { setError('상태 변경 실패'); } finally { setTogglingId(null); }
  }

  async function handleDelete(id: number) {
    setDeletingId(id);
    try {
      await deleteTemplate(id);
      setTemplates(prev => prev.filter(t => t.id !== id));
      setConfirmDeleteId(null);
    } catch { setError('삭제 실패'); } finally { setDeletingId(null); }
  }

  return (
    <div>
      <div style={{ marginBottom: 20, padding: '20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 style={{ fontSize: 16, fontWeight: 600, color: '#1A1A2E', margin: '0 0 4px' }}>콘텐츠 템플릿</h1>
            <p style={{ fontSize: 13, color: '#888', margin: 0 }}>재사용 가능한 플랫폼별 카피 템플릿</p>
          </div>
          <Link
            href="/admin/marketing/templates/new"
            style={{ padding: '9px 18px', background: '#1A1A2E', color: 'white', borderRadius: 6, textDecoration: 'none', fontSize: 13, fontWeight: 500 }}
          >
            새 템플릿
          </Link>
        </div>
      </div>

      <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
        <select
          value={platform}
          onChange={e => setPlatform(e.target.value)}
          style={{ padding: '7px 12px', border: '1px solid #e7e3d8', borderRadius: 6, fontSize: 13, background: 'white' }}
        >
          <option value="">전체 플랫폼</option>
          {['X', 'INSTAGRAM', 'NAVER_BLOG', 'THREADS', 'FACEBOOK'].map(p => (
            <option key={p} value={p}>{PLATFORM_LABELS[p]}</option>
          ))}
        </select>
      </div>

      {error && <div style={{ padding: 12, background: '#ffe6e6', color: '#b33333', borderRadius: 6, marginBottom: 12, fontSize: 13 }}>{error}</div>}

      {loading ? (
        <p style={{ color: '#aaa', fontSize: 13 }}>불러오는 중...</p>
      ) : templates.length === 0 ? (
        <p style={{ color: '#aaa', fontSize: 13 }}>등록된 템플릿이 없습니다.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {templates.map(t => (
            <div key={t.id} style={{ padding: '16px 20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8', opacity: t.isActive ? 1 : 0.6 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <span style={{ fontSize: 11, fontWeight: 600, padding: '2px 8px', background: '#1A1A2E', color: 'white', borderRadius: 4 }}>
                      {PLATFORM_LABELS[t.platform] ?? t.platform}
                    </span>
                    {!t.isActive && <span style={{ fontSize: 11, color: '#aaa', fontWeight: 500 }}>(비활성)</span>}
                  </div>
                  <p style={{ margin: '0 0 4px', fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>{t.name}</p>
                  <p style={{ margin: 0, fontSize: 12, color: '#888', overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>
                    {t.bodyTemplate.slice(0, 80)}{t.bodyTemplate.length > 80 ? '...' : ''}
                  </p>
                </div>
                <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                  <Link
                    href={`/admin/marketing/templates/${t.id}`}
                    style={{ padding: '5px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, textDecoration: 'none', fontSize: 12, color: '#333' }}
                  >
                    편집
                  </Link>
                  <button
                    onClick={() => handleToggle(t.id)}
                    disabled={togglingId === t.id}
                    style={{ padding: '5px 10px', background: t.isActive ? '#f0f0f0' : '#e6f7e6', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 12, color: t.isActive ? '#666' : '#2d7a2d' }}
                  >
                    {t.isActive ? '비활성화' : '활성화'}
                  </button>
                  {confirmDeleteId === t.id ? (
                    <>
                      <button onClick={() => handleDelete(t.id)} disabled={deletingId === t.id} style={{ padding: '5px 10px', background: '#b33333', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>확인</button>
                      <button onClick={() => setConfirmDeleteId(null)} style={{ padding: '5px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>취소</button>
                    </>
                  ) : (
                    <button onClick={() => setConfirmDeleteId(t.id)} style={{ padding: '5px 10px', background: '#ffe6e6', color: '#b33333', border: '1px solid #ffcccc', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>삭제</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
