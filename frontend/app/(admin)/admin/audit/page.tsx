'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { listAuditLogs, type AdminAuditLogResponse, type AuditLogParams } from '@/lib/api/admin/audit';
import { Badge } from '@/components/ui/badge';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { formatDateTime } from '@/lib/utils/adminFormat';
import type { PageResponse } from '@/lib/api/admin';

export default function AuditPage() {
  const user = useUserStore((s) => s.user);
  const router = useRouter();
  const [logs, setLogs] = useState<PageResponse<AdminAuditLogResponse> | null>(null);
  const [selectedLog, setSelectedLog] = useState<AdminAuditLogResponse | null>(null);
  const [filters, setFilters] = useState<AuditLogParams>({ page: 0, size: 20 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const isAuthorizedAdmin = !!user && !user.isGuest && !!user.roles?.includes('ADMIN');

  useEffect(() => {
    if (!isAuthorizedAdmin) return;

    const loadLogs = async () => {
      try {
        setLoading(true);
        const data = await listAuditLogs(filters);
        setLogs(data);
        setError('');
      } catch (e: any) {
        if (e.response?.status === 403) router.replace('/');
        else setError('감사 로그를 불러오지 못했어요.');
      } finally {
        setLoading(false);
      }
    };

    loadLogs();
  }, [isAuthorizedAdmin, router, filters]);

  if (loading) {
    return <div style={{ padding: 40, fontFamily: 'sans-serif' }}>불러오는 중…</div>;
  }
  if (error) {
    return <div style={{ padding: 40, color: '#e55', fontFamily: 'sans-serif' }}>{error}</div>;
  }

  const getActionColor = (action: string) => {
    if (action.includes('DELETE') || action.includes('SUSPEND')) return 'destructive';
    if (action.includes('CREATE') || action.includes('ADD')) return 'default';
    return 'secondary';
  };

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      <div style={{ position: 'sticky', top: 0, zIndex: 50, background: 'white', borderBottom: '1px solid #e7e3d8', padding: '12px 20px' }}>
        <AdminPageHeader
          title="감사로그"
          description="시스템 작업 기록 및 사용자 활동 감사"
        />
      </div>

      <div style={{ maxWidth: 1200, margin: '0 auto', padding: '20px 16px 60px' }}>
        {/* 필터 */}
        <div
          style={{
            marginBottom: 22,
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            필터
          </h2>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <input
              type="text"
              placeholder="행위자 사용자 ID"
              value={filters.actor ?? ''}
              onChange={(e) => setFilters((prev) => ({ ...prev, actor: e.target.value, page: 0 }))}
              style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, minWidth: 150 }}
            />
            <input
              type="text"
              placeholder="액션 (DELETE_POST 등)"
              value={filters.action ?? ''}
              onChange={(e) => setFilters((prev) => ({ ...prev, action: e.target.value, page: 0 }))}
              style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, minWidth: 150 }}
            />
            <input
              type="text"
              placeholder="대상 타입 (POST, USER 등)"
              value={filters.targetType ?? ''}
              onChange={(e) => setFilters((prev) => ({ ...prev, targetType: e.target.value, page: 0 }))}
              style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 6, minWidth: 150 }}
            />
            <button
              onClick={() => setFilters({ page: 0, size: 20 })}
              style={{
                padding: '8px 14px',
                background: '#f7f6f2',
                border: '1px solid #ddd',
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 12,
              }}
            >
              초기화
            </button>
          </div>
        </div>

        {/* 로그 테이블 */}
        <div
          style={{
            padding: 16,
            background: 'white',
            borderRadius: 12,
            border: '1px solid #e7e3d8',
          }}
        >
          <h2 style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            감사 로그 목록
          </h2>

          <AdminTable
            data={logs?.content ?? []}
            columns={[
              {
                key: 'createdAt',
                header: '시각',
                render: (log) => formatDateTime(log.createdAt),
              },
              {
                key: 'actorUserId',
                header: '행위자',
                render: (log) => (
                  <span style={{ fontFamily: 'ui-monospace', fontSize: 11 }}>
                    {log.actorUserId.slice(0, 12)}
                  </span>
                ),
              },
              {
                key: 'action',
                header: '액션',
                render: (log) => (
                  <Badge variant={getActionColor(log.action)}>
                    {log.action}
                  </Badge>
                ),
              },
              {
                key: 'targetType',
                header: '대상',
                render: (log) => (
                  <span style={{ fontSize: 11, color: '#666' }}>
                    {log.targetType ?? '-'} {log.targetId ? `(${log.targetId.slice(0, 8)})` : ''}
                  </span>
                ),
              },
              {
                key: 'ip',
                header: 'IP',
                render: (log) => (
                  <span style={{ fontSize: 10, color: '#888' }}>
                    {log.ip ?? '-'}
                  </span>
                ),
              },
              {
                key: 'id',
                header: '',
                render: () => <span style={{ fontSize: 12, cursor: 'pointer' }}>→</span>,
              },
            ]}
            loading={loading}
            emptyMessage="감사 로그가 없습니다."
            rowKey={(log) => log.id}
            onRowClick={(log) => setSelectedLog(log)}
          />

          {logs && logs.totalPages > 1 && (
            <AdminPagination
              page={filters.page ?? 0}
              totalPages={logs.totalPages}
              onPageChange={(page) => setFilters((prev) => ({ ...prev, page }))}
              loading={loading}
            />
          )}
        </div>
      </div>

      {/* 상세 모달 */}
      {selectedLog && (
        <div
          role="dialog"
          aria-modal="true"
          onClick={() => setSelectedLog(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10000,
            padding: 16,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'white',
              borderRadius: 12,
              maxWidth: 800,
              width: '100%',
              maxHeight: '80vh',
              overflowY: 'auto',
              padding: 22,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: '#111' }}>감사 로그 상세</div>
              <button
                onClick={() => setSelectedLog(null)}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: 20,
                  color: '#888',
                  cursor: 'pointer',
                }}
              >
                ×
              </button>
            </div>

            <div style={{ background: '#f7f6f2', borderRadius: 6, padding: 12, marginBottom: 16 }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <tbody>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666', minWidth: 100 }}>
                      <strong>ID</strong>
                    </td>
                    <td style={{ padding: '4px 8px' }}>{selectedLog.id}</td>
                  </tr>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666' }}>
                      <strong>시각</strong>
                    </td>
                    <td style={{ padding: '4px 8px' }}>
                      {formatDateTime(selectedLog.createdAt)}
                    </td>
                  </tr>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666' }}>
                      <strong>행위자</strong>
                    </td>
                    <td style={{ padding: '4px 8px', fontFamily: 'ui-monospace', fontSize: 11 }}>
                      {selectedLog.actorUserId}
                    </td>
                  </tr>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666' }}>
                      <strong>액션</strong>
                    </td>
                    <td style={{ padding: '4px 8px' }}>{selectedLog.action}</td>
                  </tr>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666' }}>
                      <strong>대상</strong>
                    </td>
                    <td style={{ padding: '4px 8px' }}>
                      {selectedLog.targetType} ({selectedLog.targetId ?? '-'})
                    </td>
                  </tr>
                  <tr>
                    <td style={{ padding: '4px 8px', color: '#666' }}>
                      <strong>IP</strong>
                    </td>
                    <td style={{ padding: '4px 8px' }}>{selectedLog.ip ?? '-'}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* JSON 비교 */}
            {(selectedLog.beforeJson || selectedLog.afterJson) && (
              <>
                <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', marginBottom: 8 }}>
                  변경 내용
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                  {selectedLog.beforeJson && (
                    <div
                      style={{
                        background: '#fff3f0',
                        border: '1px solid #f3e5e0',
                        borderRadius: 6,
                        padding: 10,
                      }}
                    >
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#a02020', marginBottom: 6 }}>
                        변경 전
                      </div>
                      <pre
                        style={{
                          fontSize: 10,
                          overflow: 'auto',
                          maxHeight: 200,
                          fontFamily: 'ui-monospace',
                          margin: 0,
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        }}
                      >
                        {JSON.stringify(JSON.parse(selectedLog.beforeJson), null, 2)}
                      </pre>
                    </div>
                  )}
                  {selectedLog.afterJson && (
                    <div
                      style={{
                        background: '#e7f6ee',
                        border: '1px solid #c7e9dd',
                        borderRadius: 6,
                        padding: 10,
                      }}
                    >
                      <div style={{ fontSize: 11, fontWeight: 600, color: '#0e6e3f', marginBottom: 6 }}>
                        변경 후
                      </div>
                      <pre
                        style={{
                          fontSize: 10,
                          overflow: 'auto',
                          maxHeight: 200,
                          fontFamily: 'ui-monospace',
                          margin: 0,
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        }}
                      >
                        {JSON.stringify(JSON.parse(selectedLog.afterJson), null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
