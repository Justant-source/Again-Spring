'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { broadcastNotification } from '@/lib/api/admin/notifications';

export default function NotificationsPage() {
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({
    title: '',
    subtitle: '',
    target: 'ALL' as 'ALL' | 'MEMBERS' | 'CUSTOM',
    userIds: '',
  });
  const [preview, setPreview] = useState<{
    title: string;
    subtitle: string;
    target: string;
  } | null>(null);

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

  const handleShowPreview = () => {
    if (!form.title || !form.subtitle) {
      alert('제목과 부제목을 입력하세요.');
      return;
    }

    let targetLabel = '';
    if (form.target === 'ALL') {
      targetLabel = '전체 회원';
    } else if (form.target === 'MEMBERS') {
      targetLabel = '일반 회원 (게스트 제외)';
    } else if (form.target === 'CUSTOM') {
      const userCount = form.userIds
        .split(',')
        .map((id) => id.trim())
        .filter((id) => id).length;
      targetLabel = `특정 사용자 (${userCount}명)`;
    }

    setPreview({
      title: form.title,
      subtitle: form.subtitle,
      target: targetLabel,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!form.title || !form.subtitle) {
      alert('제목과 부제목을 입력하세요.');
      return;
    }

    if (form.target === 'CUSTOM') {
      if (!form.userIds.trim()) {
        alert('특정 사용자 선택 시 사용자 ID를 입력하세요.');
        return;
      }
    }

    if (!confirm('알림을 발송하시겠습니까?')) {
      return;
    }

    setLoading(true);
    try {
      const userIds =
        form.target === 'CUSTOM'
          ? form.userIds
              .split(',')
              .map((id) => id.trim())
              .filter((id) => id)
          : undefined;

      await broadcastNotification({
        title: form.title,
        subtitle: form.subtitle,
        target: form.target,
        userIds,
      });

      alert('알림이 발송되었습니다.');
      setForm({
        title: '',
        subtitle: '',
        target: 'ALL',
        userIds: '',
      });
      setPreview(null);
    } catch (error) {
      console.error('Failed to broadcast notification:', error);
      alert('알림 발송에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <AdminPageHeader title="알림 발송" />
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <Card className="p-6">
            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-sm font-medium mb-2">
                  제목 *
                </label>
                <Input
                  name="title"
                  value={form.title}
                  onChange={handleChange}
                  placeholder="알림 제목을 입력하세요"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">
                  부제목 *
                </label>
                <textarea
                  name="subtitle"
                  value={form.subtitle}
                  onChange={handleChange}
                  placeholder="알림 부제목 (미리보기에 표시됩니다)"
                  className="w-full p-3 border rounded-lg min-h-24"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">
                  발송 대상 *
                </label>
                <select
                  name="target"
                  value={form.target}
                  onChange={handleChange}
                  className="w-full p-2 border rounded-lg"
                >
                  <option value="ALL">전체 회원</option>
                  <option value="MEMBERS">일반 회원 (게스트 제외)</option>
                  <option value="CUSTOM">특정 사용자</option>
                </select>
              </div>

              {form.target === 'CUSTOM' && (
                <div>
                  <label className="block text-sm font-medium mb-2">
                    사용자 ID 목록 (쉼표로 구분) *
                  </label>
                  <textarea
                    name="userIds"
                    value={form.userIds}
                    onChange={handleChange}
                    placeholder="user1, user2, user3"
                    className="w-full p-3 border rounded-lg min-h-24 font-mono text-sm"
                  />
                  <p className="text-xs text-gray-500 mt-2">
                    사용자 ID를 쉼표로 구분하여 입력하세요.
                  </p>
                </div>
              )}

              <div className="flex gap-2 justify-end pt-4">
                <Button
                  variant="outline"
                  type="button"
                  onClick={handleShowPreview}
                >
                  미리보기
                </Button>
                <Button type="submit" disabled={loading}>
                  {loading ? '발송 중...' : '발송'}
                </Button>
              </div>
            </form>
          </Card>
        </div>

        <div>
          {preview && (
            <Card className="p-6 sticky top-20">
              <h3 className="text-sm font-medium mb-4">미리보기</h3>
              <div className="space-y-4">
                <div className="bg-gray-50 p-4 rounded-lg border border-gray-200">
                  <div className="text-sm font-medium text-gray-900 mb-1">
                    {preview.title}
                  </div>
                  <div className="text-xs text-gray-600 line-clamp-2">
                    {preview.subtitle}
                  </div>
                </div>

                <div>
                  <p className="text-xs text-gray-600 font-medium mb-2">
                    발송 대상
                  </p>
                  <p className="text-sm text-gray-900">{preview.target}</p>
                </div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
