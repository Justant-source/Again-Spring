'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { CommunityComment } from '@/components/community/c3/CommunityComment';
import { CommentBar } from '@/components/community/c3/CommentBar';
import { ReportModal } from '@/components/community/ReportModal';

interface PageProps {
  params: { id: string };
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  if (diffDays === 0) {
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours === 0) {
      const diffMins = Math.floor(diffMs / (1000 * 60));
      return diffMins > 0 ? `${diffMins}분 전` : '방금';
    }
    return `${diffHours}시간 전`;
  }
  if (diffDays === 1) return '어제';
  if (diffDays < 7) return `${diffDays}일 전`;
  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
}

const PAGE_SIZE = 10;

export default function PostCommentsPage({ params }: PageProps) {
  const router = useRouter();
  const [comments, setComments] = useState<Comment[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [commentText, setCommentText] = useState('');
  const [replyToNick, setReplyToNick] = useState<string | undefined>(undefined);
  const [parentCommentId, setParentCommentId] = useState<number | null>(null);
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
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const data = await commentApi.list(params.id, page, PAGE_SIZE);
      if (data.length < PAGE_SIZE) setHasMore(false);
      setComments(prev => [...prev, ...data]);
      setPage(p => p + 1);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingMore(false);
    }
  }, [params.id, page, loadingMore, hasMore]);

  // IntersectionObserver — 하단 도달 시 추가 로드
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

  const handleCommentSubmit = async () => {
    if (!commentText.trim()) return;
    try {
      await commentApi.add(params.id, commentText.trim(), parentCommentId || undefined);
      setCommentText('');
      setParentCommentId(null);
      setReplyToNick(undefined);
      // 첫 페이지 새로고침
      const fresh = await commentApi.list(params.id, 0, PAGE_SIZE);
      setComments(fresh);
      setHasMore(fresh.length === PAGE_SIZE);
      setPage(1);
    } catch (e) {
      console.error(e);
    }
  };

  const handleLike = async (commentId: number) => {
    try {
      await commentApi.toggleLike(params.id, commentId);
      // 해당 댓글만 likeCount 업데이트 (전체 재로드 대신)
      setComments(prev => prev.map(c => {
        if (c.id === commentId) return { ...c, likeCount: c.likeCount + (c.isLiked ? -1 : 1), isLiked: !c.isLiked };
        const updatedReplies = c.replies?.map(r =>
          r.id === commentId ? { ...r, likeCount: r.likeCount + (r.isLiked ? -1 : 1), isLiked: !r.isLiked } : r
        );
        return updatedReplies ? { ...c, replies: updatedReplies } : c;
      }));
    } catch (e) {
      console.error(e);
    }
  };

  const renderComment = (comment: any, isReply = false) => (
    <div key={comment.id}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <CommunityComment
          nick={comment.authorId}
          isAuthor={comment.isAuthor ?? false}
          isPartner={comment.isPartner ?? false}
          time={formatDate(comment.createdAt)}
          text={comment.body}
          likeCount={comment.likeCount}
          isReply={isReply}
        />
        <button
          onClick={() => { setReportTarget({ commentId: comment.id, authorId: comment.authorId }); setReportOpen(true); }}
          style={{ background: 'none', border: 'none', color: 'var(--L-sub)', cursor: 'pointer', fontSize: 11, padding: '4px 8px', marginTop: isReply ? 20 : 0 }}
        >신고</button>
      </div>
      <div style={{ display: 'flex', gap: 12, marginBottom: 4 }}>
        <button
          onClick={() => handleLike(comment.id)}
          style={{ background: 'none', border: 'none', color: 'var(--L-sub)', cursor: 'pointer', fontSize: 11, padding: 0 }}
        >공감 {comment.likeCount}</button>
        {!isReply && (
          <button
            onClick={() => { setParentCommentId(comment.id); setReplyToNick(comment.authorId); }}
            style={{ background: 'none', border: 'none', color: 'var(--L-sub)', cursor: 'pointer', fontSize: 11, padding: 0 }}
          >답글</button>
        )}
      </div>
      {comment.replies?.length > 0 && (
        <div>{comment.replies.map((r: Comment) => renderComment(r, true))}</div>
      )}
    </div>
  );

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 120 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '16px', borderBottom: '1px solid var(--L-border)', background: 'var(--L-bg)' }}>
        <button onClick={() => router.back()} style={{ background: 'none', border: 'none', fontSize: 18, cursor: 'pointer', color: 'var(--L-ink)' }}>‹</button>
        <h1 style={{ fontSize: 14, fontWeight: 500, color: 'var(--L-ink)', margin: 0 }}>댓글 {comments.length}</h1>
      </div>

      <div style={{ padding: '16px', color: 'var(--L-ink)' }} className="tone-L">
        {initialLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--L-sub)', fontSize: 12 }}>불러오는 중...</div>
        ) : comments.length > 0 ? (
          comments.map(c => renderComment(c, false))
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--L-sub)', fontSize: 12 }}>아직 댓글이 없습니다</div>
        )}
        {/* 무한스크롤 트리거 */}
        <div ref={bottomRef} style={{ height: 20 }} />
        {loadingMore && <div style={{ textAlign: 'center', padding: '12px', color: 'var(--L-sub)', fontSize: 12 }}>불러오는 중...</div>}
        {!hasMore && comments.length > 0 && <div style={{ textAlign: 'center', padding: '8px', color: 'var(--L-sub)', fontSize: 11 }}>모든 댓글을 불러왔어요</div>}
      </div>

      <CommentBar value={commentText} onChange={setCommentText} onSubmit={handleCommentSubmit} replyTo={replyToNick} />
      <ReportModal isOpen={reportOpen} onClose={() => { setReportOpen(false); setReportTarget(null); }} postId={params.id} commentId={reportTarget?.commentId} authorId={reportTarget?.authorId} />
    </div>
  );
}
