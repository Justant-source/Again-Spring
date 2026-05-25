'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useUserStore } from '@/lib/store/userStore';
import {
  getAdminSummary, getAdminDailyStats, getAdminRetention,
  getAdminFeedbacks, searchUsers, listUsers, getCrisisRecent, updateUserRoles,
  type CrisisMessage, type AdminUserListItem, type PageResponse,
} from '@/lib/api/admin';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, BarChart, Bar, Legend,
} from 'recharts';
import { FeedbackDetailModal, CATEGORY_BADGE, type AdminFeedback } from '@/components/admin/FeedbackDetailModal';
import { UserDetailModal } from '@/components/admin/UserDetailModal';
import { SystemHealthPanel } from '@/components/admin/SystemHealthPanel';
import { LlmFailureRateChart } from '@/components/admin/LlmFailureRateChart';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminStatCard } from '@/components/admin/AdminStatCard';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { AdminFilters } from '@/components/admin/AdminFilters';
import { AdminTable } from '@/components/admin/AdminTable';

const SUMMARY_LABEL: Record<string, string> = {
  todayTotalSessions: '오늘 전체 세션',
  todayCompletedSessions: '오늘 완료 세션',
  todayGuestSessions: '오늘 게스트 세션',
  todayMemberSessions: '오늘 회원 세션',
  todayNewUsers: '오늘 신규 가입',
  avgTurnsToday: '평균 턴 수',
  finalizeRate: '완료율',
  totalFeedbacks: '총 의견 수',
};

const STATUS_FILTERS: { value: string; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'pending', label: '대기' },
  { value: 'reviewed', label: '검토' },
  { value: 'resolved', label: '해결' },
];

const CRISIS_POLL_MS = 30_000;

