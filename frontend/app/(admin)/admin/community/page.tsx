'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api/client';
import { AdminSection } from '@/components/admin/AdminSection';

interface CommunityReport {
  id: number;
  targetType: string;
  targetId: string;
  reporterUserId?: string;
  reason: string;
  status: string;
  createdAt: string;
}

interface ReportsPage {
  content: CommunityReport[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export default function AdminCommunityPage() {
  const [reports, setReports] = useState<CommunityReport[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadReports(0);
  }, []);

  const loadReports = async (pageNum: number) => {
    setLoading(true);
    try {
      const res = await api.get<ReportsPage>('/api/admin/community/reports', {
        params: { status: 'PENDING', page: pageNum, size: 20 },
      });
      setReports(res.data.content);
      setTotalPages(res.data.totalPages);
      setPage(pageNum);
    } catch (error) {
      console.error('Failed to load reports:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleResolve = async (reportId: number, action: 'BLOCK_POST' | 'DISMISS') => {
    try {
      await api.post(`/api/admin/community/reports/${reportId}/resolve`, { action });
      setReports(reports.filter(r => r.id !== reportId));
    } catch (error) {
      console.error('Failed to resolve report:', error);
    }
  };

  const handleBlockPost = async (postId: string) => {
    try {
      await api.post(`/api/admin/community/posts/${postId}/block`);
      alert('포스트가 차단되었습니다.');
    } catch (error) {
      console.error('Failed to block post:', error);
    }
  };

  return (
    <div style={{ padding: 20 }}>
      <AdminSection title="커뮤니티 관리">
        <div style={{ marginBottom: 24, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
          <Link href="/admin/community" style={{ padding: 16, background: 'white', borderRadius: 8, border: '1px solid #e7e3d8', textDecoration: 'none', color: '#1A1A2E' }}>
            <div style={{ fontWeight: 600, fontSize: 14 }}>신고 큐</div>
            <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>부적절한 콘텐츠 신고 처리</div>
          </Link>
          <div style={{ padding: 16, background: 'white', borderRadius: 8, border: '1px solid #e7e3d8', color: '#999', cursor: 'not-allowed' }}>
            <div style={{ fontWeight: 600, fontSize: 14 }}>커뮤니티 통계</div>
            <div style={{ fontSize: 12, color: '#ccc', marginTop: 4 }}>곧 추가됩니다</div>
          </div>
        </div>
      </AdminSection>

      <AdminSection title="신고 큐">
        {loading ? (
          <div style={{ textAlign: 'center', color: '#888', padding: '20px' }}>로딩 중...</div>
        ) : reports.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#888', padding: '20px' }}>신고 없음</div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #e7e3d8', background: '#f9f7f4' }}>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>신고 ID</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>대상 유형</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>대상 ID</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>사유</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>신고자</th>
                    <th style={{ padding: '12px 8px', textAlign: 'left', fontWeight: 600 }}>작업</th>
                  </tr>
                </thead>
                <tbody>
                  {reports.map((report) => (
                    <tr key={report.id} style={{ borderBottom: '1px solid #f0ece0' }}>
                      <td style={{ padding: '8px', color: '#666' }}>{report.id}</td>
                      <td style={{ padding: '8px', color: '#666' }}>{report.targetType}</td>
                      <td style={{ padding: '8px', color: '#666', fontFamily: 'monospace', fontSize: 12 }}>{report.targetId.substring(0, 12)}...</td>
                      <td style={{ padding: '8px', color: '#666', maxWidth: 200, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{report.reason}</td>
                      <td style={{ padding: '8px', color: '#666', fontFamily: 'monospace', fontSize: 12 }}>{report.reporterUserId ? report.reporterUserId.substring(0, 12) : '게스트'}...</td>
                      <td style={{ padding: '8px', display: 'flex', gap: 6 }}>
                        {report.targetType === 'POST' && (
                          <button
                            onClick={() => handleResolve(report.id, 'BLOCK_POST')}
                            style={{
                              padding: '4px 10px',
                              background: '#D4A5A5',
                              color: 'white',
                              border: 'none',
                              borderRadius: 4,
                              cursor: 'pointer',
                              fontSize: 12,
                            }}
                          >
                            차단
                          </button>
                        )}
                        <button
                          onClick={() => handleResolve(report.id, 'DISMISS')}
                          style={{
                            padding: '4px 10px',
                            background: '#ccc',
                            color: '#666',
                            border: 'none',
                            borderRadius: 4,
                            cursor: 'pointer',
                            fontSize: 12,
                          }}
                        >
                          무시
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 16 }}>
                <button
                  onClick={() => loadReports(page - 1)}
                  disabled={page === 0}
                  style={{
                    padding: '6px 12px',
                    background: page === 0 ? '#eee' : 'white',
                    border: '1px solid #ddd',
                    borderRadius: 4,
                    cursor: page === 0 ? 'default' : 'pointer',
                    fontSize: 12,
                  }}
                >
                  이전
                </button>
                <span style={{ padding: '6px 12px', fontSize: 12 }}>
                  {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => loadReports(page + 1)}
                  disabled={page >= totalPages - 1}
                  style={{
                    padding: '6px 12px',
                    background: page >= totalPages - 1 ? '#eee' : 'white',
                    border: '1px solid #ddd',
                    borderRadius: 4,
                    cursor: page >= totalPages - 1 ? 'default' : 'pointer',
                    fontSize: 12,
                  }}
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </AdminSection>
    </div>
  );
}
