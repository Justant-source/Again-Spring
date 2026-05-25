'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import { createStory, type StoryRequest } from '@/lib/api/marketing/storyApi';

const RELATION_TYPES = [
  { value: 'friend', label: '친구' },
  { value: 'sibling', label: '형제자매' },
  { value: 'couple', label: '연인' },
  { value: 'marriage', label: '부부' },
  { value: 'work', label: '직장' },
  { value: 'other', label: '기타' },
];

export default function NewStoryPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState<StoryRequest>({
    sourcePlatform: '',
    sourceUrl: '',
    rawText: '',
    relationType: '',
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');

    if (!form.sourcePlatform.trim()) {
      setError('출처 플랫폼을 입력하세요.');
      return;
    }
    if (!form.rawText.trim()) {
      setError('원문 사연을 입력하세요.');
      return;
    }
    if (!form.relationType) {
      setError('관계 유형을 선택하세요.');
      return;
    }

    setLoading(true);
    try {
      const result = await createStory({
        sourcePlatform: form.sourcePlatform.trim(),
        sourceUrl: form.sourceUrl?.trim() || undefined,
        rawText: form.rawText.trim(),
        relationType: form.relationType,
      });
      router.push(`/admin/marketing/stories/${result.id}`);
    } catch (e: any) {
      setError(e.response?.data?.message || '사연을 등록하지 못했어요. 잠시 후 다시 시도해주세요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AdminSection title="사연 등록">
      <form onSubmit={handleSubmit} style={{ maxWidth: 600 }}>
        {error && (
          <div style={{
            padding: 12,
            background: '#ffe6e6',
            color: '#b33333',
            borderRadius: 6,
            marginBottom: 16,
            fontSize: 13,
          }}>
            {error}
          </div>
        )}

        <div style={{ marginBottom: 18 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6, color: '#1A1A2E' }}>
            출처 플랫폼 <span style={{ color: '#e55' }}>*</span>
          </label>
          <input
            type="text"
            placeholder="예: 네이버 카페, 인스타그램"
            value={form.sourcePlatform}
            onChange={(e) => setForm({ ...form, sourcePlatform: e.target.value })}
            style={{
              width: '100%',
              padding: '10px 12px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 13,
              fontFamily: 'inherit',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div style={{ marginBottom: 18 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6, color: '#1A1A2E' }}>
            출처 URL
          </label>
          <input
            type="text"
            placeholder="예: https://..."
            value={form.sourceUrl || ''}
            onChange={(e) => setForm({ ...form, sourceUrl: e.target.value })}
            style={{
              width: '100%',
              padding: '10px 12px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 13,
              fontFamily: 'inherit',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div style={{ marginBottom: 18 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6, color: '#1A1A2E' }}>
            관계 유형 <span style={{ color: '#e55' }}>*</span>
          </label>
          <select
            value={form.relationType}
            onChange={(e) => setForm({ ...form, relationType: e.target.value })}
            style={{
              width: '100%',
              padding: '10px 12px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 13,
              fontFamily: 'inherit',
              boxSizing: 'border-box',
              backgroundColor: 'white',
            }}
          >
            <option value="">-- 선택하세요 --</option>
            {RELATION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6, color: '#1A1A2E' }}>
            원문 사연 <span style={{ color: '#e55' }}>*</span>
          </label>
          <textarea
            placeholder="원문 사연을 입력하세요. 금지어는 자동으로 처리됩니다."
            value={form.rawText}
            onChange={(e) => setForm({ ...form, rawText: e.target.value })}
            style={{
              width: '100%',
              padding: '12px',
              border: '1px solid #ddd',
              borderRadius: 6,
              fontSize: 13,
              fontFamily: 'inherit',
              boxSizing: 'border-box',
              minHeight: 200,
              resize: 'vertical',
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={() => router.back()}
            disabled={loading}
            style={{
              padding: '9px 18px',
              background: 'white',
              color: '#555',
              border: '1px solid #ddd',
              borderRadius: 6,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            취소
          </button>
          <button
            type="submit"
            disabled={loading}
            style={{
              padding: '9px 18px',
              background: '#1A1A2E',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: loading ? 'wait' : 'pointer',
              fontSize: 13,
              fontWeight: 500,
              opacity: loading ? 0.6 : 1,
            }}
          >
            {loading ? '등록 중...' : '등록'}
          </button>
        </div>
      </form>
    </AdminSection>
  );
}
