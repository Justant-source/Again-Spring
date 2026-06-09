'use client';

import { useState } from 'react';
import { createMarketingJob, MarketingJob } from '@/lib/api/admin/marketing';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { AlertCircle } from 'lucide-react';

interface Props {
  postId: string | null;
  onClose: () => void;
  onCreated: (job: MarketingJob) => void;
}

const PLATFORM_OPTIONS = [
  { value: 'youtube_shorts', label: 'YouTube Shorts' },
  { value: 'naver_clip', label: '네이버 클립' },
  { value: 'instagram_reels', label: 'Instagram Reels' },
  { value: 'instagram_feed', label: 'Instagram 피드' },
  { value: 'naver_blog', label: '네이버 블로그' },
  { value: 'x', label: 'X (Twitter)' },
  { value: 'threads', label: 'Threads' },
];

export function CreateMarketingJobDialog({ postId, onClose, onCreated }: Props) {
  const [selectedPlatforms, setSelectedPlatforms] = useState<Set<string>>(new Set());
  const [autoPublish, setAutoPublish] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (!postId) return null;

  const handleTogglePlatform = (platform: string) => {
    const newSet = new Set(selectedPlatforms);
    if (newSet.has(platform)) {
      newSet.delete(platform);
    } else {
      newSet.add(platform);
    }
    setSelectedPlatforms(newSet);
  };

  async function handleCreate() {
    if (!postId) {
      setError('사연 ID가 없습니다.');
      return;
    }

    if (selectedPlatforms.size === 0) {
      setError('최소 하나 이상의 플랫폼을 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const job = await createMarketingJob(
        parseInt(postId),
        Array.from(selectedPlatforms),
        autoPublish
      );
      onCreated(job);
      onClose();
    } catch (err: any) {
      setError(
        err?.response?.data?.message ||
          '마케팅 잡 생성에 실패했어요. 잠시 후 다시 시도해주세요.'
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={!!postId} onOpenChange={onClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>마케팅 제작 요청</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
              <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          {/* 플랫폼 선택 */}
          <div>
            <Label className="block text-sm font-medium mb-3">
              플랫폼 선택 (필수)
            </Label>
            <div className="space-y-2">
              {PLATFORM_OPTIONS.map((platform) => (
                <div key={platform.value} className="flex items-center space-x-2">
                  <Checkbox
                    id={platform.value}
                    checked={selectedPlatforms.has(platform.value)}
                    onCheckedChange={() => handleTogglePlatform(platform.value)}
                    disabled={submitting}
                  />
                  <label
                    htmlFor={platform.value}
                    className="text-sm cursor-pointer"
                  >
                    {platform.label}
                  </label>
                </div>
              ))}
            </div>
          </div>

          {/* 자동 게시 */}
          <div className="flex items-center space-x-2 pt-2">
            <Checkbox
              id="autoPublish"
              checked={autoPublish}
              onCheckedChange={(checked) => setAutoPublish(!!checked)}
              disabled={submitting}
            />
            <label htmlFor="autoPublish" className="text-sm cursor-pointer">
              완료 시 자동으로 게시하기
            </label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleCreate} disabled={submitting}>
            {submitting ? '요청 중...' : '마케팅 제작 요청'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
