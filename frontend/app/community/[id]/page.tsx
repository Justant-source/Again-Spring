'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { postApi, PostDetail, JuryResult, VoteResult } from '@/lib/api/community/postApi';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { VoteBar, SideStory, JurorCard, CommunityComment } from '@/components/community/c3';
import { AUTHOR, PARTNER, AUTHOR_BG, PARTNER_BG } from '@/lib/constants/factionColors';
import { timeAgo } from '@/lib/utils/timeAgo';
import { useGuestInit } from '@/lib/hooks/useGuestInit';

const COMMENT_PAGE_SIZE = 10;

interface PageProps {
  params: { id: string };
}


// 카테고리 enum → 표시 한글 (피드와 동일)
const CAT_LABELS: Record<string, string> = {
  COUPLE: '연인', MARRIED: '부부', FRIEND: '친구', FAMILY: '가족', WORK: '직장', OTHER: '기타',
};
function catLabel(c: string): string {
  return CAT_LABELS[(c || '').toUpperCase()] || '기타';
}

// 액션 행 컬럼 (투표수 · 댓글수 · 공유) 공통 스타일
const ACTION_COL: React.CSSProperties = {
  flex: 1,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 7,
  fontSize: 14,
};

// View A: 관람자 — C3_StoryDetail (Tone L)
function C3StoryDetail({
  post,
  voteResult,
  comments,
  onVote,
  onLike,
  isVoting,
  loadingMoreComments,
  commentBottomRef,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
  onVote: (optionId: number) => Promise<void>;
  onLike: (commentId: number) => void;
  isVoting: boolean;
  hasMoreComments: boolean;
  loadingMoreComments: boolean;
  commentBottomRef: React.RefObject<HTMLDivElement>;
}) {
  const [pick, setPick] = useState<'g' | 'r' | null>(post.myVoteSide || null);
  const [voted, setVoted] = useState(post.hasVoted || false);
  const router = useRouter();

  const handlePick = (side: 'g' | 'r') => {
    // 재투표 금지 — 이미 투표했으면 선택 변경 불가
    if (voted) return;
    setPick(side);
  };

  const handleVote = async () => {
    if (!pick || voted) return;
    const optionId = pick === 'g' ? post.voteOptions[0]?.id : post.voteOptions[1]?.id;
    if (!optionId) return;

    try {
      await onVote(optionId);
      setVoted(true);
    } catch (err) {
      console.error('Vote failed:', err);
    }
  };

  const handleShare = async () => {
    const url = typeof window !== 'undefined' ? window.location.href : '';
    try {
      if (typeof navigator !== 'undefined' && navigator.share) {
        await navigator.share({ title: post.title, url });
      } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(url);
      }
    } catch {
      /* 사용자 취소 등 — 무시 */
    }
  };

  // 투표 완료 후 실제 BE 비율 사용, 아직 투표 전이면 pick에 따라 시각 피드백 (라이브 집계 시뮬레이션)
  const authorPct = voteResult
    ? Math.round(voteResult.options?.[0]?.percentage ?? post.authorPct ?? 50)
    : pick === 'g' ? 62 : pick === 'r' ? 54 : (post.authorPct ?? 58);
  const partnerPct = 100 - authorPct;

  const voteCount = voteResult?.totalVotes ?? post.voteResult?.totalVotes ?? 0;

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh' }}>
      <div style={{ padding: '14px 20px 160px' }}>
        {/* 상단: ‹ 광장 + 투표 완료 도장 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Link href="/community" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
            <span style={{ fontSize: 17, color: 'var(--L-sub)' }}>‹</span>
            <span style={{ fontSize: 13, color: 'var(--L-sub)' }}>광장</span>
          </Link>
          {voted && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 22, height: 22, borderRadius: '50%', border: `1.5px solid ${AUTHOR}`, color: AUTHOR, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>卜</span>
              <span style={{ fontSize: 11.5, color: AUTHOR, fontWeight: 500 }}>투표 완료</span>
            </div>
          )}
        </div>

        {/* 카테고리 + paired 점 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginTop: 14 }}>
          <span style={{ fontSize: 11, color: 'var(--L-bg)', background: 'var(--L-ink)', borderRadius: 999, padding: '2px 9px' }}>
            {catLabel(post.category)}
          </span>
          {post.paired && (
            <>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: AUTHOR }} />
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: PARTNER, marginLeft: -3 }} />
            </>
          )}
        </div>

        {/* 제목 + 메타 */}
        <h1 style={{ fontSize: 20, color: 'var(--L-ink)', fontWeight: 500, fontFamily: 'var(--font-serif)', margin: 0, marginTop: 10, lineHeight: 1.4 }}>
          {post.title}
        </h1>
        <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 6 }}>
          익명 · {timeAgo(post.createdAt)}
        </div>

        {/* 양쪽 사연 (clamp=true) */}
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 9 }}>
          <SideStory
            side="g"
            label="작성자"
            body={post.bodyPublished}
            clamp
            selected={pick === 'g'}
            onSelect={() => handlePick('g')}
            onMore={() => router.push(`/community/${post.id}/read?side=g`)}
          />
          <SideStory
            side="r"
            label="상대방"
            body={post.partnerBodyPublished || '상대방의 이야기를 기다리는 중입니다.'}
            clamp
            selected={pick === 'r'}
            onSelect={() => handlePick('r')}
            onMore={post.partnerBodyPublished ? () => router.push(`/community/${post.id}/read?side=r`) : undefined}
          />
        </div>

        {/* 라이브 비율 막대 (얇은 8px + 좌우 라벨) */}
        <div style={{ marginTop: 16 }}>
          <div style={{ display: 'flex', height: 8, borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ width: `${authorPct}%`, background: AUTHOR, transition: 'width .25s' }} />
            <div style={{ flex: 1, background: PARTNER }} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--L-sub)', marginTop: 7 }}>
            <span>작성자 {authorPct}%</span>
            <span>상대방 {partnerPct}%</span>
          </div>
        </div>

        {/* 액션 행 — 투표수 · 댓글수 · 공유하기 */}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 16, paddingTop: 14, paddingBottom: 4, borderTop: '1px solid var(--L-border)', color: 'var(--L-sub)' }}>
          <span style={ACTION_COL}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M9 11l3-8 3 8M5 11h14v8a2 2 0 01-2 2H7a2 2 0 01-2-2z" strokeLinejoin="round" /></svg>
            {voteCount}
          </span>
          <span style={ACTION_COL}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z" strokeLinejoin="round" /></svg>
            {comments.length}
          </span>
          <button onClick={handleShare} style={{ ...ACTION_COL, background: 'none', border: 'none', color: 'var(--L-sub)', cursor: 'pointer', fontFamily: 'inherit', padding: 0 }}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 3v13M12 3L8 7M12 3l4 4M5 14v5a2 2 0 002 2h10a2 2 0 002-2v-5" strokeLinecap="round" strokeLinejoin="round" /></svg>
            공유하기
          </button>
        </div>

        {/* 댓글 인라인 목록 — 블라인드 방식 (무한스크롤) */}
        <div style={{ marginTop: 4, marginBottom: 80 }}>
          {comments.length === 0 && (
            <div style={{ fontSize: 12, color: 'var(--L-sub)', padding: '16px 0', textAlign: 'center' }}>
              아직 댓글이 없습니다
            </div>
          )}
          {comments.map((comment) => (
            <div key={comment.id}>
              <CommunityComment
                nick={comment.authorNickname || comment.authorId}
                isAuthor={comment.isAuthor ?? false}
                isPartner={comment.isPartner ?? false}
                time={timeAgo(comment.createdAt)}
                text={comment.body}
                likeCount={comment.likeCount}
                isLiked={comment.isLiked}
                onLike={() => onLike(comment.id)}
                onReply={() => router.push(`/community/${post.id}/comments`)}
              />
              {comment.replies?.map((reply) => (
                <CommunityComment
                  key={reply.id}
                  nick={reply.authorNickname || reply.authorId}
                  isAuthor={reply.isAuthor ?? false}
                  isPartner={reply.isPartner ?? false}
                  time={timeAgo(reply.createdAt)}
                  text={reply.body}
                  likeCount={reply.likeCount}
                  isLiked={reply.isLiked}
                  isReply
                  onLike={() => onLike(reply.id)}
                />
              ))}
            </div>
          ))}
          {/* 무한스크롤 트리거 */}
          <div ref={commentBottomRef} style={{ height: 16 }} />
          {loadingMoreComments && (
            <div style={{ textAlign: 'center', padding: '8px 0', color: 'var(--L-sub)', fontSize: 12 }}>불러오는 중...</div>
          )}
        </div>
      </div>

      {/* 하단 고정 영역: 댓글바 + 투표 버튼 */}
      <div style={{ position: 'fixed', bottom: 0, left: 0, right: 0, maxWidth: 640, marginLeft: 'auto', marginRight: 'auto', background: 'var(--L-bg)' }}>
        {/* 댓글 입력바 — 탭하면 댓글 페이지로 */}
        <div
          role="button"
          tabIndex={0}
          onClick={() => router.push(`/community/${post.id}/comments`)}
          onKeyDown={(e) => { if (e.key === 'Enter') router.push(`/community/${post.id}/comments`); }}
          style={{
            borderTop: '1px solid var(--L-border)',
            padding: '12px 20px',
            cursor: 'text',
          }}
        >
          <span style={{ fontSize: 14.5, color: 'var(--L-sub)' }}>댓글을 남겨주세요.</span>
        </div>
        {/* 투표 버튼 */}
        <div style={{ padding: '8px 20px 20px', background: 'var(--L-bg)' }}>
          <button
            onClick={handleVote}
            disabled={!pick || voted || isVoting}
            style={{
              width: '100%',
              padding: '13px 0',
              background: voted ? 'var(--L-border)' : 'var(--L-ink)',
              color: voted ? 'var(--L-sub)' : 'var(--L-bg)',
              border: 'none',
              borderRadius: 4,
              fontSize: 15,
              fontWeight: 500,
              cursor: voted || !pick || isVoting ? 'default' : 'pointer',
              opacity: !pick && !voted ? 0.6 : 1,
              fontFamily: 'inherit',
            }}
          >
            {voted ? '투표 완료' : isVoting ? '처리 중...' : pick ? '투표 완료하기' : '작성자 · 상대방을 선택하세요'}
          </button>
        </div>
      </div>
    </div>
  );
}

