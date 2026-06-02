'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { postApi, PostDetail, JuryResult, VoteResult } from '@/lib/api/community/postApi';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { VoteBar, SideStory, JurorCard } from '@/components/community/c3';
import { GRN, RED, GRN_BG, RED_BG } from '@/lib/constants/factionColors';

const COMMENT_PAGE_SIZE = 10;

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

// View A: 관람자 — C3_StoryDetail (Tone L)
function C3StoryDetail({
  post,
  voteResult,
  comments,
  onVote,
  isVoting,
  hasMoreComments,
  loadingMoreComments,
  commentBottomRef,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
  onVote: (optionId: number) => Promise<void>;
  isVoting: boolean;
  hasMoreComments: boolean;
  loadingMoreComments: boolean;
  commentBottomRef: React.RefObject<HTMLDivElement>;
}) {
  const [pick, setPick] = useState<'g' | 'r' | null>(post.myVoteSide || null);
  const [voted, setVoted] = useState(post.hasVoted || false);
  const router = useRouter();

  const handlePick = (side: 'g' | 'r') => {
    setPick(side);
  };

  const handleVote = async () => {
    if (!pick) return;
    const optionId = pick === 'g' ? post.voteOptions[0]?.id : post.voteOptions[1]?.id;
    if (!optionId) return;

    try {
      await onVote(optionId);
      setVoted(true);
    } catch (err) {
      console.error('Vote failed:', err);
    }
  };

  const authorPct = pick === 'g' ? 62 : pick === 'r' ? 46 : (post.authorPct || 50);

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', padding: '16px' }}>
      {/* 상단 네비 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <Link
          href="/community"
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: 'var(--L-ink)',
            textDecoration: 'none',
            cursor: 'pointer',
          }}
        >
          ‹ 광장
        </Link>
        {voted && (
          <div style={{ fontSize: 11, color: GRN, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
            <span>卜</span> 투표 완료
          </div>
        )}
      </div>

      {/* 메타 정보: 카테고리 칩 + 제목 + 작성자 */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 8 }}>
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              background: 'var(--L-card)',
              color: 'var(--L-ink)',
              padding: '4px 10px',
              borderRadius: 20,
            }}
          >
            {post.category}
          </span>
          {post.paired && (
            <>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: GRN }} />
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: RED }} />
            </>
          )}
        </div>
        <h1
          style={{
            fontSize: 18,
            fontWeight: 600,
            fontFamily: 'var(--font-serif)',
            color: 'var(--L-ink)',
            margin: 0,
            marginBottom: 8,
          }}
        >
          {post.title}
        </h1>
        <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>
          익명 · {formatDate(post.createdAt)}
        </div>
      </div>

      {/* SideStory 2개 (clamp=true) */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 20 }}>
        <SideStory
          side="g"
          label="작성자"
          body={post.bodyPublished}
          clamp={true}
          selected={pick === 'g'}
          onSelect={() => handlePick('g')}
          onMore={() => router.push(`/community/${post.id}/read?side=g`)}
        />
        <SideStory
          side="r"
          label="상대방"
          body={post.partnerBodyPublished || '상대방의 이야기를 기다리는 중입니다.'}
          clamp={true}
          selected={pick === 'r'}
          onSelect={() => handlePick('r')}
          onMore={post.partnerBodyPublished ? () => router.push(`/community/${post.id}/read?side=r`) : undefined}
        />
      </div>

      {/* 비율 막대 */}
      <div style={{ marginBottom: 20 }}>
        <VoteBar authorPct={Math.round(authorPct)} big={false} />
        <div
          style={{
            fontSize: 12,
            color: 'var(--L-sub)',
            marginTop: 8,
            textAlign: 'center',
          }}
        >
          작성자 {Math.round(authorPct)}% / 상대방 {Math.round(100 - authorPct)}%
        </div>
      </div>

      {/* 댓글 인라인 목록 (10개씩 무한스크롤) */}
      <div style={{ marginBottom: 80 }}>
        <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--L-ink)', marginBottom: 12 }}>
          댓글 {comments.length}{hasMoreComments ? '+' : ''}
        </div>
        {comments.length === 0 && (
          <div style={{ fontSize: 12, color: 'var(--L-sub)', padding: '16px 0', textAlign: 'center' }}>
            아직 댓글이 없습니다
          </div>
        )}
        {comments.map((comment) => (
          <div key={comment.id}>
            <div style={{ padding: '10px 0', borderBottom: '1px solid var(--L-border)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{
                  fontSize: 12.5, fontWeight: 500,
                  color: (comment as any).isAuthor ? GRN : (comment as any).isPartner ? RED : 'var(--L-ink)',
                }}>
                  {((comment as any).isAuthor || (comment as any).isPartner) ? '* ' : ''}{comment.authorId}
                </span>
                <span style={{ fontSize: 11, color: 'var(--L-sub)' }}>{formatDate(comment.createdAt)}</span>
              </div>
              <div style={{ fontSize: 13.5, color: 'var(--L-ink)', lineHeight: 1.6 }}>{comment.body}</div>
              <div style={{ fontSize: 11.5, color: 'var(--L-sub)', marginTop: 6 }}>공감 {comment.likeCount}</div>
            </div>
            {/* 대댓글 */}
            {comment.replies?.map((reply) => (
              <div key={reply.id} style={{ paddingLeft: 20, padding: '8px 0 8px 20px', borderBottom: '1px solid var(--L-border)' }}>
                <span style={{ color: 'var(--L-sub)', fontSize: 13, marginRight: 6 }}>↳</span>
                <span style={{ fontSize: 12.5, fontWeight: 500, color: (reply as any).isAuthor ? GRN : (reply as any).isPartner ? RED : 'var(--L-ink)' }}>
                  {((reply as any).isAuthor || (reply as any).isPartner) ? '* ' : ''}{reply.authorId}
                </span>
                <span style={{ fontSize: 11, color: 'var(--L-sub)', marginLeft: 6 }}>{formatDate(reply.createdAt)}</span>
                <div style={{ fontSize: 13.5, color: 'var(--L-ink)', lineHeight: 1.6, marginTop: 4, paddingLeft: 20 }}>{reply.body}</div>
              </div>
            ))}
          </div>
        ))}
        {/* 무한스크롤 트리거 */}
        <div ref={commentBottomRef} style={{ height: 16 }} />
        {loadingMoreComments && (
          <div style={{ textAlign: 'center', padding: '8px', color: 'var(--L-sub)', fontSize: 12 }}>불러오는 중...</div>
        )}
      </div>

      {/* 하단 고정 버튼 */}
      <div
        style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          padding: '12px 16px',
          background: 'white',
          borderTop: '1px solid var(--L-border)',
        }}
      >
        <button
          onClick={handleVote}
          disabled={!pick || voted || isVoting}
          style={{
            width: '100%',
            padding: '12px 16px',
            background: voted ? 'var(--L-border)' : 'var(--L-ink)',
            color: voted ? 'var(--L-sub)' : 'white',
            border: 'none',
            borderRadius: 8,
            fontSize: 14,
            fontWeight: 600,
            cursor: voted || !pick || isVoting ? 'default' : 'pointer',
            opacity: voted || !pick || isVoting ? 0.5 : 1,
          }}
        >
          {voted ? '다시 선택하려면 탭하세요' : pick ? '투표 완료하기' : '작성자 · 상대방을 선택하세요'}
        </button>
      </div>
      <div style={{ height: 80 }} />
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
            accent={GRN}
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
          accent={GRN}
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
    return <C3StoryDetail post={post} voteResult={voteResult} comments={comments} onVote={handleVote} isVoting={isVoting} hasMoreComments={hasMoreComments} loadingMoreComments={loadingMoreComments} commentBottomRef={commentBottomRef} />;
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
