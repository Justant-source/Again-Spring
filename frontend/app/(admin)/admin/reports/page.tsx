'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { MoreVertical, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';
import {
  listReports,
  resolveReport,
  getPendingCount,
  type AdminReport,
} from '@/lib/api/admin/reports';

const PAGE_SIZE = 20;

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<'pending' | 'resolved'>('pending');
  const [pendingReports, setPendingReports] = useState<AdminReport[]>([]);
  const [resolvedReports, setResolvedReports] = useState<AdminReport[]>([]);
  const [pendingPage, setPendingPage] = useState(0);
  const [resolvedPage, setResolvedPage] = useState(0);
  const [pendingTotalPages, setPendingTotalPages] = useState(0);
  const [resolvedTotalPages, setResolvedTotalPages] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [resolving, setResolving] = useState<number | null>(null);

  // Load pending reports
  const loadPendingReports = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const data = await listReports({
        status: 'PENDING',
        page,
        size: PAGE_SIZE,
      });
      setPendingReports(data.content);
      setPendingTotalPages(data.totalPages);
      setPendingPage(page);
    } catch (error) {
      console.error('Failed to load pending reports:', error);
      toast.error('신고 목록을 불러올 수 없습니다');
    } finally {
      setLoading(false);
    }
  }, []);

  // Load resolved reports
  const loadResolvedReports = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const data = await listReports({
        status: 'RESOLVED',
        page,
        size: PAGE_SIZE,
      });
      setResolvedReports(data.content);
      setResolvedTotalPages(data.totalPages);
      setResolvedPage(page);
    } catch (error) {
      console.error('Failed to load resolved reports:', error);
      toast.error('처리 완료 신고를 불러올 수 없습니다');
    } finally {
      setLoading(false);
    }
  }, []);

  // Load pending count
  const loadPendingCount = useCallback(async () => {
    try {
      const data = await getPendingCount();
      setPendingCount(data.count);
    } catch (error) {
      console.error('Failed to load pending count:', error);
    }
  }, []);

  // Initial load
  useEffect(() => {
    loadPendingReports(0);
    loadPendingCount();
  }, [loadPendingReports, loadPendingCount]);

  // Tab change handler
  const handleTabChange = (value: string) => {
    const tab = value as 'pending' | 'resolved';
    setActiveTab(tab);
    if (tab === 'pending') {
      loadPendingReports(0);
    } else {
      loadResolvedReports(0);
    }
  };

  // Resolve report with confirmation
  const handleResolveReport = async (
    reportId: number,
    action: 'BLOCK_POST' | 'BLOCK_COMMENT' | 'DISMISS'
  ) => {
    const confirmMessage =
      action === 'BLOCK_POST'
        ? '이 게시글을 차단하시겠습니까?'
        : action === 'BLOCK_COMMENT'
          ? '이 댓글을 차단하시겠습니까?'
          : '이 신고를 무시하시겠습니까?';

    const confirmed = window.confirm(confirmMessage);
    if (!confirmed) return;

    setResolving(reportId);
    try {
      await resolveReport(reportId, action);
      toast.success('신고가 처리되었습니다');
      setPendingReports(pendingReports.filter((r) => r.id !== reportId));
      loadPendingCount();
    } catch (error) {
      console.error('Failed to resolve report:', error);
      toast.error('신고 처리에 실패했습니다');
    } finally {
      setResolving(null);
    }
  };

  // Get badge for target type
  const getTargetTypeBadge = (targetType: string) => {
    if (targetType === 'POST') {
      return <Badge variant="outline">게시글</Badge>;
    } else if (targetType === 'COMMENT') {
      return <Badge variant="outline">댓글</Badge>;
    }
    return <Badge variant="outline">{targetType}</Badge>;
  };

  // Get badge for resolved action
  const getActionBadge = (action?: string) => {
    if (action === 'BLOCK_POST') {
      return <Badge variant="destructive">게시글 차단</Badge>;
    } else if (action === 'BLOCK_COMMENT') {
      return <Badge variant="destructive">댓글 차단</Badge>;
    } else if (action === 'DISMISS') {
      return <Badge variant="secondary">무시됨</Badge>;
    }
    return <Badge variant="secondary">{action}</Badge>;
  };

  // Format datetime
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  // Pending tab columns
  const pendingColumns = [
    {
      key: 'targetType',
      header: '신고 유형',
      render: (row: AdminReport) => getTargetTypeBadge(row.targetType),
    },
    {
      key: 'targetId',
      header: '대상 ID',
      render: (row: AdminReport) => (
        <span className="font-mono text-sm">{row.targetId.substring(0, 16)}...</span>
      ),
    },
    {
      key: 'reporterUserId',
      header: '신고자',
      render: (row: AdminReport) => (
        <span className="font-mono text-sm">
          {row.reporterUserId ? row.reporterUserId.substring(0, 16) : '익명'}
        </span>
      ),
    },
    {
      key: 'reason',
      header: '신고 사유',
      render: (row: AdminReport) => (
        <span className="max-w-xs truncate text-sm">{row.reason}</span>
      ),
    },
    {
      key: 'createdAt',
      header: '신고 일시',
      render: (row: AdminReport) => (
        <span className="text-sm text-gray-600">{formatDate(row.createdAt)}</span>
      ),
    },
    {
      key: 'actions',
      header: '처리',
      render: (row: AdminReport) => (
        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                disabled={resolving === row.id}
              >
                <MoreVertical size={16} />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {row.targetType === 'POST' && (
                <DropdownMenuItem
                  onClick={() => handleResolveReport(row.id, 'BLOCK_POST')}
                  disabled={resolving === row.id}
                  className="text-red-600"
                >
                  게시글 차단
                </DropdownMenuItem>
              )}
              {row.targetType === 'COMMENT' && (
                <DropdownMenuItem
                  onClick={() => handleResolveReport(row.id, 'BLOCK_COMMENT')}
                  disabled={resolving === row.id}
                  className="text-red-600"
                >
                  댓글 차단
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={() => handleResolveReport(row.id, 'DISMISS')}
                disabled={resolving === row.id}
              >
                신고 무시
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => {
                  window.open(`/api/admin/community/${row.targetType.toLowerCase()}/${row.targetId}`, '_blank');
                }}
              >
                <ExternalLink size={14} className="mr-2" />
                대상 보기
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ),
    },
  ];

  // Resolved tab columns
  const resolvedColumns = [
    {
      key: 'targetType',
      header: '신고 유형',
      render: (row: AdminReport) => getTargetTypeBadge(row.targetType),
    },
    {
      key: 'targetId',
      header: '대상 ID',
      render: (row: AdminReport) => (
        <span className="font-mono text-sm">{row.targetId.substring(0, 16)}...</span>
      ),
    },
    {
      key: 'reporterUserId',
      header: '신고자',
      render: (row: AdminReport) => (
        <span className="font-mono text-sm">
          {row.reporterUserId ? row.reporterUserId.substring(0, 16) : '익명'}
        </span>
      ),
    },
    {
      key: 'reason',
      header: '신고 사유',
      render: (row: AdminReport) => (
        <span className="max-w-xs truncate text-sm">{row.reason}</span>
      ),
    },
    {
      key: 'createdAt',
      header: '신고 일시',
      render: (row: AdminReport) => (
        <span className="text-sm text-gray-600">{formatDate(row.createdAt)}</span>
      ),
    },
    {
      key: 'resolvedAt',
      header: '처리 일시',
      render: (row: AdminReport) => (
        <span className="text-sm text-gray-600">
          {row.resolvedAt ? formatDate(row.resolvedAt) : '-'}
        </span>
      ),
    },
    {
      key: 'resolvedAction',
      header: '처리 결과',
      render: (row: AdminReport) => getActionBadge(row.resolvedAction),
    },
  ];

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">신고 관리</h1>
        <p className="mt-2 text-gray-600">부적절한 콘텐츠 신고를 검토하고 처리합니다</p>
      </div>

      <Card>
        <Tabs value={activeTab} onValueChange={handleTabChange} className="w-full">
          <CardHeader className="border-b">
            <TabsList className="w-full justify-start">
              <TabsTrigger value="pending" className="flex items-center gap-2">
                처리 대기
                {pendingCount > 0 && (
                  <Badge variant="destructive" className="ml-2">
                    {pendingCount}
                  </Badge>
                )}
              </TabsTrigger>
              <TabsTrigger value="resolved">처리 완료</TabsTrigger>
            </TabsList>
          </CardHeader>

          <CardContent className="pt-6">
            <TabsContent value="pending" className="m-0">
              <div className="space-y-4">
                <AdminTable
                  data={pendingReports}
                  columns={pendingColumns}
                  loading={loading}
                  emptyMessage="처리 대기 중인 신고가 없습니다"
                  rowKey={(row) => row.id}
                />
                {pendingTotalPages > 1 && (
                  <AdminPagination
                    page={pendingPage}
                    totalPages={pendingTotalPages}
                    onPageChange={loadPendingReports}
                    loading={loading}
                  />
                )}
              </div>
            </TabsContent>

            <TabsContent value="resolved" className="m-0">
              <div className="space-y-4">
                <AdminTable
                  data={resolvedReports}
                  columns={resolvedColumns}
                  loading={loading}
                  emptyMessage="처리 완료된 신고가 없습니다"
                  rowKey={(row) => row.id}
                />
                {resolvedTotalPages > 1 && (
                  <AdminPagination
                    page={resolvedPage}
                    totalPages={resolvedTotalPages}
                    onPageChange={loadResolvedReports}
                    loading={loading}
                  />
                )}
              </div>
            </TabsContent>
          </CardContent>
        </Tabs>
      </Card>
    </div>
  );
}
