'use client';

import { useState, useEffect, useRef } from 'react';
import { postApi } from '@/lib/api/community/postApi';

const STORAGE_KEY = 'as_recent_searches';
const MAX_RECENT = 10;

const C3_CATS = [
  { id: 'COUPLE',  label: '연인', char: '연', color: '#C9785A' },
  { id: 'MARRIED', label: '부부', char: '부', color: '#5F8F76' },
  { id: 'FRIEND',  label: '친구', char: '친', color: '#5B8CBB' },
  { id: 'FAMILY',  label: '가족', char: '가', color: '#D4924E' },
  { id: 'WORK',    label: '직장', char: '직', color: '#7A7A9D' },
  { id: 'OTHER',   label: '기타', char: '기', color: '#888888' },
];

function loadRecent(): string[] {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]'); }
  catch { return []; }
}

function saveRecent(items: string[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_RECENT)));
}

interface SearchPanelProps {
  currentCategory: string;
  onSearch: (q: string, category: string) => void;
  onCategorySelect: (categoryId: string) => void;
  onClose: () => void;
}

export function SearchPanel({ currentCategory, onSearch, onCategorySelect, onClose }: SearchPanelProps) {
  const [q, setQ] = useState('');
  const [recents, setRecents] = useState<string[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setRecents(loadRecent());
    postApi.counts().then(setCounts).catch(() => {});
    inputRef.current?.focus();
  }, []);

  const submit = (keyword: string) => {
    if (!keyword.trim()) return;
    const updated = [keyword, ...recents.filter(r => r !== keyword)];
    saveRecent(updated);
    setRecents(updated);
    onSearch(keyword, currentCategory);
    onClose();
  };

  const removeRecent = (item: string) => {
    const updated = recents.filter(r => r !== item);
    saveRecent(updated);
    setRecents(updated);
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'var(--L-bg)',
      zIndex: 1000, display: 'flex', flexDirection: 'column',
    }}>
      {/* 검색 입력 헤더 */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '12px 16px', borderBottom: '1px solid var(--L-border)',
      }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--L-sub)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          ref={inputRef}
          value={q}
          onChange={e => setQ(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') submit(q); }}
          placeholder="검색어를 입력하세요"
          style={{
            flex: 1, border: 'none', outline: 'none', background: 'transparent',
            fontSize: 15, color: 'var(--L-ink)', fontFamily: 'var(--font-sans)',
          }}
        />
        <button
          onClick={onClose}
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--L-sub)', fontSize: 14, fontFamily: 'var(--font-sans)', padding: 0,
          }}
        >
          취소
        </button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 16px' }}>
        {/* 최근 검색 */}
        {recents.length > 0 && (
          <section style={{ marginBottom: 28 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--L-ink)' }}>최근 검색</span>
              <button
                onClick={() => { saveRecent([]); setRecents([]); }}
                style={{
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 12, color: 'var(--L-sub)', fontFamily: 'var(--font-sans)', padding: 0,
                }}
              >
                전체 삭제
              </button>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {recents.map(item => (
                <button
                  key={item}
                  onClick={() => submit(item)}
                  style={{
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                    padding: '6px 12px', borderRadius: 999,
                    border: '1px solid var(--L-border)', background: 'transparent',
                    fontSize: 13, color: 'var(--L-ink)', cursor: 'pointer',
                    fontFamily: 'var(--font-sans)',
                  }}
                >
                  {item}
                  <span
                    role="button"
                    onClick={e => { e.stopPropagation(); removeRecent(item); }}
                    style={{ fontSize: 11, color: 'var(--L-sub)', lineHeight: 1 }}
                    aria-label={`${item} 삭제`}
                  >
                    ✕
                  </span>
                </button>
              ))}
            </div>
          </section>
        )}

        {/* 다른 광장 */}
        <section>
          <div style={{ marginBottom: 14 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--L-ink)' }}>다른 광장</span>
          </div>
          <div style={{ display: 'flex', gap: 16, overflowX: 'auto', scrollbarWidth: 'none', paddingBottom: 4 }}>
            {C3_CATS.map(cat => (
              <button
                key={cat.id}
                onClick={() => { onCategorySelect(cat.id); onClose(); }}
                style={{
                  flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5,
                  background: 'none', border: 'none', cursor: 'pointer', padding: 0,
                }}
              >
                <span style={{
                  width: 52, height: 52, borderRadius: '50%', background: cat.color,
                  color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 18, fontWeight: 600, fontFamily: 'var(--font-sans)',
                }}>
                  {cat.char}
                </span>
                <span style={{ fontSize: 12, color: 'var(--L-ink)', fontFamily: 'var(--font-sans)' }}>{cat.label}</span>
                <span style={{ fontSize: 11, color: 'var(--L-sub)', fontFamily: 'var(--font-sans)' }}>
                  글{counts[cat.id] ?? 0}
                </span>
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
