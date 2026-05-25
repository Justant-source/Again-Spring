'use client';

import type { PlatformStat } from '@/lib/api/marketing/dashboardApi';

interface Props {
  stats: PlatformStat[];
}

const PLATFORM_LABELS: Record<string, string> = {
  X: 'X',
  INSTAGRAM: 'Instagram',
  NAVER_BLOG: '네이버블로그',
  THREADS: 'Threads',
  FACEBOOK: 'Facebook',
};

export function PlatformPerformanceTable({ stats }: Props) {
  if (!stats || stats.length === 0) {
    return (
      <p style={{ fontSize: 13, color: '#aaa', padding: '8px 0' }}>플랫폼 성과 데이터가 없습니다.</p>
    );
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr style={{ borderBottom: '1px solid #e7e3d8' }}>
            {['플랫폼', '발행', '노출', '좋아요', '댓글'].map((h) => (
              <th
                key={h}
                style={{
                  padding: '8px 12px',
                  textAlign: 'left',
                  fontWeight: 600,
                  color: '#666',
                  fontSize: 11,
                }}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {stats.map((s) => (
            <tr key={s.platform} style={{ borderBottom: '1px solid #f0ece4' }}>
              <td style={{ padding: '10px 12px', fontWeight: 500, color: '#1A1A2E' }}>
                {PLATFORM_LABELS[s.platform.toUpperCase()] ?? s.platform}
              </td>
              <td style={{ padding: '10px 12px', color: '#555' }}>{s.publishedCount}</td>
              <td style={{ padding: '10px 12px', color: '#555' }}>{s.impressions.toLocaleString()}</td>
              <td style={{ padding: '10px 12px', color: '#555' }}>{s.likes.toLocaleString()}</td>
              <td style={{ padding: '10px 12px', color: '#555' }}>{s.comments.toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
