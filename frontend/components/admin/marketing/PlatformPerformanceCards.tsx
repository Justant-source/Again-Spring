'use client';

import { Card } from '@/components/ui/card';
import { PlatformStatsDto } from '@/lib/api/admin/marketing';

interface PlatformPerformanceCardsProps {
  data: PlatformStatsDto[];
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

export function PlatformPerformanceCards({
  data,
  loading,
}: PlatformPerformanceCardsProps) {
  const getPlatformLabel = (platform: string): string => {
    return PLATFORM_LABELS[platform] || platform;
  };

  return (
    <div
      className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 auto-cols-min"
      data-testid="marketing-platform-performance"
    >
      {loading && (
        <div className="col-span-full text-center text-gray-400 text-sm py-6">로드 중...</div>
      )}
      {!loading && data.length === 0 && (
        <Card className="col-span-full p-6 text-center text-gray-400">
          아직 플랫폼 게시 기록이 없어요.
        </Card>
      )}
      {!loading && data.map((stat) => (
        <Card key={stat.platform} className="p-4">
          <div className="space-y-3">
            <h3 className="font-semibold text-sm">
              {getPlatformLabel(stat.platform)}
            </h3>

            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">30일 게시</span>
                <span className="font-mono font-medium">{stat.published}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">성공률</span>
                <span className="font-mono font-medium">
                  {Math.round(stat.successRate * 100)}%
                </span>
              </div>
            </div>

            {stat.lastPublishedUrl && (
              <div className="pt-2 border-t">
                <a
                  href={stat.lastPublishedUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-blue-600 hover:underline block truncate"
                  title={stat.lastPublishedUrl}
                >
                  최근 게시 →
                </a>
                {stat.lastPublishedAt && (
                  <p className="text-xs text-gray-400 mt-1">
                    {new Date(stat.lastPublishedAt).toLocaleDateString('ko-KR', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </p>
                )}
              </div>
            )}
          </div>
        </Card>
      ))}
    </div>
  );
}
