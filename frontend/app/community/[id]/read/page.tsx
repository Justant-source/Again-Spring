'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { postApi, PostDetail } from '@/lib/api/community/postApi';
import { ReportModal } from '@/components/community/ReportModal';
import { AUTHOR, PARTNER, AUTHOR_BG, PARTNER_BG } from '@/lib/constants/factionColors';

interface PageProps {
  params: { id: string };
}

export default function C3StoryRead({ params }: PageProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialSide = (searchParams.get('side') as 'g' | 'r') || 'g';

  const [post, setPost] = useState<PostDetail | null>(null);
  const [side, setSide] = useState<'g' | 'r'>(initialSide);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);

  useEffect(() => {
    const loadPost = async () => {
      try {
        setLoading(true);
        const postData = await postApi.get(params.id);
        setPost(postData);
      } catch (err) {
        console.error('Failed to load post:', err);
        setError('사연을 불러올 수 없습니다');
      } finally {
        setLoading(false);
      }
    };
    loadPost();
  }, [params.id]);

  if (loading) {
    return <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--P-sub)' }}>불러오는 중...</div>;
  }

  if (error || !post) {
    return (
      <div style={{ padding: '16px', background: '#FEE', border: '1px solid #F99', borderRadius: 8, fontSize: 13, color: '#C33', textAlign: 'center' }}>
        {error || '사연을 찾을 수 없습니다'}
      </div>
    );
  }

  const hasPair = !!post.partnerBodyPublished;
  const c = side === 'g' ? AUTHOR : PARTNER;
  const bg = side === 'g' ? AUTHOR_BG : PARTNER_BG;
  const label = side === 'g' ? '작성자' : '상대방';
  const body = side === 'g' ? post.bodyPublished : post.partnerBodyPublished;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--L-bg)', padding: '14px 20px 28px' }}>
      {/* 헤더: ‹ 제목 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
        <button
          onClick={() => router.back()}
          style={{ background: 'none', border: 'none', fontSize: 17, color: 'var(--L-sub)', cursor: 'pointer', padding: 0, lineHeight: 1 }}
        >
          ‹
        </button>
        <span style={{ fontSize: 13, color: 'var(--L-sub)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {post.title}
        </span>
      </div>

      {/* 진영 탭 — 양쪽 글이 있을 때만 표시 */}
      {hasPair && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          {(['g', 'r'] as const).map((s) => {
            const sc = s === 'g' ? AUTHOR : PARTNER;
            const sl = s === 'g' ? '작성자의 이야기' : '상대방의 이야기';
            const active = side === s;
            return (
              <button
                key={s}
                onClick={() => setSide(s)}
                style={{
                  flex: 1,
                  padding: '9px 12px',
                  background: 'transparent',
                  border: active ? `2px solid ${sc}` : '1px solid var(--L-border)',
                  borderRadius: 8,
                  fontSize: 12,
                  fontWeight: 600,
                  color: active ? sc : 'var(--L-sub)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  justifyContent: 'center',
                  fontFamily: 'inherit',
                }}
              >
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: sc, flexShrink: 0 }} />
                {sl}
              </button>
            );
          })}
        </div>
      )}

      {/* 진영 라벨 — 단독 표시 시 */}
      {!hasPair && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 14 }}>
          <span style={{ width: 9, height: 9, borderRadius: '50%', background: c }} />
          <span style={{ fontSize: 13, color: c, fontWeight: 500 }}>{label}의 이야기</span>
        </div>
      )}

      {/* 전문 카드 + 우측 하단 ⋯ */}
      <div style={{ position: 'relative', background: bg, borderRadius: 12, padding: '18px 16px 40px' }}>
        <p style={{ margin: 0, fontSize: 15, fontFamily: 'var(--font-serif)', lineHeight: 1.85, color: 'var(--P-ink)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {body}
        </p>

        {/* ⋯ 신고 버튼 */}
        <button
          onClick={() => setMenuOpen((o) => !o)}
          style={{
            position: 'absolute',
            bottom: 10,
            right: 14,
            background: 'none',
            border: 'none',
            padding: '4px 6px',
            cursor: 'pointer',
            fontSize: 15,
            color: 'var(--L-sub)',
            letterSpacing: 1,
            fontFamily: 'inherit',
          }}
        >
          ⋯
        </button>

        {/* 드롭다운 */}
        {menuOpen && (
          <>
            <div style={{ position: 'fixed', inset: 0, zIndex: 49 }} onClick={() => setMenuOpen(false)} />
            <div
              style={{
                position: 'absolute',
                right: 12,
                bottom: 36,
                background: 'var(--L-bg)',
                border: '1px solid var(--L-border)',
                borderRadius: 8,
                boxShadow: '0 4px 12px rgba(0,0,0,0.10)',
                zIndex: 50,
                minWidth: 80,
                overflow: 'hidden',
              }}
            >
              <button
                onClick={() => { setMenuOpen(false); setReportOpen(true); }}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '11px 16px',
                  background: 'none',
                  border: 'none',
                  textAlign: 'left',
                  fontSize: 13,
                  color: 'var(--faction-partner)',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                }}
              >
                신고
              </button>
            </div>
          </>
        )}
      </div>

      <ReportModal
        isOpen={reportOpen}
        postId={params.id}
        onClose={() => setReportOpen(false)}
      />
    </div>
  );
}
