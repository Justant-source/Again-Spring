'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getMarketingJob,
  publishMarketingJob,
  MarketingJob,
} from '@/lib/api/admin/marketing';
import { ExternalLink } from 'lucide-react';
import { ArtifactSection } from '@/components/admin/marketing/ArtifactSection';

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'bg-gray-200 text-gray-800',
  QUEUED: 'bg-blue-200 text-blue-800',
  RUNNING: 'bg-yellow-200 text-yellow-800',
  READY: 'bg-green-200 text-green-800',
  PUBLISHING: 'bg-orange-200 text-orange-800',
  PUBLISHED: 'bg-green-600 text-white',
  FAILED: 'bg-red-200 text-red-800',
  STALE: 'bg-gray-400 text-white',
  PARTIAL: 'bg-yellow-500 text-white',
};

export default function MarketingJobDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [job, setJob] = useState<MarketingJob | null>(null);
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);

  useEffect(() => {
    loadJob();
  }, [params.id]);

  const loadJob = async () => {
    setLoading(true);
    try {
      const data = await getMarketingJob(parseInt(params.id as string));
      setJob(data);
    } catch (error) {
      console.error('Failed to load marketing job:', error);
      alert('마케팅 잡을 불러오지 못했습니다.');
      router.push('/admin/marketing');
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async () => {
    if (!job) return;
    if (!confirm('마케팅 콘텐츠를 지금 게시하시겠습니까?')) return;

    setPublishing(true);
    try {
      const updated = await publishMarketingJob(job.id);
      setJob(updated);
      alert('게시가 완료되었습니다.');
    } catch (error) {
      console.error('Failed to publish marketing job:', error);
      alert('게시에 실패했습니다.');
    } finally {
      setPublishing(false);
    }
  };

  if (loading) {
    return (
      <AdminSection title="마케팅 잡 상세">
        <Card className="p-6">
          <div className="text-center text-gray-500">로드 중...</div>
        </Card>
      </AdminSection>
    );
  }

  if (!job) {
    return (
      <AdminSection title="마케팅 잡 상세">
        <Card className="p-6">
          <div className="text-center text-gray-500">잡을 찾을 수 없습니다.</div>
        </Card>
      </AdminSection>
    );
  }

  return (
    <AdminSection title="마케팅 잡 상세">
      <div className="space-y-6">
        {/* 기본 정보 */}
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4">기본 정보</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium text-gray-600">잡 ID</label>
              <p className="text-lg font-mono">{job.id}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">사연 ID</label>
              <p className="text-lg font-mono">{job.postId}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">상태</label>
              <p className="mt-1">
                <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200'}>
                  {job.status}
                </Badge>
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">단계</label>
              <p className="text-lg">{job.phase || '-'}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">진행률</label>
              <p className="text-lg">
                {typeof job.progress === 'number' ? `${Math.round(job.progress * 100)}%` : '-'}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">자동 게시</label>
              <p className="text-lg">
                <Badge variant={job.autoPublish ? 'default' : 'outline'}>
                  {job.autoPublish ? '활성화' : '비활성화'}
                </Badge>
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">생성일</label>
              <p className="text-lg">
                {new Date(job.createdAt).toLocaleDateString('ko-KR', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">마지막 업데이트</label>
              <p className="text-lg">
                {new Date(job.updatedAt).toLocaleDateString('ko-KR', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
          </div>
        </Card>

        {/* 타겟 플랫폼 */}
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4">타겟 플랫폼</h3>
          <div className="flex flex-wrap gap-2">
            {(job.targets ?? []).map((target) => (
              <Badge key={target} variant="secondary">
                {target}
              </Badge>
            ))}
          </div>
        </Card>

        {/* 아티팩트 */}
        {job.artifacts && Object.keys(job.artifacts).length > 0 && (
          <ArtifactSection jobId={job.id} artifacts={job.artifacts} />
        )}

        {/* 게시 기록 */}
        {job.publications && job.publications.length > 0 && (
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-4">게시 기록</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2 px-3 font-medium">플랫폼</th>
                    <th className="text-left py-2 px-3 font-medium">상태</th>
                    <th className="text-left py-2 px-3 font-medium">URL</th>
                  </tr>
                </thead>
                <tbody>
                  {job.publications.map((pub, idx) => (
                    <tr key={idx} className="border-b">
                      <td className="py-2 px-3 font-mono">{pub.platform}</td>
                      <td className="py-2 px-3">
                        <Badge variant="outline">{pub.state}</Badge>
                      </td>
                      <td className="py-2 px-3">
                        {pub.url && pub.url.startsWith('http') ? (
                          <a
                            href={pub.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-blue-600 hover:underline flex items-center gap-1"
                          >
                            링크
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}

        {/* 오류 메시지 */}
        {job.errorMessage && (
          <Card className="p-6 bg-red-50 border-red-200">
            <h3 className="text-lg font-semibold mb-4 text-red-800">오류</h3>
            <p className="text-red-700 font-mono text-sm">{job.errorMessage}</p>
          </Card>
        )}

        {/* 게시 승인 버튼 */}
        {job.status === 'READY' && !job.autoPublish && (
          <div className="flex gap-2 justify-end">
            <Link href="/admin/marketing">
              <Button variant="outline">돌아가기</Button>
            </Link>
            <Button onClick={handlePublish} disabled={publishing}>
              {publishing ? '게시 중...' : '게시 승인'}
            </Button>
          </div>
        )}

        {/* 돌아가기 버튼 */}
        {!(job.status === 'READY' && !job.autoPublish) && (
          <div className="flex justify-end">
            <Link href="/admin/marketing">
              <Button variant="outline">돌아가기</Button>
            </Link>
          </div>
        )}
      </div>
    </AdminSection>
  );
}
