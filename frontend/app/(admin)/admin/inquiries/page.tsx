'use client';

import { useEffect, useState } from 'react';
import { listInquiries, getInquiryDetail } from '@/lib/api/admin/inquiries';
import { getAdminFeedbacks, updateFeedbackStatus } from '@/lib/api/admin';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { InquiryThread } from '@/components/admin/inquiries/InquiryThread';
import { FeedbackDetailModal, AdminFeedback } from '@/components/admin/FeedbackDetailModal';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Eye } from 'lucide-react';
import { formatDate } from '@/lib/utils/adminFormat';
import type { AdminInquiry } from '@/lib/api/admin/inquiries';
import type { PageResponse } from '@/lib/api/admin';

interface InquiriesPage extends PageResponse<AdminInquiry> {}
interface FeedbacksPage {
  content: AdminFeedback[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const INQUIRY_STATUS_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  OPEN: { label: '열림', variant: 'default' },
  ANSWERED: { label: '답변됨', variant: 'secondary' },
  CLOSED: { label: '종료', variant: 'outline' },
};

const INQUIRY_CATEGORY_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  '기술지원': { label: '기술지원', variant: 'default' },
  '결제': { label: '결제', variant: 'secondary' },
  '계정': { label: '계정', variant: 'secondary' },
  '기타': { label: '기타', variant: 'outline' },
};

const FEEDBACK_CATEGORY_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  ui_bug: { label: 'UI 버그', variant: 'destructive' },
  feature: { label: '기능 제안', variant: 'default' },
  content: { label: '내용/카피', variant: 'secondary' },
  crisis: { label: '위기', variant: 'destructive' },
  praise: { label: '칭찬', variant: 'secondary' },
  other: { label: '기타', variant: 'outline' },
};

const FEEDBACK_STATUS_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  pending: { label: '대기', variant: 'default' },
  reviewed: { label: '검토 완료', variant: 'secondary' },
  resolved: { label: '해결됨', variant: 'outline' },
};