// View B: 작성자 + VOTING + partner 없음 — C3_ResultSolo (Tone P)
function C3ResultSolo({
  post,
  voteResult,
  comments,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
}) {
  const router = useRouter();

  return (
    <div style={{ background: 'var(--P-bg)', minHeight: '100vh', padding: '16px' }}>
      {/* 상단 메타 */}
      <div
        style={{
          fontSize: 12,
          color: 'var(--P-sub)',
          marginBottom: 20,
          display: 'flex',
          justifyContent: 'space-between',
        }}
      >
        <span>{post.category}</span>
        <span>{voteResult?.totalVotes || 0}표 · 댓글 {comments.length}</span>
      </div>

      {/* 제목 */}
      <h1
        style={{
          fontSize: 18,
          fontWeight: 600,
          fontFamily: 'var(--font-serif)',
          color: 'var(--P-ink)',
          margin: 0,
          marginBottom: 20,
        }}
      >
        {post.title}
      </h1>

      {/* 2칸 그리드: SideStory */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
        <SideStory
          side="g"
          label="작성자"
          body={post.bodyPublished}
          clamp={false}
          selected={false}
          onSelect={() => {}}
          onMore={() => router.push(`/community/${post.id}/read?side=g`)}
        />
        <SideStory
          side="r"
          label="상대방"
          body={post.partnerBodyPublished || ''}
          clamp={false}
          selected={false}
          onSelect={undefined}
          onMore={post.partnerBodyPublished ? () => router.push(`/community/${post.id}/read?side=r`) : undefined}
        />
      </div>

      {/* VoteBar */}
      {voteResult && (
        <div style={{ marginBottom: 20 }}>
          <VoteBar authorPct={Math.round(post.authorPct || 50)} big={true} />
          <div
            style={{
              fontSize: 12,
              color: 'var(--P-sub)',
              marginTop: 8,
              textAlign: 'center',
            }}
          >
            작성자 / 상대방
          </div>
        </div>
      )}

      {/* 배심원 카드 (summaryLine 있으면) */}
      {post.partnerBodyPublished && (
        <div style={{ marginBottom: 20 }}>
          <JurorCard
            name="배심원"
            lens="종합"
            text="양쪽 이야기를 들었을 때 각자의 노력이 보입니다."
            accent={AUTHOR}
          />
        </div>
      )}

      {/* 액션 버튼들 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <button
          onClick={() => router.push(`/community/${post.id}/invite`)}
          style={{
            flex: 1,
            padding: '12px 16px',
            background: 'var(--P-ink)',
            color: 'white',
            border: 'none',
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          상대 초대하기
        </button>
        <button
          onClick={() => router.push(`/community/${post.id}/comments`)}
          style={{
            flex: 1,
            padding: '12px 16px',
            background: 'transparent',
            color: 'var(--P-ink)',
            border: `1px solid var(--P-border)`,
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          댓글 보기
        </button>
      </div>
    </div>
  );
}

// View C: 작성자 + VOTING + partner 있음 — C3_ResultPair (Tone P)
function C3ResultPair({
  post,
  voteResult,
  comments,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
}) {
  const router = useRouter();

  return (
    <div style={{ background: 'var(--P-bg)', minHeight: '100vh', padding: '16px' }}>
      {/* 상단 메타 */}
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 20 }}>
        {post.category} · 양쪽 도착
      </div>

      {/* 2칙 그리드 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
        <SideStory
          side="g"
          label="작성자"
          body={post.bodyPublished}
          clamp={false}
          selected={false}
          onSelect={() => {}}
          onMore={() => {}}
        />
        <SideStory
          side="r"
          label="상대방"
          body={post.partnerBodyPublished || ''}
          clamp={false}
          selected={false}
          onSelect={() => {}}
          onMore={() => {}}
        />
      </div>

      {/* VoteBar */}
      {voteResult && (
        <div style={{ marginBottom: 20 }}>
          <VoteBar authorPct={Math.round(post.authorPct || 50)} big={true} />
        </div>
      )}

      {/* AI 배심원 종합 */}
      <div style={{ marginBottom: 20 }}>
        <JurorCard
          name="AI 배심원"
          lens="종합"
          text="양쪽 모두 대화를 통해 더 깊이 이해할 수 있었어요."
          accent={AUTHOR}
        />
      </div>

      {/* 결과 공유 버튼 */}
      <button
        style={{
          width: '100%',
          padding: '12px 16px',
          background: 'var(--P-ink)',
          color: 'white',
          border: 'none',
          borderRadius: 8,
          fontSize: 14,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        결과 공유
      </button>
    </div>
  );
}

// View D: status=CLOSED — C3_Closed (Tone P)
function C3Closed({
  post,
  voteResult,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
}) {
  return (
    <div style={{ background: 'var(--P-bg)', minHeight: '100vh', padding: '16px' }}>
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          background: '#F0E8E0',
          color: '#8A7F6B',
          padding: '6px 12px',
          borderRadius: 16,
          display: 'inline-block',
          marginBottom: 20,
        }}
      >
        마감됨
      </div>

      <h1
        style={{
          fontSize: 18,
          fontWeight: 600,
          fontFamily: 'var(--font-serif)',
          color: 'var(--P-ink)',
          margin: 0,
          marginBottom: 20,
        }}
      >
        {post.title}
      </h1>

      {/* 투표 수 */}
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 20 }}>
        최종 {voteResult?.totalVotes || 0}표
      </div>

      {/* 2칸 그리드 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
        <SideStory
          side="g"
          label="작성자"
          body={post.bodyPublished || ''}
          clamp={false}
          selected={false}
          onSelect={() => {}}
          onMore={() => {}}
        />
        <SideStory
          side="r"
          label="상대방"
          body={post.partnerBodyPublished || ''}
          clamp={false}
          selected={false}
          onSelect={() => {}}
          onMore={() => {}}
        />
      </div>

      {/* VoteBar */}
      {voteResult && (
        <div style={{ marginBottom: 20 }}>
          <VoteBar authorPct={Math.round(post.authorPct || 50)} big={true} />
        </div>
      )}

      {/* 안내 메시지 */}
      <div
        style={{
          fontSize: 12,
          color: 'var(--P-sub)',
          background: 'var(--P-card)',
          padding: '12px',
          borderRadius: 8,
          textAlign: 'center',
        }}
      >
        투표가 마감되어 결과가 고정됐어요
      </div>

      {/* 결과 공유 버튼 */}
      <button
        style={{
          width: '100%',
          padding: '12px 16px',
          background: 'var(--P-ink)',
          color: 'white',
          border: 'none',
          borderRadius: 8,
          fontSize: 14,
          fontWeight: 600,
          cursor: 'pointer',
          marginTop: 20,
        }}
      >
        결과 공유
      </button>
    </div>
  );
}

