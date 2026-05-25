'use client';

import { useEffect, useState } from 'react';
import { getCalendarItems, type CalendarItem } from '@/lib/api/marketing/calendarApi';
import Link from 'next/link';

const PLATFORM_COLORS: Record<string, string> = {
  X: '#222',
  INSTAGRAM: '#e1306c',
  NAVER_BLOG: '#00c73c',
  THREADS: '#555',
  FACEBOOK: '#1877f2',
};

export default function MarketingCalendarPage() {
  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth()); // 0-indexed
  const [items, setItems] = useState<CalendarItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);

  function padDate(n: number) {
    return String(n).padStart(2, '0');
  }
  const fromStr = `${year}-${padDate(month + 1)}-01`;
  const toStr = `${year}-${padDate(month + 1)}-${padDate(lastDay.getDate())}`;

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');
      try {
        const data = await getCalendarItems(fromStr, toStr);
        setItems(data);
      } catch {
        setError('캘린더를 불러올 수 없습니다.');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [year, month]);

  function prevMonth() {
    if (month === 0) { setYear(y => y - 1); setMonth(11); }
    else setMonth(m => m - 1);
  }
  function nextMonth() {
    if (month === 11) { setYear(y => y + 1); setMonth(0); }
    else setMonth(m => m + 1);
  }

  const MONTH_NAMES = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];
  const DAY_NAMES = ['일','월','화','수','목','금','토'];

  // Build calendar grid (6 weeks max)
  const startDow = firstDay.getDay();
  const daysInMonth = lastDay.getDate();
  const cells: (number | null)[] = [];
  for (let i = 0; i < startDow; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  while (cells.length % 7 !== 0) cells.push(null);

  function getItemsForDay(day: number): CalendarItem[] {
    return items.filter((item) => {
      const dt = item.scheduledAt || item.publishedAt;
      if (!dt) return false;
      const d = new Date(dt);
      return d.getFullYear() === year && d.getMonth() === month && d.getDate() === day;
    });
  }

  return (
    <div>
      <div
        style={{
          marginBottom: 20,
          padding: '20px',
          background: 'white',
          borderRadius: 12,
          border: '1px solid #e7e3d8',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <h1 style={{ fontSize: 16, fontWeight: 600, color: '#1A1A2E', margin: 0 }}>
            발행 캘린더
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              onClick={prevMonth}
              style={{ padding: '6px 12px', border: '1px solid #e7e3d8', borderRadius: 6, background: 'white', cursor: 'pointer', fontSize: 14 }}
            >
              &lt;
            </button>
            <span style={{ fontSize: 15, fontWeight: 600, color: '#1A1A2E', minWidth: 80, textAlign: 'center' }}>
              {year}년 {MONTH_NAMES[month]}
            </span>
            <button
              onClick={nextMonth}
              style={{ padding: '6px 12px', border: '1px solid #e7e3d8', borderRadius: 6, background: 'white', cursor: 'pointer', fontSize: 14 }}
            >
              &gt;
            </button>
          </div>
        </div>
      </div>

      {error && (
        <div style={{ padding: 16, background: '#ffe6e6', border: '1px solid #e55', borderRadius: 8, marginBottom: 20, color: '#e55', fontSize: 13 }}>
          {error}
        </div>
      )}

      <div style={{ background: 'white', borderRadius: 12, border: '1px solid #e7e3d8', overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', borderBottom: '1px solid #e7e3d8' }}>
          {DAY_NAMES.map((d) => (
            <div
              key={d}
              style={{
                padding: '10px 0',
                textAlign: 'center',
                fontSize: 12,
                fontWeight: 600,
                color: '#888',
              }}
            >
              {d}
            </div>
          ))}
        </div>

        {loading ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#aaa', fontSize: 13 }}>불러오는 중...</div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)' }}>
            {cells.map((day, idx) => {
              const dayItems = day ? getItemsForDay(day) : [];
              const isToday = day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
              return (
                <div
                  key={idx}
                  style={{
                    minHeight: 80,
                    padding: '6px 8px',
                    borderRight: idx % 7 !== 6 ? '1px solid #f0ece4' : 'none',
                    borderBottom: '1px solid #f0ece4',
                    background: day ? 'white' : '#fafafa',
                  }}
                >
                  {day && (
                    <>
                      <div
                        style={{
                          fontSize: 12,
                          fontWeight: isToday ? 700 : 400,
                          color: isToday ? 'white' : '#444',
                          background: isToday ? '#1A1A2E' : 'transparent',
                          width: 22,
                          height: 22,
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          marginBottom: 4,
                        }}
                      >
                        {day}
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        {dayItems.slice(0, 3).map((item) => (
                          <Link
                            key={item.id}
                            href={`/admin/marketing/contents/${item.id}`}
                            style={{
                              display: 'block',
                              fontSize: 10,
                              color: 'white',
                              background: PLATFORM_COLORS[item.platform?.toUpperCase() ?? ''] ?? '#888',
                              borderRadius: 3,
                              padding: '1px 4px',
                              textDecoration: 'none',
                              overflow: 'hidden',
                              whiteSpace: 'nowrap',
                              textOverflow: 'ellipsis',
                            }}
                          >
                            {item.platform?.toUpperCase().replace('NAVER_BLOG', 'Blog') ?? ''}
                            {item.scheduledAt && !item.publishedAt ? ' (Scheduled)' : ' (Published)'}
                          </Link>
                        ))}
                        {dayItems.length > 3 && (
                          <span style={{ fontSize: 10, color: '#888' }}>+{dayItems.length - 3}</span>
                        )}
                      </div>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
