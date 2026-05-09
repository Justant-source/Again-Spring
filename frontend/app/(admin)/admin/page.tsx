'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import {
  getAdminSummary, getAdminDailyStats, getAdminRetention,
  getAdminFeedbacks, searchUsers, deleteUserData,
} from '@/lib/api/admin';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, BarChart, Bar,
} from 'recharts';

export default function AdminPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();
  const [summary, setSummary] = useState<Record<string, number> | null>(null);
  const [dailyStats, setDailyStats] = useState<any[]>([]);
  const [retention, setRetention] = useState<any[]>([]);
  const [feedbacks, setFeedbacks] = useState<any[]>([]);
  const [userSearchQ, setUserSearchQ] = useState('');
  const [userResults, setUserResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Client-side ADMIN guard (서버 403이 2차 보호)
  useEffect(() => {
    if (!user) return;
    if (!user.isGuest && !(user as any).roles?.includes('ADMIN')) {
      router.replace('/');
    }
  }, [user, router]);

  useEffect(() => {
    Promise.all([
      getAdminSummary(),
      getAdminDailyStats(),
      getAdminRetention(),
      getAdminFeedbacks({ page: 0 }),
    ]).then(([s, d, r, f]) => {
      setSummary(s);
      setDailyStats(d);
      setRetention(r);
      setFeedbacks(f?.content ?? []);
    }).catch((e) => {
      if (e.response?.status === 403) router.replace('/');
      else setError('데이터를 불러오지 못했어요.');
    }).finally(() => setLoading(false));
  }, [router]);

  async function handleUserSearch() {
    if (!userSearchQ.trim()) return;
    const results = await searchUsers(userSearchQ.trim());
    setUserResults(results);
  }

  async function handleDeleteUserData(id: string) {
    if (!confirm(`사용자 ${id}의 데이터를 익명화할까요?`)) return;
    await deleteUserData(id);
    setUserResults((prev) => prev.filter((u) => u.id !== id));
  }

  if (loading) return <div style={{ padding: 40 }}>로딩 중...</div>;
  if (error) return <div style={{ padding: 40, color: '#e55' }}>{error}</div>;

  return (
    <div style={{ padding: '24px 32px', fontFamily: 'sans-serif', maxWidth: 1100 }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 24 }}>다시봄 Admin 대시보드</h1>

      {/* 요약 카드 */}
      {summary && (
        <Section title="오늘 요약">
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {Object.entries(summary).map(([k, v]) => (
              <StatCard key={k} label={k} value={String(v)} />
            ))}
          </div>
        </Section>
      )}

      {/* 일별 세션 추이 */}
      <Section title="일별 세션 (최근 30일)">
        {dailyStats.length === 0 ? (
          <EmptyState />
        ) : (
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={[...dailyStats].reverse()}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} />
              <YAxis tick={{ fontSize: 10 }} />
              <Tooltip />
              <Line type="monotone" dataKey="memberSessions" stroke="#1A1A2E" dot={false} name="회원" />
              <Line type="monotone" dataKey="guestSessions" stroke="#888" dot={false} name="게스트" />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Section>

      {/* 리텐션 */}
      <Section title="DAU 추이 (최근 14일)">
        {retention.length === 0 ? (
          <EmptyState />
        ) : (
          <ResponsiveContainer width="100%" height={180}>
            <BarChart data={retention}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} />
              <YAxis tick={{ fontSize: 10 }} />
              <Tooltip />
              <Bar dataKey="dau" fill="#1A1A2E" name="DAU" />
              <Bar dataKey="newUsers" fill="#aaa" name="신규" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Section>

      {/* 의견함 */}
      <Section title="의견함 (최신 20건)">
        {feedbacks.length === 0 ? (
          <EmptyState />
        ) : (
          <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f5f5f5' }}>
                {['ID', '카테고리', '내용', '상태', '일시'].map((h) => (
                  <th key={h} style={{ padding: '6px 10px', textAlign: 'left', fontWeight: 600 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {feedbacks.map((fb: any) => (
                <tr key={fb.id} style={{ borderBottom: '1px solid #eee' }}>
                  <td style={{ padding: '6px 10px' }}>{fb.id}</td>
                  <td style={{ padding: '6px 10px' }}>{fb.category}</td>
                  <td style={{ padding: '6px 10px', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {fb.content}
                  </td>
                  <td style={{ padding: '6px 10px' }}>{fb.status}</td>
                  <td style={{ padding: '6px 10px', fontSize: 11, color: '#888' }}>
                    {fb.createdAt ? new Date(fb.createdAt).toLocaleDateString('ko-KR') : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>

      {/* 사용자 검색·삭제 */}
      <Section title="사용자 관리">
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <input
            value={userSearchQ}
            onChange={(e) => setUserSearchQ(e.target.value)}
            placeholder="닉네임 또는 이메일"
            onKeyDown={(e) => e.key === 'Enter' && handleUserSearch()}
            style={{ flex: 1, padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}
          />
          <button
            onClick={handleUserSearch}
            style={{ padding: '8px 16px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}
          >
            검색
          </button>
        </div>
        {userResults.length > 0 && (
          <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f5f5f5' }}>
                {['ID', '닉네임', '이메일', '게스트', '삭제'].map((h) => (
                  <th key={h} style={{ padding: '6px 10px', textAlign: 'left', fontWeight: 600 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {userResults.map((u: any) => (
                <tr key={u.id} style={{ borderBottom: '1px solid #eee' }}>
                  <td style={{ padding: '6px 10px', fontSize: 11 }}>{u.id}</td>
                  <td style={{ padding: '6px 10px' }}>{u.nickname}</td>
                  <td style={{ padding: '6px 10px' }}>{u.email}</td>
                  <td style={{ padding: '6px 10px' }}>{u.isGuest ? '게스트' : '회원'}</td>
                  <td style={{ padding: '6px 10px' }}>
                    <button
                      onClick={() => handleDeleteUserData(u.id)}
                      style={{ padding: '4px 8px', background: '#e55', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
                    >
                      익명화
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 32 }}>
      <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 12, color: '#333' }}>{title}</h2>
      {children}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: '14px 20px', background: '#f9f9f9', borderRadius: 8,
      border: '1px solid #eee', minWidth: 130,
    }}>
      <div style={{ fontSize: 11, color: '#888', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, color: '#1A1A2E' }}>{value}</div>
    </div>
  );
}

function EmptyState() {
  return <p style={{ color: '#aaa', fontSize: 13 }}>데이터 없음 (베타 출시 전)</p>;
}
