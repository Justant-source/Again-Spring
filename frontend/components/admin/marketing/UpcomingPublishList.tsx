'use client';

import type { CalendarItem } from '@/lib/api/marketing/dashboardApi';

interface Props {
  items: CalendarItem[];
}

const PLATFORM_LABELS: Record<string, string> = {
  X: 'X',
  INSTAGRAM: 'Instagram',
  NAVER_BLOG: '네이버블로그',
  THREADS: 'Threads',
  FACEBOOK: 'Facebook',
};

export function UpcomingPublishList({ items }: Props) {
  if (!items || items.length === 0) {
    return (
      <p style={{ fontSize: 13, color: '#aaa', padding: '8px 0' }}>
        24시간 내 예약된 발행이 없습니다.
      </p>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {items.map((item) => {
        const dt = item.scheduledAt
          ? new Date(item.scheduledAt).toLocaleString('ko-KR')
          : '-';
        return (
          <div
            key={item.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '10px 14px',
              background: '#f9f8f5',
              borderRadius: 8,
              border: '1px solid #e7e3d8',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  padding: '2px 8px',
                  background: '#1A1A2E',
                  color: 'white',
                  borderRadius: 4,
                }}
              >
                {PLATFORM_LABELS[item.platform?.toUpperCase() ?? ''] ?? item.platform}
              </span>
              <span style={{ fontSize: 13, color: '#444' }}>{item.title ?? `콘텐츠 #${item.id}`}</span>
            </div>
            <span style={{ fontSize: 12, color: '#888' }}>{dt}</span>
          </div>
        );
      })}
    </div>
  );
}