export default function CommunityPostPage({ params }: PageProps) {
  useGuestInit();
  const [post, setPost] = useState<PostDetail | null>(null);
  const [voteResult, setVoteResult] = useState<VoteResult | null>(null);
  const [juryResult, setJuryResult] = useState<JuryResult | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentPage, setCommentPage] = useState(0);
  const [hasMoreComments, setHasMoreComments] = useState(true);
  const [loadingMoreComments, setLoadingMoreComments] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isVoting, setIsVoting] = useState(false);
  const commentBottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const loadPost = async () => {
      try {
        setLoading(true);
        const postData = await postApi.get(params.id);
        setPost(postData);

        // 댓글 첫 페이지 (10개)
        const commentsData = await commentApi.list(params.id, 0, COMMENT_PAGE_SIZE);
        setComments(commentsData);
        setHasMoreComments(commentsData.length === COMMENT_PAGE_SIZE);
        setCommentPage(1);

        // Get jury result — author only (BE는 401 반환, 비로그인 시 전역 auth 에러 유발 방지)
        if (postData.isAuthor) {
          postApi.getJury(params.id).then(setJuryResult).catch(() => {});
        }
      } catch (err) {
        console.error('Failed to load post:', err);
        setError('사연을 불러올 수 없습니다');
      } finally {
        setLoading(false);
      }
    };

    loadPost();
  }, [params.id]);

  // 댓글 추가 로드 (무한스크롤)
  const loadMoreComments = useCallback(async () => {
    if (loadingMoreComments || !hasMoreComments || !post) return;
    setLoadingMoreComments(true);
    try {
      const data = await commentApi.list(post.id, commentPage, COMMENT_PAGE_SIZE);
      if (data.length < COMMENT_PAGE_SIZE) setHasMoreComments(false);
      setComments(prev => [...prev, ...data]);
      setCommentPage(p => p + 1);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingMoreComments(false);
    }
  }, [post, commentPage, loadingMoreComments, hasMoreComments]);

  useEffect(() => {
    const el = commentBottomRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) loadMoreComments(); },
      { threshold: 0.1 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [loadMoreComments]);

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
    } catch {
      // 401 등 — axios interceptor가 처리
    }
  };

  const handleVote = async (optionId: number) => {
    // 이미 투표했으면 재투표 불가
    if (post?.hasVoted) return;
    setIsVoting(true);
    try {
      const result = await postApi.vote(params.id, optionId);
      setVoteResult(result);
      setPost((prev) => prev ? { ...prev, hasVoted: true, myVoteSide: optionId === prev.voteOptions?.[0]?.id ? 'g' : 'r' } : null);
    } catch (err: any) {
      // 409 ALREADY_VOTED는 조용히 처리 (UI는 이미 잠금 상태)
      if (err?.response?.status !== 409) console.error('Vote failed:', err);
    } finally {
      setIsVoting(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--P-sub)' }}>
        불러오는 중...
      </div>
    );
  }

  if (error || !post) {
    return (
      <div
        style={{
          padding: '16px',
          background: '#FEE',
          border: '1px solid #F99',
          borderRadius: 8,
          fontSize: 13,
          color: '#C33',
          textAlign: 'center',
        }}
      >
        {error || '사연을 찾을 수 없습니다'}
      </div>
    );
  }

  // Render logic
  if (!post.isAuthor) {
    // View A: 관람자
    return <C3StoryDetail post={post} voteResult={voteResult} comments={comments} onVote={handleVote} onLike={handleLike} isVoting={isVoting} hasMoreComments={hasMoreComments} loadingMoreComments={loadingMoreComments} commentBottomRef={commentBottomRef} />;
  } else if (post.status === 'VOTING' && !post.paired) {
    // View B: 작성자 + VOTING + partner 없음
    return <C3ResultSolo post={post} voteResult={voteResult} comments={comments} />;
  } else if (post.status === 'VOTING' && post.paired) {
    // View C: 작성자 + VOTING + partner 있음
    return <C3ResultPair post={post} voteResult={voteResult} comments={comments} />;
  } else {
    // View D: status=CLOSED
    return <C3Closed post={post} voteResult={voteResult} />;
  }
}
