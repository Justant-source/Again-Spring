'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getAnnouncement,
  updateAnnouncement,
  CreateAnnouncementRequest,
} from '@/lib/api/admin/announcements';

interface PageProps {
  params: {
    id: string;
  };
}

export default function EditAnnouncementPage({ params }: PageProps) {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<CreateAnnouncementRequest>({
    title: '',
    body: '',
    level: 'INFO',
  });

  useEffect(() => {
    loadAnnouncement();
  }, [params.id]);

  const loadAnnouncement = async () => {
    setLoading(true);
    try {
      const data = await getAnnouncement(params.id);
      setForm({
        title: data.title,
        body: data.body,
        level: data.level,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
      });
    } catch (error) {
      console.error('Failed to load announcement:', error);
      alert('공지사항을 불러오지 못했습니다.');
      router.push('/admin/announcements');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (
    e: React.ChangeEvent<
      HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
    >
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleDateChange = (
    e: React.ChangeEvent<HTMLInputElement>,
    field: 'startsAt' | 'endsAt'
  ) => {
    const { value } = e.target;
    setForm((prev) => ({
      ...prev,
      [field]: value ? new Date(value).toISOString() : undefined,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title || !form.body) {
      alert('제목과 본문을 입력하세요.');
      return;
    }

    setSubmitting(true);
    try {
      await updateAnnouncement(params.id, form);
      alert('공지사항이 수정되었습니다.');
      router.push('/admin/announcements');
    } catch (error) {
      console.error('Failed to update announcement:', error);
      alert('공지사항 수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <AdminSection title="공지사항 수정">
        <Card className="p-6">
          <div className="text-center text-gray-500">로드 중...</div>
        </Card>
      </AdminSection>
    );
  }

  return (
    <AdminSection title="공지사항 수정">
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium mb-2">제목 *</label>
            <Input
              name="title"
              value={form.title}
              onChange={handleChange}
              placeholder="공지사항 제목을 입력하세요"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">본문 *</label>
            <textarea
              name="body"
              value={form.body}
              onChange={handleChange}
              placeholder="공지사항 내용을 입력하세요"
              className="w-full p-3 border rounded-lg min-h-48"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2">레벨</label>
              <select
                name="level"
                value={form.level}
                onChange={handleChange}
                className="w-full p-2 border rounded-lg"
              >
                <option value="INFO">일반 (INFO)</option>
                <option value="WARN">경고 (WARN)</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium mb-2">
                시작일 (선택)
              </label>
              <input
                type="date"
                value={
                  form.startsAt
                    ? new Date(form.startsAt).toISOString().split('T')[0]
                    : ''
                }
                onChange={(e) => handleDateChange(e, 'startsAt')}
                className="w-full p-2 border rounded-lg"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">
              종료일 (선택)
            </label>
            <input
              type="date"
              value={
                form.endsAt
                  ? new Date(form.endsAt).toISOString().split('T')[0]
                  : ''
              }
              onChange={(e) => handleDateChange(e, 'endsAt')}
              className="w-full p-2 border rounded-lg"
            />
          </div>

          <div className="flex gap-2 justify-end pt-4">
            <Button
              variant="outline"
              onClick={() => router.back()}
              type="button"
            >
              취소
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? '저장 중...' : '저장'}
            </Button>
          </div>
        </form>
      </Card>
    </AdminSection>
  );
}
