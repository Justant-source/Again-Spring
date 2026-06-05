'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
import {
  getAnnouncements,
  deleteAnnouncement,
  activateAnnouncement,
  deactivateAnnouncement,
  notifyAnnouncement,
  Announcement,
} from '@/lib/api/admin/announcements';

export default function AnnouncementsPage() {
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [isActive, setIsActive] = useState<boolean | undefined>(undefined);

  useEffect(() => {
    loadAnnouncements(0);
  }, [isActive]);

  const loadAnnouncements = async (pageNum: number) => {
    setLoading(true);
    try {
      const data = await getAnnouncements(pageNum, 20, isActive);
      setAnnouncements(data.content);
      setTotalPages(data.totalPages);
      setPage(pageNum);
    } catch (error) {
      console.error('Failed to load announcements:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('공지사항을 삭제하시겠습니까?')) return;
    try {
      await deleteAnnouncement(id);
      setAnnouncements(announcements.filter((a) => a.id !== id));
    } catch (error) {
      console.error('Failed to delete announcement:', error);
      alert('공지사항 삭제에 실패했습니다.');
    }
  };

  const handleActivate = async (id: string) => {
    try {
      await activateAnnouncement(id);
      loadAnnouncements(page);
    } catch (error) {
      console.error('Failed to activate announcement:', error);
      alert('공지사항 활성화에 실패했습니다.');
    }
  };

  const handleDeactivate = async (id: string) => {
    try {
      await deactivateAnnouncement(id);
      loadAnnouncements(page);
    } catch (error) {
      console.error('Failed to deactivate announcement:', error);
      alert('공지사항 비활성화에 실패했습니다.');
    }
  };

  const handleNotify = async (id: string) => {
    if (!confirm('모든 사용자에게 알림을 발송하시겠습니까?')) return;
    try {
      await notifyAnnouncement(id);
      alert('알림이 발송되었습니다.');
    } catch (error) {
      console.error('Failed to notify announcement:', error);
      alert('알림 발송에 실패했습니다.');
    }
  };

  return (
    <AdminSection title="공지사항 관리">
      <div className="mb-6 flex justify-end">
        <Link href="/admin/announcements/new">
          <Button>새 공지 작성</Button>
        </Link>
      </div>

      <Card className="p-6">
        {loading ? (
          <div className="text-center text-gray-500">로드 중...</div>
        ) : announcements.length === 0 ? (
          <div className="text-center text-gray-400">공지사항이 없습니다.</div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>제목</TableHead>
                    <TableHead>레벨</TableHead>
                    <TableHead>활성</TableHead>
                    <TableHead>시작일</TableHead>
                    <TableHead>종료일</TableHead>
                    <TableHead>등록일</TableHead>
                    <TableHead>액션</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {announcements.map((announcement) => (
                    <TableRow key={announcement.id}>
                      <TableCell className="font-medium">
                        {announcement.title}
                      </TableCell>
                      <TableCell>
                        <Badge
                          className={
                            announcement.level === 'WARN'
                              ? 'bg-yellow-500'
                              : 'bg-blue-500'
                          }
                        >
                          {announcement.level}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            announcement.isActive ? 'default' : 'outline'
                          }
                        >
                          {announcement.isActive ? '활성' : '비활성'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {announcement.startsAt
                          ? new Date(
                              announcement.startsAt
                            ).toLocaleDateString('ko-KR')
                          : '-'}
                      </TableCell>
                      <TableCell>
                        {announcement.endsAt
                          ? new Date(
                              announcement.endsAt
                            ).toLocaleDateString('ko-KR')
                          : '-'}
                      </TableCell>
                      <TableCell>
                        {new Date(announcement.createdAt).toLocaleDateString(
                          'ko-KR',
                          {
                            month: 'short',
                            day: 'numeric',
                          }
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Link href={`/admin/announcements/${announcement.id}`}>
                            <Button variant="outline" size="sm">
                              수정
                            </Button>
                          </Link>
                          {announcement.isActive ? (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                handleDeactivate(announcement.id)
                              }
                            >
                              비활성
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleActivate(announcement.id)}
                            >
                              활성화
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleNotify(announcement.id)}
                          >
                            알림발송
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleDelete(announcement.id)}
                          >
                            삭제
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {totalPages > 1 && (
              <div className="mt-6 flex justify-between items-center">
                <div className="text-sm text-gray-600">
                  페이지 {page + 1} / {totalPages}
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    disabled={page === 0}
                    onClick={() => loadAnnouncements(page - 1)}
                  >
                    이전
                  </Button>
                  <Button
                    variant="outline"
                    disabled={page + 1 >= totalPages}
                    onClick={() => loadAnnouncements(page + 1)}
                  >
                    다음
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </Card>
    </AdminSection>
  );
}
