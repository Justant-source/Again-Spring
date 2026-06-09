'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  listMarketingJobs,
  MarketingJob,
} from '@/lib/api/admin/marketing';

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'bg-gray-200 text-gray-800',
  QUEUED: 'bg-blue-200 text-blue-800',
  RUNNING: 'bg-yellow-200 text-yellow-800',
  READY: 'bg-green-200 text-green-800',
  PUBLISHING: 'bg-orange-200 text-orange-800',
  PUBLISHED: 'bg-green-600 text-white',
  FAILED: 'bg-red-200 text-red-800',
  STALE: 'bg-gray-400 text-white',
};

export default function MarketingJobsPage() {
  const [jobs, setJobs] = useState<MarketingJob[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadJobs();
  }, []);

  const loadJobs = async () => {
    setLoading(true);
    try {
      const data = await listMarketingJobs();
      setJobs(data);
    } catch (error) {
      console.error('Failed to load marketing jobs:', error);
      alert('마케팅 잡 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AdminSection title="마케팅 잡 관리">
      <div className="mb-6">
        <p className="text-sm text-gray-600">
          마케팅 잡은 사연 상세 화면에서 생성할 수 있습니다.
        </p>
      </div>

      <Card className="p-6">
        {loading ? (
          <div className="text-center text-gray-500">로드 중...</div>
        ) : jobs.length === 0 ? (
          <div className="text-center text-gray-400">마케팅 잡이 없습니다.</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>사연 ID</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>단계</TableHead>
                  <TableHead>진행률</TableHead>
                  <TableHead>생성일</TableHead>
                  <TableHead>액션</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {jobs.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell className="font-mono text-sm">{job.id}</TableCell>
                    <TableCell className="font-mono text-sm">{job.postId}</TableCell>
                    <TableCell>
                      <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200'}>
                        {job.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm">{job.phase || '-'}</TableCell>
                    <TableCell className="text-sm">{job.progress}%</TableCell>
                    <TableCell className="text-sm">
                      {new Date(job.createdAt).toLocaleDateString('ko-KR', {
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </TableCell>
                    <TableCell>
                      <Link href={`/admin/marketing/jobs/${job.id}`}>
                        <Button variant="outline" size="sm">
                          상세
                        </Button>
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>
    </AdminSection>
  );
}
