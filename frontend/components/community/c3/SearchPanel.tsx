'use client';

import { useState, useEffect, useRef } from 'react';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { FeedCard } from './FeedCard';
import { timeAgo } from '@/lib/utils/timeAgo';

const STORAGE_KEY = 'as_recent_searches';
const MAX_RECENT = 10;

const PLAZAS = [
  { id: 'COUPLE',  label: '연인', initial: '연', color: '#E0879A' },
  { id: 'MARRIED', label: '부부', initial: '부', color: '#D67E5E' },
  { id: 'FRIEND',  label: '친구', initial: '친', color: '#D6A646' },
  { id: 'FAMILY',  label: '가족', initial: '가', color: '#B39A56' },
  { id: 'WORK',    label: '직장', initial: '직', color: '#6E90B8' },
  { id: 'OTHER',   label: '기타', initial: '기', color: '#7BA68E' },
];

function loadRecent(): string[] {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]'); }
  catch { return []; }
}
function saveRecent(items: string[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_RECENT)));
}

const MagCircle = () => (
  <span style={{ width: 26, height: 26, borderRadius: '50%', background: '#E4DCCF', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--L-sub)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" />
    </svg>
  </span>
);

interface Props {
  currentCategory: string;
  onCategorySelect: (id: string) => void;
  onClose: () => void;
}

export function SearchPanel({ currentCategory, onCategorySelect, onClose }: Props) {
  const [view, setView] = useState<'entry' | 'results'>('entry');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PostSummary[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [searching, setSearching] = useState(false);
  const [recents, setRecents] = useState<string[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const inputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setRecents(loadRecent());
    postApi.counts().then(setCounts).catch(() => {});
    inputRef.current?.focus();
  }, []);

  const currentPlaza = PLAZAS.find(p => p.id === currentCategory) ?? null;
  const otherPlazas = currentCategory ? PLAZAS.filter(p => p.id !== currentCategory) : PLAZAS;

  const submitSearch = async (q: string) => {
    if (!q.trim()) return;
    const updated = [q, ...recents.filter(r => r !== q)];
    saveRecent(updated);
    setRecents(updated);
    setQuery(q);
    setView('results');
    setSearching(true);
    scrollRef.current?.scrollTo({ top: 0 });
    try {
      const res = await postApi.search(q, { category: currentCategory || undefined, size: 50 });
      setResults(res.content);
      setTotalCount(res.totalElements);
    } catch {
      setResults([]);
      setTotalCount(0);
    } finally {
      setSearching(false);
    }
  };

  const handleBack = () => {
    if (view === 'results') { setView('entry'); setQuery(''); }
    else onClose();
  };

  const clearQuery = () => {
    setQuery('');
    if (view === 'results') setView('entry');
    inputRef.current?.focus();
  };

  const removeRecent = (term: string) => {
    const updated = recents.filter(r => r !== term);
    saveRecent(updated);
    setRecents(updated);
  };

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--L-bg)', zIndex: 1000, display: 'flex', flexDirection: 'column' }}>

      {/* 상단바: ‹ + 입력칸 + ✕ */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '15px 18px 14px', borderBottom: '1px solid var(--L-border)', flexShrink: 0 }}>
        <span onClick={handleBack} style={{ fontSize: 22, color: 'var(--L-ink)', lineHeight: 1, cursor: 'pointer' }}>‹</span>
        <input
          ref={inputRef}
          value={query}
          onChange={e => setQuery(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') submitSearch(query); }}
          placeholder={currentPlaza ? `${currentPlaza.label} 광장에서 검색` : '전체에서 사연 검색'}
          style={{ flex: 1, border: 'none', outline: 'none', background: 'transparent', fontSize: 15.5, color: 'var(--L-ink)', fontFamily: 'var(--font-sans)' }}
        />
        {query && <span onClick={clearQuery} style={{ fontSize: 16, color: 'var(--L-sub)', cursor: 'pointer', lineHeight: 1 }}>✕</span>}
      </div>

      {/* 스크롤 영역 */}
      <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', position: 'relative' }}>
        {view === 'entry' ? (
          <div style={{ padding: '20px 20px 80px' }}>
            {/* 광장 범위 배지 */}
            {currentPlaza && (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 7, padding: '6px 12px', borderRadius: 999, background: 'var(--L-ink)', color: 'var(--L-bg)', fontSize: 12.5, marginBottom: 18 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: currentPlaza.color }} />
                {currentPlaza.label} 광장에서 검색 중
              </div>
            )}

            {/* 최근 검색 */}
            {recents.length > 0 && (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 14.5, fontWeight: 600, color: 'var(--L-ink)' }}>최근 검색</span>
                  <span onClick={() => { saveRecent([]); setRecents([]); }} style={{ fontSize: 12.5, color: 'var(--L-sub)', cursor: 'pointer' }}>전체 삭제</span>
                </div>
                <div style={{ marginTop: 6 }}>
                  {recents.map(term => (
                    <div key={term} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '12px 0' }}>
                      <MagCircle />
                      <span onClick={() => submitSearch(term)} style={{ flex: 1, fontSize: 14.5, color: 'var(--L-ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'pointer' }}>{term}</span>
                      <span onClick={() => removeRecent(term)} style={{ fontSize: 15, color: 'var(--L-sub)', lineHeight: 1, cursor: 'pointer' }}>✕</span>
                    </div>
                  ))}
                </div>
              </>
            )}

            {/* 다른 광장 */}
            <div style={{ fontSize: 14.5, fontWeight: 600, color: 'var(--L-ink)', marginTop: recents.length > 0 ? 30 : 0, marginBottom: 8 }}>다른 광장</div>
            <div>
              {otherPlazas.map(p => (
                <div key={p.id} onClick={() => { onCategorySelect(p.id); onClose(); }} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', cursor: 'pointer' }}>
                  <span style={{ width: 48, height: 48, borderRadius: '50%', background: p.color, color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 19, fontWeight: 600, flexShrink: 0, fontFamily: 'var(--font-serif)' }}>{p.initial}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--L-ink)' }}>{p.label} 광장</div>
                    <div style={{ fontSize: 12.5, color: 'var(--L-sub)', marginTop: 3 }}>지금까지 {(counts[p.id] ?? 0).toLocaleString()}개의 사연</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div style={{ padding: '16px 20px 90px' }}>
            {searching ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)', fontSize: 13 }}>검색 중...</div>
            ) : results.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)', fontSize: 13 }}>검색 결과가 없습니다</div>
            ) : (
              <>
                <div style={{ fontSize: 12.5, color: 'var(--L-sub)', marginBottom: 12 }}>
                  사연 <span style={{ color: 'var(--L-ink)', fontWeight: 600 }}>{totalCount.toLocaleString()}건</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {results.map(post => (
                    <FeedCard
                      key={post.id}
                      href={`/community/${post.id}`}
                      cat={PLAZAS.find(p => p.id === post.category)?.label ?? '기타'}
                      id={post.authorNickname ?? '익명'}
                      time={timeAgo(post.createdAt)}
                      title={post.title}
                      body={post.bodyPublished}
                      g={post.authorPct ?? 50}
                      votes={post.voteCount ?? 0}
                      c={post.commentCount ?? 0}
                      views={post.viewCount ?? 0}
                      paired={post.paired}
                      voted={!!post.myVoteSide}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        )}
      </div>

      {/* 위로 버튼 */}
      <div
        onClick={() => scrollRef.current?.scrollTo({ top: 0, behavior: 'smooth' })}
        style={{ position: 'absolute', bottom: 20, right: 16, width: 46, height: 46, borderRadius: '50%', background: 'var(--L-bg)', border: '1px solid var(--L-border)', boxShadow: '0 3px 12px rgba(43,43,43,0.14)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--L-ink)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 19V6M6 11l6-6 6 6" /></svg>
      </div>
    </div>
  );
}
