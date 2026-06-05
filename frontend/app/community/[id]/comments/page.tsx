'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { CommunityComment } from '@/components/community/c3/CommunityComment';
import { CommentBar } from '@/components/community/c3/CommentBar';
import { CommentComposeSheet } from '@/components/community/c3/CommentComposeSheet';
import { ReportModal } from '@/components/community/ReportModal';
import { timeAgo } from '@/lib/utils/timeAgo';
import { useGuestInit } from '@/lib/hooks/useGuestInit';

interface PageProps {
  params: { id: string };
}

const PAGE_SIZE = 10;

export default function PostCommentsPage({ params }: PageProps) {
  useGuestInit();
  const router = useRouter();
  const searchParams = useSearchParams();
  const highlightId = searchParams.get('highlight') ? Number(searchParams.get('highlight')) : null;
  const scrolledRef = useRef(false);
  const [highlightedId, setHighlightedId] = useState<number | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);

  const [commentText, setCommentText] = useState('');
  const [replyToNick, setReplyToNick] = useState<string | undefined>(undefined);
  const [parentCommentId, setParentCommentId] = useState<number | null>(null);
  const [composeOpen, setComposeOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [likeToast, setLikeToast] = useState(false);

  const [reportOpen, setReportOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ commentId?: number; authorId?: string } | null>(null);

  const bottomRef = useRef<HTMLDivElement>(null);

  // 첫 페이지 로드
  useEffect(() => {
    const load = async () => {
      try {
        const data = await commentApi.list(params.id, 0, PAGE_SIZE);
        setComments(data);
        setHasMore(data.length === PAGE_SIZE);
        setPage(1);
      } catch (e) {
        console.error(e);
      } finally {
        setInitialLoading(false);
      }
    };
    load();
  }, [params.id]);

  // 추가 페이지 로드
  // ⚠️ initialLoading 가드 필수 — 없으면 마운트 직후 빈 목록 때문에 observer가
  //    즉시 발화해 첫 페이지(page=0)를 useEffect와 중복 로드 → 댓글 중복 표시
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore || initialLoading) return;
    setLoadingMore(true);
    try {
      const data = await commentApi.list(params.id, page, PAGE_SIZE);
      if (data.length < PAGE_SIZE) setHasMore(false);
      setComments((prev) => [...prev, ...data]);
      setPage((p) => p + 1);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingMore(false);
    }
  }, [params.id, page, loadingMore, hasMore, initialLoading]);

  // 알림 클릭 시 highlight 댓글로 스크롤 (loadMore 선언 이후)
  useEffect(() => {
    if (!highlightId || scrolledRef.current || initialLoading) return;
    const el = document.getElementById(`comment-${highlightId}`);
    if (el) {
      scrolledRef.current = true;
      setHighlightedId(highlightId);
      setTimeout(() => {
        const top = el.getBoundingClientRect().top + window.scrollY - 60;
        window.scrollTo({ top, behavior: 'smooth' });
      }, 50);
      setTimeout(() => setHighlightedId(null), 2000);
    } else if (!loadingMore && hasMore) {
      loadMore();
    }
  }, [highlightId, comments, initialLoading, loadingMore, hasMore, loadMore]);

  // 무한스크롤 — 하단 도달 시 추가 로드
  useEffect(() => {
    const el = bottomRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) loadMore(); },
      { threshold: 0.1 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [loadMore]);

  const openCompose = (parentId?: number, replyNick?: string) => {
    setParentCommentId(parentId ?? null);
    setReplyToNick(replyNick);
    setComposeOpen(true);
  };

  const closeCompose = () => {
    setComposeOpen(false);
    setParentCommentId(null);
    setReplyToNick(undefined);
  };

  const handleSubmit = async () => {
    if (!commentText.trim()) return;
    setSubmitError(null);
    try {
      await commentApi.add(params.id, commentText.trim(), parentCommentId || undefined);
      setCommentText('');
      closeCompose();
      const fresh = await commentApi.list(params.id, 0, PAGE_SIZE);
      setComments(fresh);
      setHasMore(fresh.length === PAGE_SIZE);
      setPage(1);
    } catch {
      setSubmitError('댓글 등록에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleLike = async (commentId: number) => {
    try {
      const result = await commentApi.toggleLike(params.id, commentId);
      setComments((prev) =>
        prev.map((c) => {
          if (c.id === commentId) return { ...c, likeCount: result.count, isLiked: result.liked };
          const updatedReplies = c.replies?.map((r) =>
            r.id === commentId ? { ...r, likeCount: result.count, isLiked: result.liked } : r
          );
          return c.replies ? { ...c, replies: updatedReplies } : c;
        })
      );
    } catch (e: any) {
      if (e?.response?.status === 403 || e?.response?.status === 401) {
        setLikeToast(true);
        setTimeout(() => setLikeToast(false), 2500);
      }
    }
  };

  const totalCount = comments.reduce((sum, c) => sum + 1 + (c.replies?.length ?? 0), 0);

  const renderComment = (comment: Comment, isReply = false) => (
    <div
      key={comment.id}
      id={`comment-${comment.id}`}
      style={comment.id === highlightedId ? {
        borderRadius: 8,
        outline: '2px solid var(--L-point)',
        transition: 'outline 0.5s ease',
      } : undefined}
    >
      <CommunityComment
        nick={comment.authorNickname || comment.authorId}
        isAuthor={comment.isAuthor ?? false}
        isPartner={comment.isPartner ?? false}
        time={timeAgo(comment.createdAt)}
        text={comment.body}
        likeCount={comment.likeCount}
        isLiked={comment.isLiked}
        isReply={isReply}
        onLike={() => handleLike(comment.id)}
        onReply={
          isReply
            ? undefined
            : () => openCompose(comment.id, comment.authorNickname || comment.authorId)
        }
        onReport={() => {
          setReportTarget({ commentId: comment.id, authorId: comment.authorId });
          setReportOpen(true);
        }}
      />
      {!isReply && comment.replies?.map((r) => renderComment(r, true))}
    </div>
  );

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 64 }} className="tone-L">
      {/* 헤더 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 18px 10px',
          borderBottom: '1px solid var(--L-border)',
          background: 'var(--L-bg)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <button
            onClick={() => router.back()}
            style={{ background: 'none', border: 'none', fontSize: 20, cursor: 'pointer', color: 'var(--L-ink)', padding: 0 }}
          >
            ‹
          </button>
          <span style={{ fontSize: 16, fontWeight: 500, color: 'var(--L-ink)' }}>
            댓글 {totalCount > 0 ? totalCount : ''}
          </span>
        </div>
      </div>

      {/* 댓글 목록 */}
      <div style={{ padding: '0 20px 80px' }}>
        {initialLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)', fontSize: 12 }}>
            불러오는 중...
          </div>
        ) : comments.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)', fontSize: 12 }}>
            아직 댓글이 없습니다
          </div>
        ) : (
          comments.map((c) => renderComment(c, false))
        )}

        {/* 무한스크롤 트리거 */}
        <div ref={bottomRef} style={{ height: 20 }} />
        {loadingMore && (
          <div style={{ textAlign: 'center', padding: '12px 0', color: 'var(--L-sub)', fontSize: 12 }}>
            불러오는 중...
          </div>
        )}
        {!hasMore && comments.length > 0 && (
          <div style={{ textAlign: 'center', padding: '8px 0', color: 'var(--L-sub)', fontSize: 11 }}>
            모든 댓글을 불러왔어요
          </div>
        )}
      </div>

      {/* 하단 댓글 입력바 */}
      <CommentBar
        replyTo={replyToNick}
        onClick={() => openCompose(undefined, undefined)}
      />

      {/* 댓글 작성 바텀시트 (9-1) */}
      {composeOpen && (
        <CommentComposeSheet
          value={commentText}
          onChange={(v) => { setCommentText(v); setSubmitError(null); }}
          onSubmit={handleSubmit}
          onClose={closeCompose}
          replyTo={replyToNick}
          error={submitError}
        />
      )}

      {/* 좋아요 미인증 토스트 */}
      {likeToast && (
        <div style={{
          position: 'fixed', bottom: 80, left: '50%', transform: 'translateX(-50%)',
          background: 'var(--L-ink)', color: 'var(--L-bg)', fontSize: 13,
          padding: '10px 20px', borderRadius: 20, zIndex: 300, whiteSpace: 'nowrap',
        }}>
          좋아요는 로그인 또는 게스트 시작 후 가능합니다.
        </div>
      )}

      {/* 신고 모달 */}
      <ReportModal
        isOpen={reportOpen}
        onClose={() => { setReportOpen(false); setReportTarget(null); }}
        postId={params.id}
        commentId={reportTarget?.commentId}
        authorId={reportTarget?.authorId}
      />
    </div>
  );
}
