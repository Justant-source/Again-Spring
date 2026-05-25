'use client';

import { useState } from 'react';
import { updatePerformance, type PerformanceData } from '@/lib/api/marketing/performanceApi';

interface Props {
  contentId: number;
  onSaved: () => void;
}

export function PerformanceForm({ contentId, onSaved }: Props) {
  const [form, setForm] = useState<PerformanceData>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const fields: { key: keyof PerformanceData; label: string; isText?: boolean }[] = [
    { key: 'impressions', label: '노출수' },
    { key: 'likes', label: '좋아요' },
    { key: 'comments', label: '댓글' },
    { key: 'shares', label: '공유' },
    { key: 'clicks', label: '클릭' },
    { key: 'saves', label: '저장' },
    { key: 'note', label: '메모', isText: true },
  ];

  async function handleSave() {
    setSaving(true);
    setError('');
    try {
      await updatePerformance(contentId, form);
      onSaved();
    } catch (e: any) {
      setError('저장에 실패했습니다.');
      console.error(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      {error && (
        <p style={{ color: '#b33333', fontSize: 13, margin: '0 0 12px' }}>{error}</p>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 16 }}>
        {fields.map((f) => (
          <div key={f.key}>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>
              {f.label}
            </label>
            {f.isText ? (
              <input
                type="text"
                value={(form[f.key] as string) ?? ''}
                onChange={(e) => setForm((prev) => ({ ...prev, [f.key]: e.target.value }))}
                style={{
                  width: '100%',
                  padding: '6px 8px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            ) : (
              <input
                type="number"
                min={0}
                value={(form[f.key] as number) ?? ''}
                onChange={(e) => {
                  const val = e.target.value === '' ? undefined : Number(e.target.value);
                  setForm((prev) => ({ ...prev, [f.key]: val }));
                }}
                style={{
                  width: '100%',
                  padding: '6px 8px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  boxSizing: 'border-box',
                }}
              />
            )}
          </div>
        ))}
      </div>
      <button
        onClick={handleSave}
        disabled={saving}
        style={{
          padding: '8px 18px',
          background: '#1A1A2E',
          color: 'white',
          border: 'none',
          borderRadius: 6,
          cursor: saving ? 'not-allowed' : 'pointer',
          fontSize: 13,
          fontWeight: 500,
          opacity: saving ? 0.6 : 1,
        }}
      >
        {saving ? '저장 중...' : '성과 저장'}
      </button>
    </div>
  );
}
