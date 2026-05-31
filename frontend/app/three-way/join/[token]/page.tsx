'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { threeWayApi } from '@/lib/api/community/threeWayApi';

export default function ThreeWayJoinPage() {
  const params = useParams();
  const router = useRouter();
  const token = params.token as string;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleJoin = async () => {
    try {
      setLoading(true);
      const session = await threeWayApi.join(token);
      router.push(`/three-way/${session.id}`);
    } catch {
      setError('참여할 수 없습니다.');
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 640, margin: '0 auto', padding: '32px 16px', fontFamily: 'inherit', textAlign: 'center' }}>
      <h1 style={{ fontSize: 20, fontWeight: 600, marginBottom: 16, color: '#1A1A2E' }}>3자 대화 참여</h1>
      <p style={{ fontSize: 14, color: '#A08670', marginBottom: 24 }}>상대방이 초대한 대화방에 참여합니다.</p>
      {error && <div style={{ color: '#C33', marginBottom: 16, fontSize: 13 }}>{error}</div>}
      <button
        onClick={handleJoin}
        disabled={loading}
        style={{ padding: '14px 32px', background: '#5C4030', color: 'white', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: 'pointer', opacity: loading ? 0.6 : 1 }}
      >
        {loading ? '참여 중...' : '참여하기'}
      </button>
    </div>
  );
}