export default function AdminInquiriesPage() {
  // ===== 1:1 문의 탭 =====
  const [inquiries, setInquiries] = useState<AdminInquiry[]>([]);
  const [inquiryPage, setInquiryPage] = useState(0);
  const [inquiryTotalPages, setInquiryTotalPages] = useState(0);
  const [inquiryStatus, setInquiryStatus] = useState<string>('ALL');
  const [inquirySearch, setInquirySearch] = useState('');
  const [inquiryLoading, setInquiryLoading] = useState(false);
  const [selectedInquiryId, setSelectedInquiryId] = useState<string | null>(null);

  // ===== 의견함 탭 =====
  const [feedbacks, setFeedbacks] = useState<AdminFeedback[]>([]);
  const [feedbackPage, setFeedbackPage] = useState(0);
  const [feedbackTotalPages, setFeedbackTotalPages] = useState(0);
  const [feedbackLoading, setFeedbackLoading] = useState(false);
  const [selectedFeedback, setSelectedFeedback] = useState<AdminFeedback | null>(null);

  // 1:1 문의 로드
  const loadInquiries = async (pageNum: number = 0) => {
    setInquiryLoading(true);
    try {
      const res = await listInquiries({
        status: inquiryStatus === 'ALL' ? undefined : inquiryStatus || undefined,
        page: pageNum,
        size: 20,
      });
      // 클라이언트 측 필터링 (subject contains)
      let filtered = res.content;
      if (inquirySearch.trim()) {
        const q = inquirySearch.toLowerCase();
        filtered = filtered.filter(i => i.subject.toLowerCase().includes(q));
      }
      setInquiries(filtered);
      setInquiryTotalPages(res.totalPages);
      setInquiryPage(pageNum);
    } catch (error) {
      console.error('Failed to load inquiries:', error);
    } finally {
      setInquiryLoading(false);
    }
  };

  // 의견함 로드
  const loadFeedbacks = async (pageNum: number = 0) => {
    setFeedbackLoading(true);
    try {
      const res = await getAdminFeedbacks({
        page: pageNum,
        status: undefined,
      }) as FeedbacksPage;
      setFeedbacks(res.content || []);
      setFeedbackTotalPages(res.totalPages);
      setFeedbackPage(pageNum);
    } catch (error) {
      console.error('Failed to load feedbacks:', error);
    } finally {
      setFeedbackLoading(false);
    }
  };

  // 초기 로드
  useEffect(() => {
    loadInquiries(0);
  }, [inquiryStatus]);

  useEffect(() => {
    loadFeedbacks(0);
  }, []);

  // 문의 스레드 업데이트 후
  const handleInquiryUpdated = () => {
    loadInquiries(inquiryPage);
  };

  // 의견함 업데이트 후
  const handleFeedbackUpdated = (updated: AdminFeedback) => {
    setFeedbacks(feedbacks.map(f => f.id === updated.id ? updated : f));
    setSelectedFeedback(null);
  };

  // Inquiry columns
  const inquiryColumns = [
    {
      key: 'subject',
      header: '제목',
      render: (row: AdminInquiry) => (
        <span className="font-medium truncate">{row.subject}</span>
      ),
    },
    {
      key: 'userId',
      header: '사용자',
      render: (row: AdminInquiry) => (
        <span className="text-xs font-mono text-gray-600">{row.userId}</span>
      ),
    },
    {
      key: 'category',
      header: '카테고리',
      render: (row: AdminInquiry) => {
        const categoryBadge = INQUIRY_CATEGORY_BADGE[row.category] || INQUIRY_CATEGORY_BADGE['기타'];
        return <Badge variant={categoryBadge.variant}>{categoryBadge.label}</Badge>;
      },
    },
    {
      key: 'status',
      header: '상태',
      render: (row: AdminInquiry) => {
        const statusBadge = INQUIRY_STATUS_BADGE[row.status] || INQUIRY_STATUS_BADGE.OPEN;
        return <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>;
      },
    },
    {
      key: 'createdAt',
      header: '등록일',
      render: (row: AdminInquiry) => (
        <span className="text-xs text-gray-600">{formatDate(row.createdAt)}</span>
      ),
    },
    {
      key: 'updatedAt',
      header: '마지막 답변',
      render: (row: AdminInquiry) => (
        <span className="text-xs text-gray-600">
          {row.status === 'CLOSED' ? '종료됨' : row.status === 'ANSWERED' ? formatDate(row.updatedAt) : '-'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: AdminInquiry) => (
        <div className="text-right">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setSelectedInquiryId(row.id)}
            className="gap-1"
          >
            <Eye size={14} />
            {row.status === 'ANSWERED' ? '답변' : '상세'}
          </Button>
        </div>
      ),
    },
  ];

  // Feedback columns
  const feedbackColumns = [
    {
      key: 'category',
      header: '카테고리',
      render: (row: AdminFeedback) => {
        const fbCategoryBadge = FEEDBACK_CATEGORY_BADGE[row.category] || FEEDBACK_CATEGORY_BADGE.other;
        return <Badge variant={fbCategoryBadge.variant}>{fbCategoryBadge.label}</Badge>;
      },
    },
    {
      key: 'content',
      header: '내용',
      render: (row: AdminFeedback) => (
        <span className="text-gray-700 max-w-xs truncate">{row.content}</span>
      ),
    },
    {
      key: 'status',
      header: '상태',
      render: (row: AdminFeedback) => {
        const fbStatusBadge = FEEDBACK_STATUS_BADGE[row.status] || FEEDBACK_STATUS_BADGE.pending;
        return <Badge variant={fbStatusBadge.variant}>{fbStatusBadge.label}</Badge>;
      },
    },
    {
      key: 'contactConsent',
      header: '회신 동의',
      render: (row: AdminFeedback) => (
        <span className="text-xs">
          {row.contactConsent ? '동의' : '비동의'}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: '등록일',
      render: (row: AdminFeedback) => (
        <span className="text-xs text-gray-600">{formatDate(row.createdAt) || '-'}</span>
      ),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: AdminFeedback) => (
        <div className="text-right">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setSelectedFeedback(row)}
            className="gap-1"
          >
            <Eye size={14} />
            상세보기
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 p-6">
      <AdminPageHeader
        title="문의·의견함 관리"
        description="사용자 문의와 의견을 관리합니다"
      />

      <div>
        <Tabs defaultValue="inquiries" className="w-full">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="inquiries">1:1 문의</TabsTrigger>
            <TabsTrigger value="feedback">의견함</TabsTrigger>
          </TabsList>

          {/* ===== 1:1 문의 탭 ===== */}
          <TabsContent value="inquiries" className="space-y-4 mt-4">
            {/* 필터 */}
            <div className="flex gap-3 flex-wrap">
              <Input
                placeholder="제목 검색..."
                value={inquirySearch}
                onChange={(e) => setInquirySearch(e.target.value)}
                className="flex-1 min-w-[200px]"
              />
              <Select value={inquiryStatus} onValueChange={setInquiryStatus}>
                <SelectTrigger className="w-[140px]">
                  <SelectValue placeholder="상태" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">모두</SelectItem>
                  <SelectItem value="OPEN">열림</SelectItem>
                  <SelectItem value="ANSWERED">답변됨</SelectItem>
                  <SelectItem value="CLOSED">종료</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* 문의 테이블 */}
            <div className="space-y-4">
              <AdminTable
                data={inquiries}
                columns={inquiryColumns}
                loading={inquiryLoading}
                emptyMessage="문의가 없습니다"
                rowKey={(row) => row.id}
              />
              {inquiryTotalPages > 1 && (
                <AdminPagination
                  page={inquiryPage}
                  totalPages={inquiryTotalPages}
                  onPageChange={loadInquiries}
                  loading={inquiryLoading}
                />
              )}
            </div>
          </TabsContent>

          {/* ===== 의견함 탭 ===== */}
          <TabsContent value="feedback" className="space-y-4 mt-4">
            {/* 의견함 테이블 */}
            <div className="space-y-4">
              <AdminTable
                data={feedbacks}
                columns={feedbackColumns}
                loading={feedbackLoading}
                emptyMessage="의견이 없습니다"
                rowKey={(row) => row.id}
              />
              {feedbackTotalPages > 1 && (
                <AdminPagination
                  page={feedbackPage}
                  totalPages={feedbackTotalPages}
                  onPageChange={loadFeedbacks}
                  loading={feedbackLoading}
                />
              )}
            </div>
          </TabsContent>
        </Tabs>
      </div>

      {/* 문의 스레드 다이얼로그 */}
      <InquiryThread
        inquiryId={selectedInquiryId}
        onClose={() => setSelectedInquiryId(null)}
        onUpdated={handleInquiryUpdated}
      />

      {/* 의견함 상세 모달 */}
      <FeedbackDetailModal
        feedback={selectedFeedback}
        onClose={() => setSelectedFeedback(null)}
        onUpdated={handleFeedbackUpdated}
      />
    </div>
  );
}
