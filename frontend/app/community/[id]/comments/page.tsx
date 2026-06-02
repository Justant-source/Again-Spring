'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { postApi } from '@/lib/api/community/postApi';
import { CommunityComment } from '@/components/community/c3/CommunityComment';
import { CommentBar } from '@/components/community/c3/CommentBar';
import { ReportModal } from '@/components/community/ReportModal';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';

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

export default function PostCommentsPage({ params }: PageProps) {
  const router = useRouter();
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [parentCommentId, setParentCommentId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [crisisOpen, setCrisisOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ commentId?: number; authorId?: string } | null>(null);

  useEffect(() => {
    const loadComments = async () => {
      try {
        setLoading(false);
        const commentsData = await commentApi.list(params.id);
        setComments(commentsData);
      } catch (err) {
        console.error('Failed to load comments:', err);
      }
    };

    loadComments();
  }, [params.id]);

  const handleCommentSubmit = async (text: string, inputRef: HTMLInputElement | null) => {
    if (!text.trim()) return;

    const keywordCheck = checkKeywords(text);
    if (keywordCheck.level === 1) {
      setCrisisOpen(true);
      return;
    }

    try {
      const newComment = await commentApi.add(params.id, text.trim(), parentCommentId || undefined);
      setComments([...comments, newComment]);
      setCommentText('');
      setParentCommentId(null);
      if (inputRef) {
        inputRef.value = '';
      }
    } catch (err) {
      console.error('Failed to add comment:', err);
    }
  };

  const handleReplyClick = (commentId: number) => {
    setParentCommentId(commentId);
  };

  const handleReportClick = (commentId: number, authorId: string) => {
    setReportTarget({ commentId, authorId });
    setReportOpen(true);
  };

  const renderComment = (comment: Comment, isReply = false) => {
    const isAuthor = false; // TODO: Get from context
    const isPartner = false; // TODO: Get from context

    return (
      <div key={comment.id}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <CommunityComment
            nick={comment.authorId}
            isAuthor={isAuthor}
            isPartner={isPartner}
            time={formatDate(comment.createdAt)}
            text={comment.body}
            likeCount={comment.likeCount}
            isReply={isReply}
          />
          <button
            onClick={() => handleReportClick(comment.id, comment.authorId)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--L-sub)',
              cursor: 'pointer',
              fontSize: 11,
              padding: '4px 8px',
              marginTop: isReply ? 20 : 0,
            }}
          >
            신고
          </button>
        </div>

        {/* 댓글 버튼 */}
        {!isReply && (
          <button
            onClick={() => handleReplyClick(comment.id)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--L-sub)',
              cursor: 'pointer',
              fontSize: 11,
              paddingLeft: 0,
              marginBottom: 12,
              textDecoration: 'underline',
            }}
          >
            답글
          </button>
        )}

        {/* 대댓글 렌더링 */}
        {comment.replies && comment.replies.length > 0 && (
          <div style={{ marginLeft: 0 }}>
            {comment.replies.map((reply) => renderComment(reply, true))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 160 }}>
      {/* 상단 헤더 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '16px',
          borderBottom: '1px solid var(--L-border)',
          background: 'white',
        }}
      >
        <button
          onClick={() => router.back()}
          style={{
            background: 'none',
            border: 'none',
            fontSize: 18,
            cursor: 'pointer',
            color: 'var(--L-ink)',
          }}
        >
          ‹
        </button>
        <h1 style={{ fontSize: 14, fontWeight: 600, color: 'var(--L-ink)', margin: 0 }}>
          댓글 {comments.length}
        </h1>
      </div>

      {/* 댓글 목록 */}
      <div style={{ padding: '16px', color: 'var(--L-ink)' }} className="tone-L">
        {comments.length > 0 ? (
          comments.map((comment) => renderComment(comment, false))
        ) : (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--L-sub)', fontSize: 12 }}>
            아직 댓글이 없습니다
          </div>
        )}
      </div>

      {/* 댓글 입력 바 (고정) */}
      <CommentBar
        onFocus={() => {
          // Handled by form submission
        }}
      />

      {/* 위기 자원 모달 */}
      <CrisisResourceModal
        open={crisisOpen}
        onClose={() => setCrisisOpen(false)}
        severity="advisory"
      />

      {/* 신고 모달 */}
      <ReportModal
        isOpen={reportOpen}
        onClose={() => {
          setReportOpen(false);
          setReportTarget(null);
        }}
        postId={params.id}
        commentId={reportTarget?.commentId}
        authorId={reportTarget?.authorId}
      />
    </div>
  );
}
