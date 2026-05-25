'use client';

import { useEffect, useState } from 'react';
import { getHashtags, createHashtag, deleteHashtag, type Hashtag } from '@/lib/api/marketing/hashtagApi';

const PLATFORMS = [
  { value: '', label: '전체' },
  { value: 'X', label: 'X' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_BLOG', label: '네이버블로그' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'FACEBOOK', label: 'Facebook' },
];

export default function HashtagsPage() {
  const [hashtags, setHashtags] = useState<Hashtag[]>([]);
  const [platform, setPlatform] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newTag, setNewTag] = useState('');
  const [newPlatform, setNewPlatform] = useState('X');
  const [newCategory, setNewCategory] = useState('');
  const [adding, setAdding] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    try {
      setHashtags(await getHashtags(platform || undefined));
      setError('');
    } catch { setError('해시태그를 불러오지 못했습니다.'); } finally { setLoading(false); }
  }

  useEffect(() => { load(); }, [platform]);

  async function handleAdd() {
    if (!newTag.trim()) { setError('태그를 입력해주세요.'); return; }
    setAdding(true);
    try {
      const created = await createHashtag({ platform: newPlatform, tag: newTag.trim(), category: newCategory.trim() || undefined });
      setHashtags(prev => [created, ...prev]);
      setNewTag('');
      setNewCategory('');
      setError('');
    } catch (e: any) {
      setError(e.response?.data?.message ?? '추가 실패');
    } finally { setAdding(false); }
  }

  async function handleDelete(id: number) {
    setDeletingId(id);
    try {
      await deleteHashtag(id);
      setHashtags(prev => prev.filter(h => h.id !== id));
      setConfirmDeleteId(null);
    } catch { setError('삭제 실패'); } finally { setDeletingId(null); }
  }

  return (
    <div>
      <div style={{ marginBottom: 20, padding: '20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8' }}>
        <h1 style={{ fontSize: 16, fontWeight: 600, color: '#1A1A2E', margin: '0 0 4px' }}>해시태그 라이브러리</h1>
        <p style={{ fontSize: 13, color: '#888', margin: 0 }}>플랫폼별 해시태그 풀 관리 및 사용 빈도 추적</p>
      </div>

      <div style={{ padding: '16px 20px', background: 'white', borderRadius: 12, border: '1px solid #e7e3d8', marginBottom: 20 }}>
        <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>해시태그 추가</h2>
        {error && <p style={{ color: '#b33333', fontSize: 13, marginBottom: 8 }}>{error}</p>}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>플랫폼</label>
            <select value={newPlatform} onChange={e => setNewPlatform(e.target.value)}
              style={{ padding: '7px 10px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}>
              {PLATFORMS.filter(p => p.value).map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
            </select>
          </div>
          <div>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>태그</label>
            <input type="text" value={newTag} onChange={e => setNewTag(e.target.value)} placeholder="#없이 입력" maxLength={100}
              style={{ padding: '7px 10px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, width: 140 }} />
          </div>
          <div>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>카테고리</label>
            <input type="text" value={newCategory} onChange={e => setNewCategory(e.target.value)} placeholder="선택" maxLength={50}
              style={{ padding: '7px 10px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, width: 120 }} />
          </div>
          <button onClick={handleAdd} disabled={adding}
            style={{ padding: '7px 16px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: adding ? 'not-allowed' : 'pointer', fontSize: 13, opacity: adding ? 0.6 : 1 }}>
            {adding ? '추가 중...' : '추가'}
          </button>
        </div>
      </div>

      <div style={{ marginBottom: 12 }}>
        <select value={platform} onChange={e => setPlatform(e.target.value)}
          style={{ padding: '7px 12px', border: '1px solid #e7e3d8', borderRadius: 6, fontSize: 13, background: 'white' }}>
          {PLATFORMS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>
      </div>

      {loading ? <p style={{ color: '#aaa', fontSize: 13 }}>불러오는 중...</p> : (
        <div style={{ background: 'white', borderRadius: 12, border: '1px solid #e7e3d8', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #e7e3d8' }}>
                {['플랫폼', '태그', '카테고리', '사용', '마지막 사용', ''].map(h => (
                  <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 600, color: '#666', fontSize: 11 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {hashtags.length === 0 ? (
                <tr><td colSpan={6} style={{ padding: 20, textAlign: 'center', color: '#aaa' }}>해시태그가 없습니다.</td></tr>
              ) : hashtags.map(h => (
                <tr key={h.id} style={{ borderBottom: '1px solid #f0ece4' }}>
                  <td style={{ padding: '10px 14px' }}><span style={{ fontSize: 11, fontWeight: 600, padding: '2px 6px', background: '#1A1A2E', color: 'white', borderRadius: 3 }}>{h.platform}</span></td>
                  <td style={{ padding: '10px 14px', fontFamily: 'monospace', color: '#1A1A2E' }}>#{h.tag}</td>
                  <td style={{ padding: '10px 14px', color: '#888' }}>{h.category ?? '-'}</td>
                  <td style={{ padding: '10px 14px', color: '#555' }}>{h.usageCount}</td>
                  <td style={{ padding: '10px 14px', color: '#888', fontSize: 12 }}>{h.lastUsedAt ? new Date(h.lastUsedAt).toLocaleDateString('ko-KR') : '-'}</td>
                  <td style={{ padding: '10px 14px' }}>
                    {confirmDeleteId === h.id ? (
                      <div style={{ display: 'flex', gap: 4 }}>
                        <button onClick={() => handleDelete(h.id)} disabled={deletingId === h.id} style={{ padding: '3px 8px', background: '#b33333', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>확인</button>
                        <button onClick={() => setConfirmDeleteId(null)} style={{ padding: '3px 8px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>취소</button>
                      </div>
                    ) : (
                      <button onClick={() => setConfirmDeleteId(h.id)} style={{ padding: '3px 8px', background: '#ffe6e6', color: '#b33333', border: '1px solid #ffcccc', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>삭제</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
