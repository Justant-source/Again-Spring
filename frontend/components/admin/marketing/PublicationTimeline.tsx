'use client';

import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { TimelineEventDto } from '@/lib/api/admin/marketing';

interface PublicationTimelineProps {
  events: TimelineEventDto[];
  loading: boolean;
}

const PLATFORM_LABELS: Record<string, string> = {
  naver_blog: '네이버 블로그',
  x_thread: 'X 4단 스레드',
  instagram_feed: '인스타그램 피드',
  instagram_reels: '인스타그램 릴스',
  youtube_shorts: 'YouTube Shorts',
  naver_clip: '네이버 클립',
  threads: 'Threads',
};

const STATE_COLORS: Record<string, string> = {
  PUBLISHED: 'bg-green-200 text-green-800',
  NEEDS_AUTH: 'bg-red-200 text-red-800',
  FAILED: 'bg-red-200 text-red-800',
  MANUAL: 'bg-gray-200 text-gray-800',
  PENDING: 'bg-blue-100 text-blue-800',
  PUBLISHING: 'bg-orange-100 text-orange-800',
};

export function PublicationTimeline({
  events,
  loading,
}: PublicationTimelineProps) {
  const getPlatformLabel = (platform: string): string => {
    return PLATFORM_LABELS[platform] || platform;
  };

  const getStateLabel = (state: string): string => {
    if (state === 'NEEDS_AUTH') return '인증 필요';
    return state;
  };

  if (loading) {
    return (
      <div className="text-center text-gray-400 text-sm py-6">로드 중...</div>
    );
  }

  if (events.length === 0) {
    return (
      <Card className="p-6 text-center text-gray-400">
        아직 게시 이력이 없어요.
      </Card>
    );
  }

  return (
    <div
      className="space-y-2 max-h-96 overflow-y-auto"
      data-testid="marketing-timeline"
    >
      {events.map((event, idx) => (
        <div
          key={idx}
          className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 hover:bg-gray-50"
        >
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-800">
              {getPlatformLabel(event.platform)}
            </p>
            <p className="text-xs text-gray-500 font-mono mt-0.5">{event.postId}</p>
          </div>

          <div className="flex items-center gap-2">
            <Badge
              className={STATE_COLORS[event.state] || 'bg-gray-200 text-gray-800'}
            >
              {getStateLabel(event.state)}
            </Badge>

            {event.url && event.url.startsWith('http') && (
              <a
                href={event.url}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-blue-600 hover:underline whitespace-nowrap"
              >
                링크 →
              </a>
            )}

            {event.publishedAt && (
              <span className="text-xs text-gray-400 whitespace-nowrap">
                {new Date(event.publishedAt).toLocaleDateString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
