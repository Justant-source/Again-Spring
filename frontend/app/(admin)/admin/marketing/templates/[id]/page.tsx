'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { getTemplate, updateTemplate, deleteTemplate, type Template } from '@/lib/api/marketing/templateApi';

const PLATFORMS = [
  { value: 'X', label: 'X' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_BLOG', label: '네이버블로그' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'FACEBOOK', label: 'Facebook' },
];

export default function TemplateDetailPage() {
  const router = useRouter();
  const params = useParams();
  const id = Number(params.id);

  const [template, setTemplate] = useState<Template | null>(null);
  const [platform, setPlatform] = useState('X');
  const [name, setName] = useState('');
  const [bodyTemplate, setBodyTemplate] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getTemplate(id).then(t => {
      setTemplate(t);
      setPlatform(t.platform);
      setName(t.name);
      setBodyTemplate(t.bodyTemplate);
    }).catch(() => setError('불러오기 실패')).finally(() => setLoading(false));
  }, [id]);

  async function handleSave() {
    if (!name.trim() || !bodyTemplate.trim()) { setError('이름과 본문을 입력해주세요.'); return; }
    setSaving(true);
    try {
      await updateTemplate(id, { platform, name, bodyTemplate });
      router.push('/admin/marketing/templates');
    } catch { setError('저장 실패'); } finally { setSaving(false); }
  }

  async function handleDelete() {
    setDeleting(true);
    try {
      await deleteTemplate(id);
      router.push('/admin/marketing/templates');
    } catch { setError('삭제 실패'); setDeleting(false); }
  }

  if (loading) return <p style={{ color: '#aaa', fontSize: 13, padding: 20 }}>불러오는 중...</p>;

  return (
    <div>
      <div style={{ marginBottom: 20, padding: '20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
        <h1 style={{ fontSize: 16, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>템플릿 편집 #{id}</h1>
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

        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 4 }}>템플릿 본문</label>
          <p style={{ fontSize: 11, color: '#aaa', margin: '0 0 6px' }}>변수는 {'${변수명}'} 형식으로 입력하세요.</p>
          <textarea value={bodyTemplate} onChange={e => setBodyTemplate(e.target.value)}
            style={{ width: '100%', minHeight: 200, padding: '10px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, fontFamily: 'monospace', boxSizing: 'border-box', resize: 'vertical' }} />
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            {confirmDelete ? (
              <div style={{ display: 'flex', gap: 6 }}>
                <button onClick={handleDelete} disabled={deleting}
                  style={{ padding: '8px 16px', background: '#b33333', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
                  {deleting ? '삭제 중...' : '삭제 확인'}
                </button>
                <button onClick={() => setConfirmDelete(false)}
                  style={{ padding: '8px 16px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
                  취소
                </button>
              </div>
            ) : (
              <button onClick={() => setConfirmDelete(true)}
                style={{ padding: '8px 16px', background: '#ffe6e6', color: '#b33333', border: '1px solid #ffcccc', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
                삭제
              </button>
            )}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
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
    </div>
  );
}