export default function AdminPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();
  const [summary, setSummary] = useState<Record<string, number> | null>(null);
  const [dailyStats, setDailyStats] = useState<any[]>([]);
  const [retention, setRetention] = useState<any[]>([]);
  const [feedbacks, setFeedbacks] = useState<AdminFeedback[]>([]);
  const [feedbackStatusFilter, setFeedbackStatusFilter] = useState('');
  const [crisis, setCrisis] = useState<CrisisMessage[]>([]);
  const [userSearchQ, setUserSearchQ] = useState('');
  const [userResults, setUserResults] = useState<AdminUserListItem[]>([]);
  const [userPage, setUserPage] = useState<PageResponse<AdminUserListItem> | null>(null);
  const [userPageNum, setUserPageNum] = useState(0);
  const [includeGuest, setIncludeGuest] = useState(false);
  const [searchMode, setSearchMode] = useState(false);
  const [usersLoading, setUsersLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [selectedFeedback, setSelectedFeedback] = useState<AdminFeedback | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [roleUpdating, setRoleUpdating] = useState<string | null>(null); // userId being updated
  const [refreshSignal, setRefreshSignal] = useState(0);


  // 권한 검증된 ADMIN 사용자만 데이터 로드 (loadAll의 가드)
  // layout.tsx에서 권한 확인되었으므로 user가 있으면 ADMIN 권한 확보
  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  const loadAll = useCallback(async () => {
    try {
      const [s, d, r, f, c] = await Promise.all([
        getAdminSummary(),
        getAdminDailyStats(),
        getAdminRetention(),
        getAdminFeedbacks({ page: 0, status: feedbackStatusFilter || undefined }),
        getCrisisRecent(20),
      ]);
      setSummary(s);
      setDailyStats(d);
      setRetention(r);
      setFeedbacks(f?.content ?? []);
      setCrisis(c);
    } catch (e: any) {
      if (e.response?.status === 403) router.replace('/');
      else setError('데이터를 불러오지 못했어요.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [feedbackStatusFilter, router]);

  useEffect(() => {
    if (!isAuthorizedAdmin) return;
    loadAll();
  }, [loadAll, isAuthorizedAdmin]);

  // 위기 섹션 폴링 (30초) — ADMIN 권한 통과 시에만 시작
  useEffect(() => {
    if (!isAuthorizedAdmin) return;
    const id = setInterval(() => {
      getCrisisRecent(20).then(setCrisis).catch(() => {});
    }, CRISIS_POLL_MS);
    return () => clearInterval(id);
  }, [isAuthorizedAdmin]);

  function handleRefresh() {
    setRefreshing(true);
    loadAll();
    loadUsers(userPageNum, includeGuest);
    setRefreshSignal((n) => n + 1); // SystemHealthPanel + LlmFailureRateChart 즉시 재요청
  }

  const loadUsers = useCallback(async (page: number, withGuest: boolean) => {
    setUsersLoading(true);
    try {
      const result = await listUsers({ page, size: 20, includeGuest: withGuest });
      setUserPage(result);
      setUserPageNum(result.number);
      setSearchMode(false);
    } catch {
      // 권한/네트워크 오류는 상위 error 처리
    } finally {
      setUsersLoading(false);
    }
  }, []);

  // 첫 진입 시 회원 목록 자동 로드 (ADMIN 권한 통과 후)
  useEffect(() => {
    if (!isAuthorizedAdmin) return;
    loadUsers(0, includeGuest);
  }, [loadUsers, includeGuest, isAuthorizedAdmin]);

  async function handleUserSearch() {
    const q = userSearchQ.trim();
    if (!q) {
      // 검색어 비우면 전체 목록으로 복귀
      setSearchMode(false);
      loadUsers(0, includeGuest);
      return;
    }
    setUsersLoading(true);
    try {
      const results = await searchUsers(q);
      setUserResults(results);
      setSearchMode(true);
    } finally {
      setUsersLoading(false);
    }
  }

  function handleFeedbackUpdated(updated: AdminFeedback) {
    setFeedbacks((prev) => prev.map((fb) => (fb.id === updated.id ? updated : fb)));
  }

  function handleUserAnonymized(id: string) {
    setUserResults((prev) => prev.filter((u) => u.id !== id));
    setUserPage((prev) =>
      prev ? { ...prev, content: prev.content.filter((u) => u.id !== id) } : prev,
    );
  }

  // 표시할 사용자 데이터: 검색 모드면 검색 결과, 아니면 페이지 결과
  const displayedUsers: AdminUserListItem[] = searchMode
    ? userResults
    : userPage?.content ?? [];

  // 권한 미통과 시 콘텐츠 절대 노출 X (BE 가드의 2차 보호)
  // layout.tsx에서 권한 확인했으므로 여기선 로딩/에러만 처리
  if (loading) return <div style={{ padding: 40, fontFamily: 'sans-serif' }}>로딩 중...</div>;
  if (error) return <div style={{ padding: 40, color: '#e55', fontFamily: 'sans-serif' }}>{error}</div>;

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      {/* 자체 헤더 */}
      <header
        style={{
          position: 'sticky', top: 0, zIndex: 50,
          background: 'white', borderBottom: '1px solid #e7e3d8',
          padding: '12px 20px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}
      >
        <button
          onClick={() => router.push('/')}
          style={{ background: 'none', border: 'none', fontSize: 13, color: '#555', cursor: 'pointer', padding: 6 }}
        >
          ← 다시봄 메인
        </button>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>관리자 대시보드</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Link
            href="/history"
            style={{ fontSize: 13, color: '#555', textDecoration: 'none', padding: '6px 4px' }}
          >
            지난 대화
          </Link>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            aria-label="새로고침"
            style={{
              background: '#1A1A2E', color: 'white', border: 'none',
              padding: '6px 12px', borderRadius: 6, fontSize: 12, cursor: 'pointer',
              opacity: refreshing ? 0.6 : 1,
            }}
          >
            {refreshing ? '...' : '↻ 새로고침'}
          </button>
        </div>
      </header>

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 60px' }}>
        {/* 시스템 헬스 (V11) */}
        <SystemHealthPanel refreshSignal={refreshSignal} />

        {/* 요약 카드 */}
        {summary && (
          <AdminSection title="오늘 요약">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                gap: 12,
              }}
            >
              {Object.entries(summary).map(([k, v]) => (
                <AdminStatCard key={k} label={SUMMARY_LABEL[k] || k} value={formatStat(k, v)} />
              ))}
            </div>
          </AdminSection>
        )}

        {/* 추세 차트 */}
        <AdminSection title="추세 차트">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 18 }}>
            <ChartBox title="일별 세션 (최근 30일)">
              {dailyStats.length === 0 ? <EmptyState /> : (
                <ResponsiveContainer width="100%" height={200}>
                  <LineChart data={[...dailyStats].reverse()}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Line type="monotone" dataKey="memberSessions" stroke="#1A1A2E" dot={false} name="회원" />
                    <Line type="monotone" dataKey="guestSessions" stroke="#888" dot={false} name="게스트" />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </ChartBox>
            <ChartBox title="DAU (최근 14일)">
              {retention.length === 0 ? <EmptyState /> : (
                <ResponsiveContainer width="100%" height={200}>
                  <BarChart data={retention}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    <Bar dataKey="dau" fill="#1A1A2E" name="DAU" />
                    <Bar dataKey="newUsers" fill="#aaa" name="신규" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </ChartBox>
            <ChartBox title="LLM 호출 실패율 (최근 7일)">
              <LlmFailureRateChart days={7} refreshSignal={refreshSignal} />
            </ChartBox>
          </div>
        </AdminSection>

        {/* 위기 모니터링 */}
        <AdminSection
          title="위기 모니터링"
          badge={crisis.length > 0 ? { text: `${crisis.length}건`, color: '#e55' } : undefined}
          subtitle="30초마다 자동 갱신 · 본문은 노출하지 않습니다"
        >
          {crisis.length === 0 ? (
            <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>최근 위기 트리거 없음</p>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#fff3f0', color: '#a02020' }}>
                    {['시각 (KST)', 'Level', '세션', '발신자', '글자수'].map((h) => (
                      <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontWeight: 600 }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {crisis.map((c) => (
                    <tr key={c.messageId} style={{ borderBottom: '1px solid #f3e5e0' }}>
                      <td style={{ padding: '8px 10px', whiteSpace: 'nowrap' }}>
                        {new Date(c.createdAt).toLocaleString('ko-KR')}
                      </td>
                      <td style={{ padding: '8px 10px' }}>
                        <span style={{ background: '#a02020', color: 'white', padding: '2px 8px', borderRadius: 10, fontSize: 11, fontWeight: 600 }}>
                          {c.crisisLevel}
                        </span>
                      </td>
                      <td style={{ padding: '8px 10px', fontFamily: 'ui-monospace, monospace', fontSize: 11 }}>
                        {c.sessionId.slice(0, 12)}…
                      </td>
                      <td style={{ padding: '8px 10px' }}>{c.sender}</td>
                      <td style={{ padding: '8px 10px' }}>{c.charCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </AdminSection>

        {/* 의견함 */}
        <AdminSection title="의견함">
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 12, color: '#888' }}>상태 필터:</span>
            {STATUS_FILTERS.map((sf) => (
              <button
                key={sf.value}
                onClick={() => setFeedbackStatusFilter(sf.value)}
                style={{
                  padding: '4px 10px', borderRadius: 14, fontSize: 12,
                  border: feedbackStatusFilter === sf.value ? '1px solid #1A1A2E' : '1px solid #ddd',
                  background: feedbackStatusFilter === sf.value ? '#1A1A2E' : 'white',
                  color: feedbackStatusFilter === sf.value ? 'white' : '#555',
                  cursor: 'pointer',
                }}
              >
                {sf.label}
              </button>
            ))}
          </div>
          {feedbacks.length === 0 ? <EmptyState /> : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#f5f5f5' }}>
                    {['ID', '카테고리', '내용', '상태', '일시', ''].map((h) => (
                      <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontWeight: 600, fontSize: 12 }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {feedbacks.map((fb) => {
                    const badge = CATEGORY_BADGE[fb.category] || CATEGORY_BADGE.other;
                    return (
                      <tr key={fb.id} style={{ borderBottom: '1px solid #eee' }}>
                        <td style={{ padding: '8px 10px', fontSize: 12 }}>#{fb.id}</td>
                        <td style={{ padding: '8px 10px' }}>
                          <span style={{ fontSize: 11, fontWeight: 600, padding: '2px 8px', borderRadius: 4, background: badge.bg, color: badge.fg }}>
                            {badge.label}
                          </span>
                        </td>
                        <td style={{ padding: '8px 10px', maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
                          {fb.content}
                        </td>
                        <td style={{ padding: '8px 10px', fontSize: 12, color: fb.status === 'resolved' ? '#446620' : fb.status === 'reviewed' ? '#1a3aaa' : '#888' }}>
                          {fb.status}
                        </td>
                        <td style={{ padding: '8px 10px', fontSize: 11, color: '#888', whiteSpace: 'nowrap' }}>
                          {fb.createdAt ? new Date(fb.createdAt).toLocaleDateString('ko-KR') : '-'}
                        </td>
                        <td style={{ padding: '8px 10px' }}>
                          <button
                            onClick={() => setSelectedFeedback(fb)}
                            style={{ padding: '4px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
                          >
                            상세
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </AdminSection>

        {/* 사용자 관리 */}
        <AdminSection
          title="사용자 관리"
          subtitle="가입한 모든 사용자 목록 · 행 클릭 시 상세 보기"
          badge={!searchMode && userPage ? { text: `총 ${userPage.totalElements}명`, color: '#555' } : undefined}
        >
          <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
            <input
              value={userSearchQ}
              onChange={(e) => setUserSearchQ(e.target.value)}
              placeholder="닉네임 또는 이메일 검색"
              onKeyDown={(e) => e.key === 'Enter' && handleUserSearch()}
              style={{ flex: 1, minWidth: 200, padding: '9px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}
            />
            <button
              onClick={handleUserSearch}
              style={{ padding: '9px 18px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}
            >
              검색
            </button>
            {searchMode && (
              <button
                onClick={() => { setUserSearchQ(''); setSearchMode(false); loadUsers(0, includeGuest); }}
                style={{ padding: '9px 14px', background: 'white', color: '#555', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}
              >
                전체 목록
              </button>
            )}
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#555', cursor: 'pointer', marginLeft: 'auto' }}>
              <input
                type="checkbox"
                checked={includeGuest}
                onChange={(e) => setIncludeGuest(e.target.checked)}
                style={{ width: 14, height: 14 }}
              />
              게스트 포함
            </label>
          </div>

          {usersLoading ? (
            <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중…</p>
          ) : displayedUsers.length === 0 ? (
            <EmptyState />
          ) : (
            <>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ background: '#f5f5f5' }}>
                      {['닉네임', '이메일', '등급', '가입일', 'ID'].map((h) => (
                        <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontWeight: 600, fontSize: 12 }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {displayedUsers.map((u) => (
                      <tr
                        key={u.id}
                        onClick={() => setSelectedUserId(u.id)}
                        style={{ borderBottom: '1px solid #eee', cursor: 'pointer' }}
                        onMouseEnter={(e) => (e.currentTarget.style.background = '#fafaf5')}
                        onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                      >
                        <td style={{ padding: '8px 10px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            {u.nickname}
                            {u.roles?.includes('ADMIN') && (
                              <span style={{ padding: '1px 6px', background: '#1A1A2E', color: 'white', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>
                                ADMIN
                              </span>
                            )}
                            {u.roles?.includes('TESTER') && (
                              <span style={{ padding: '1px 6px', background: '#5B21B6', color: 'white', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>
                                TESTER
                              </span>
                            )}
                            {!u.isGuest && !u.roles?.includes('ADMIN') && (
                              <button
                                onClick={async (e) => {
                                  e.stopPropagation();
                                  setRoleUpdating(u.id);
                                  try {
                                    const isTester = u.roles?.includes('TESTER') ?? false;
                                    const nextRoles = isTester
                                      ? (u.roles ?? []).filter((r) => r !== 'TESTER')
                                      : [...(u.roles ?? ['USER']), 'TESTER'];
                                    const res = await updateUserRoles(u.id, nextRoles);
                                    setUserResults((prev) =>
                                      prev.map((x) => x.id === u.id ? { ...x, roles: res.roles } : x)
                                    );
                                    if (userPage) {
                                      setUserPage((prev) => prev ? {
                                        ...prev,
                                        content: prev.content.map((x) => x.id === u.id ? { ...x, roles: res.roles } : x),
                                      } : prev);
                                    }
                                  } catch {
                                    // ignore — user stays as-is
                                  } finally {
                                    setRoleUpdating(null);
                                  }
                                }}
                                disabled={roleUpdating === u.id}
                                style={{
                                  padding: '1px 7px',
                                  fontSize: 10,
                                  fontWeight: 500,
                                  border: `1px solid ${u.roles?.includes('TESTER') ? '#5B21B6' : '#aaa'}`,
                                  borderRadius: 4,
                                  background: 'transparent',
                                  color: u.roles?.includes('TESTER') ? '#5B21B6' : '#888',
                                  cursor: roleUpdating === u.id ? 'wait' : 'pointer',
                                }}
                              >
                                {roleUpdating === u.id ? '…' : u.roles?.includes('TESTER') ? 'TESTER 해제' : 'TESTER 부여'}
                              </button>
                            )}
                          </div>
                        </td>
                        <td style={{ padding: '8px 10px', fontSize: 12 }}>{u.email || '-'}</td>
                        <td style={{ padding: '8px 10px', fontSize: 12 }}>
                          <span
                            style={{
                              padding: '2px 8px', borderRadius: 4, fontSize: 11,
                              background: u.isGuest ? '#fff2c8' : '#dde9ff',
                              color: u.isGuest ? '#7a5a00' : '#1a3aaa',
                            }}
                          >
                            {u.isGuest ? '게스트' : (u.provider || '이메일')}
                          </span>
                        </td>
                        <td style={{ padding: '8px 10px', fontSize: 11, color: '#666', whiteSpace: 'nowrap' }}>
                          {u.createdAt ? new Date(u.createdAt).toLocaleDateString('ko-KR') : '-'}
                        </td>
                        <td style={{ padding: '8px 10px', fontSize: 11, fontFamily: 'ui-monospace, monospace', color: '#888' }}>{u.id}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* 페이지네이션 (검색 모드 아닐 때만) */}
              {!searchMode && userPage && userPage.totalPages > 1 && (
                <AdminPagination
                  page={userPageNum}
                  totalPages={userPage.totalPages}
                  onPageChange={(newPage) => loadUsers(newPage, includeGuest)}
                  loading={usersLoading}
                />
              )}
            </>
          )}
        </AdminSection>
      </div>

      {/* 모달 */}
      <FeedbackDetailModal
        feedback={selectedFeedback}
        onClose={() => setSelectedFeedback(null)}
        onUpdated={handleFeedbackUpdated}
      />
      <UserDetailModal
        userId={selectedUserId}
        onClose={() => setSelectedUserId(null)}
        onAnonymized={handleUserAnonymized}
      />
    </div>
  );
}

function ChartBox({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 12, color: '#888', marginBottom: 8, fontWeight: 600 }}>{title}</div>
      {children}
    </div>
  );
}


function EmptyState() {
  return <p style={{ color: '#aaa', fontSize: 13, padding: '12px 4px' }}>데이터 없음</p>;
}


function formatStat(key: string, value: number | string): string {
  if (key === 'finalizeRate') {
    const n = typeof value === 'number' ? value : Number(value);
    if (Number.isFinite(n)) return `${(n * 100).toFixed(1)}%`;
  }
  if (key === 'avgTurnsToday') {
    const n = typeof value === 'number' ? value : Number(value);
    if (Number.isFinite(n)) return n.toFixed(1);
  }
  return String(value);
}
