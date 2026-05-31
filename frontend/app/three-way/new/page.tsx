'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { threeWayApi } from '@/lib/api/community/threeWayApi';
import { CATEGORIES } from '@/lib/constants/categories';

export default function ThreeWayNewPage() {
  const router = useRouter();
  const [category, setCategory] = useState(CATEGORIES[0]?.id || '');
  const [loading, setLoading] = useState(false);

  const handleCreate = async () => {
    try {
      setLoading(true);
      const session = await threeWayApi.create(category);
      router.push(`/three-way/${session.id}?invite=true`);
    } catch { setLoading(false); }
  };

  return (
    <div style={{ maxWidth: 640, margin: '0 auto', padding: '32px 16px', fontFamily: 'inherit' }}>
      <h1 style={{ fontSize: 20, fontWeight: 600, marginBottom: 24, color: '#1A1A2E' }}>3자 대화 시작</h1>
      <div style={{ marginBottom: 20 }}>
        <label style={{ fontSize: 12, color: '#A08670', marginBottom: 8, display: 'block' }}>카테고리</label>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          style={{ width: '100%', padding: '10px 12px', border: '1px solid #EADFD0', borderRadius: 8, fontSize: 13, background: 'white' }}
        >
          {CATEGORIES.map((c) => (
            <option key={c.id} value={c.id}>{c.label}</option>
          ))}
        </select>
      </div>
      <button
        onClick={handleCreate}
        disabled={loading}
        style={{ width: '100%', padding: 14, background: '#5C4030', color: 'white', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: 'pointer', opacity: loading ? 0.6 : 1 }}
      >
        {loading ? '생성 중...' : '대화방 만들기'}
      </button>
    </div>
  );
}
