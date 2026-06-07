'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { postApi, PostDetail, JuryResult, VoteResult } from '@/lib/api/community/postApi';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { getOrCreateDeviceId } from '@/lib/utils/deviceId';
import { VoteBar, SideStory, JurySection, CommunityComment } from '@/components/community/c3';
import { AUTHOR, PARTNER, AUTHOR_BG, PARTNER_BG } from '@/lib/constants/factionColors';
import { timeAgo } from '@/lib/utils/timeAgo';
import { useGuestInit } from '@/lib/hooks/useGuestInit';
import { useVoteStore } from '@/lib/store/voteStore';
import { CommentBar } from '@/components/community/c3/CommentBar';
import { CommentComposeSheet } from '@/components/community/c3/CommentComposeSheet';
import { ReportModal } from '@/components/community/ReportModal';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { InviteSheet } from '@/components/community/InviteSheet';
import { GuestInfoSheet } from '@/components/shared/GuestInfoSheet';
import { useUserStore } from '@/lib/store/userStore';

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
  onCancelVote,
  onLike,
  isVoting,
  loadingMoreComments,
  commentBottomRef,
  onOpenCompose,
  onEditComment,
  onDeleteComment,
  onReportComment,
  highlightedId,
  composeOpen,
  commentText,
  onCommentTextChange,
  onCommentSubmit,
  onCommentComposeClose,
  replyToNick,
  submitError,
  isAuthor = false,
  activeInviteToken,
  onInviteClick,
  inviteSheetOpen = false,
  onInviteClose,
  onInviteSent,
  guestSheetOpen = false,
  onGuestSheetClose,
  user,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
  onVote: (optionId: number) => Promise<void>;
  onCancelVote: () => Promise<void>;
  onLike: (commentId: number) => void;
  isVoting: boolean;
  hasMoreComments: boolean;
  loadingMoreComments: boolean;
  commentBottomRef: React.RefObject<HTMLDivElement>;
  onOpenCompose: (parentId?: number, replyNick?: string) => void;
  onEditComment: (comment: Comment) => void;
  onDeleteComment: (commentId: number) => void;
  onReportComment: (commentId: number, authorId: string) => void;
  highlightedId: number | null;
  composeOpen: boolean;
  commentText: string;
  onCommentTextChange: (v: string) => void;
  onCommentSubmit: () => Promise<void>;
  onCommentComposeClose: () => void;
  replyToNick: string | undefined;
  submitError: string | null;
  // 초대 관련
  isAuthor?: boolean;
  activeInviteToken?: string | null;
  onInviteClick?: () => void;
  inviteSheetOpen?: boolean;
  onInviteClose?: () => void;
  onInviteSent?: (token: string) => void;
  guestSheetOpen?: boolean;
  onGuestSheetClose?: () => void;
  user?: import('@/lib/types/user').User | null;
}) {
  const router = useRouter();
  const storedSide = useVoteStore((s) => s.votes[post.id] ?? null);
  const { clearVote: storeClearVote } = useVoteStore();
  const myVotedId = post.voteResult?.myVotedOptionId;

  // 백엔드가 명시적으로 미투표라고 하면 voteStore 스테일 데이터 무시
  const backendSaysNotVoted = post.isVoted === false && myVotedId == null;
  const effectiveStoredSide = backendSaysNotVoted ? null : storedSide;

  const derivedSide: 'g' | 'r' | null =
    myVotedId != null
      ? (myVotedId === post.voteOptions?.[0]?.id ? 'g' : 'r')
      : (post.myVoteSide ?? effectiveStoredSide);
  const [pick, setPick] = useState<'g' | 'r' | null>(derivedSide);
  const [voted, setVoted] = useState(
    Boolean(post.isVoted || post.hasVoted || myVotedId != null) || effectiveStoredSide != null
  );

  // 백엔드가 미투표라 했는데 voteStore에 스테일 데이터가 있으면 정리
  useEffect(() => {
    if (backendSaysNotVoted && storedSide != null) {
      storeClearVote(post.id);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const [localVoteCount, setLocalVoteCount] = useState(
    voteResult?.totalVotes ?? post.voteResult?.totalVotes ?? 0
  );

  // 우측 끝 투표 버튼 — 해당 쪽 투표 / 이미 이 쪽에 투표했으면 취소
  const handleVoteSide = async (side: 'g' | 'r') => {
    if (isVoting) return;
    if (voted && pick === side) {
      // 취소 — 즉시 카운트 감소
      setLocalVoteCount(prev => Math.max(0, prev - 1));
      try {
        await onCancelVote();
        setPick(null);
        setVoted(false);
      } catch (err) {
        setLocalVoteCount(prev => prev + 1); // 실패 시 롤백
        console.error('Cancel vote failed:', err);
      }
      return;
    }
    if (voted) return; // 다른 쪽에 이미 투표함 — 변경 불가
    const optionId = side === 'g' ? post.voteOptions[0]?.id : post.voteOptions[1]?.id;
    if (!optionId) return;
    // 즉시 카운트 증가
    setLocalVoteCount(prev => prev + 1);
    setPick(side);
    try {
      await onVote(optionId);
      setVoted(true);
    } catch (err) {
      setLocalVoteCount(prev => Math.max(0, prev - 1)); // 실패 시 롤백
      setPick(null);
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

  // 투표 완료 후 실제 BE 비율 사용, 투표 전 선택 시 내 한 표를 더한 예상 비율로 미리보기
  const existingTotal = post.voteResult?.totalVotes ?? 0;
  const existingAuthorCount = post.voteResult?.options?.[0]?.count ?? 0;
  const existingPartnerCount = post.voteResult?.options?.[1]?.count ?? 0;
  const authorPct = voteResult
    ? Math.round(voteResult.options?.[0]?.percentage ?? post.authorPct ?? 50)
    : pick === 'g'
      ? Math.round((existingAuthorCount + 1) / (existingTotal + 1) * 100)
      : pick === 'r'
      ? Math.round(existingAuthorCount / (existingTotal + 1) * 100)
      : Math.round(post.authorPct ?? 50);
  const partnerPct = 100 - authorPct;

  // 표 수 — 막대는 비율(%)로, 라벨은 표 수로 표시. 선택 시 내 한 표 미리 반영
  const authorCount = voteResult
    ? (voteResult.options?.[0]?.count ?? 0)
    : pick === 'g'
      ? existingAuthorCount + 1
      : existingAuthorCount;
  const partnerCount = voteResult
    ? (voteResult.options?.[1]?.count ?? 0)
    : pick === 'r'
      ? existingPartnerCount + 1
      : existingPartnerCount;

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
        isMine={comment.isMine ?? false}
        onLike={() => onLike(comment.id)}
        onReply={
          isReply
            ? undefined
            : () => onOpenCompose(comment.id, comment.authorNickname || comment.authorId)
        }
        onEdit={() => onEditComment(comment)}
        onDelete={() => onDeleteComment(comment.id)}
        onReport={() => onReportComment(comment.id, comment.authorId)}
      />
      {!isReply && comment.replies?.map((r) => renderComment(r, true))}
    </div>
  );

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', position: 'relative' }}>
      <div style={{ padding: '14px 20px 160px' }}>
        {/* 상단: ‹ 광장 + 투표 완료 도장 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Link href="/community" style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
            <span style={{ fontSize: 17, color: 'var(--L-sub)' }}>‹</span>
            <span style={{ fontSize: 13, color: 'var(--L-sub)' }}>광장</span>
          </Link>
          {voted && (
            <div data-testid="vote-complete-badge" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 22, height: 22, borderRadius: '50%', border: `1.5px solid ${AUTHOR}`, color: AUTHOR, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>卜</span>
              <span style={{ fontSize: 11.5, color: AUTHOR, fontWeight: 500 }}>완료</span>
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

        {/* 제목 */}
        <h1 style={{ fontSize: 20, color: 'var(--L-ink)', fontWeight: 500, fontFamily: 'var(--font-serif)', margin: 0, marginTop: 10, lineHeight: 1.4 }}>
          {post.title}
        </h1>

        {/* 양쪽 사연 — 작성자 위, 상대방 아래 / 닉네임·시간은 각 카드 라벨 오른쪽 */}
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 9 }}>
          <SideStory
            side="g"
            label="작성자"
            meta={`${post.authorNickname || '익명'} · ${timeAgo(post.createdAt)}`}
            body={post.bodyPublished}
            clamp
            selected={pick === 'g'}
            voted={voted && pick === 'g'}
            voteDisabled={isVoting || (voted && pick !== 'g')}
            onSelect={() => router.push(`/community/${post.id}/read?side=g`)}
            onVote={() => handleVoteSide('g')}
          />
          {post.partnerBodyPublished ? (
            // 상대방 이야기가 있을 때 — 모두 동일
            <SideStory
              side="r"
              label="상대방"
              meta={post.partnerAnsweredAt ? `${post.partnerNickname || '익명'} · ${timeAgo(post.partnerAnsweredAt)}` : (post.partnerNickname || '익명')}
              body={post.partnerBodyPublished}
              clamp
              selected={pick === 'r'}
              voted={voted && pick === 'r'}
              voteDisabled={isVoting || (voted && pick !== 'r')}
              onSelect={() => router.push(`/community/${post.id}/read?side=r`)}
              onVote={() => handleVoteSide('r')}
            />
          ) : isAuthor ? (
            // 작성자 — 초대 또는 대기 슬롯
            activeInviteToken ? (
              <div
                style={{ border: `1.5px dashed ${PARTNER}`, borderRadius: 12, padding: '13px 14px',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                  minHeight: 96, background: 'rgba(95,143,118,0.04)' }}
              >
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: PARTNER, marginBottom: 8 }} />
                <span style={{ fontSize: 12, color: PARTNER, fontWeight: 500 }}>초대함 · 답변 대기 중</span>
                <button
                  onClick={onInviteClick}
                  style={{ marginTop: 8, fontSize: 11, color: 'var(--L-point)', fontWeight: 500,
                    background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'inherit' }}
                >
                  링크 다시 보내기
                </button>
              </div>
            ) : (
              <button
                data-testid="invite-partner-btn"
                onClick={onInviteClick}
                style={{ border: `1.5px dashed ${PARTNER}`, borderRadius: 12, padding: '13px 14px',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                  minHeight: 96, cursor: 'pointer', background: 'rgba(95,143,118,0.04)', width: '100%',
                  fontFamily: 'inherit' }}
              >
                <div style={{ width: 32, height: 32, borderRadius: '50%', border: `1.5px solid ${PARTNER}`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={PARTNER} strokeWidth="2" strokeLinecap="round">
                    <path d="M12 5v14M5 12h14" />
                  </svg>
                </div>
                <span style={{ fontSize: 12, color: PARTNER, fontWeight: 500 }}>상대 초대하기</span>
                <span style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 3 }}>상대의 이야기로 채워주세요</span>
              </button>
            )
          ) : (
            // 관람자 — 빈 상대 슬롯 (투표 가능)
            <SideStory
              side="r"
              label="상대방"
              body=""
              clamp
              selected={pick === 'r'}
              voted={voted && pick === 'r'}
              voteDisabled={isVoting || (voted && pick !== 'r')}
              onVote={() => handleVoteSide('r')}
            />
          )}
        </div>

        {/* 라이브 비율 막대 (얇은 8px + 좌우 라벨) */}
        <div style={{ marginTop: 16 }}>
          <div style={{ display: 'flex', height: 8, borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ width: `${authorPct}%`, background: AUTHOR, transition: 'width .25s' }} />
            <div style={{ flex: 1, background: PARTNER }} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--L-sub)', marginTop: 7 }}>
            <span>작성자 {authorCount}표</span>
            <span>상대방 {partnerCount}표</span>
          </div>
        </div>

        {/* 액션 행 — 투표수 · 댓글수 · 공유하기 */}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 16, paddingTop: 14, paddingBottom: 4, borderTop: '1px solid var(--L-border)', color: 'var(--L-sub)' }}>
          <span style={ACTION_COL}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M9 11l3-8 3 8M5 11h14v8a2 2 0 01-2 2H7a2 2 0 01-2-2z" strokeLinejoin="round" /></svg>
            {localVoteCount}
          </span>
          <span style={ACTION_COL}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z" strokeLinejoin="round" /></svg>
            {post.commentCount ?? comments.length}
          </span>
          <button onClick={handleShare} style={{ ...ACTION_COL, background: 'none', border: 'none', color: 'var(--L-sub)', cursor: 'pointer', fontFamily: 'inherit', padding: 0 }}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 3v13M12 3L8 7M12 3l4 4M5 14v5a2 2 0 002 2h10a2 2 0 002-2v-5" strokeLinecap="round" strokeLinejoin="round" /></svg>
            공유하기
          </button>
        </div>

        {/* 댓글 전체 목록 — 블라인드 방식 (무한스크롤) */}
        <div style={{ marginTop: 4, marginBottom: 80 }}>
          {comments.length === 0 && (
            <div style={{ fontSize: 12, color: 'var(--L-sub)', padding: '16px 0', textAlign: 'center' }}>
              아직 댓글이 없습니다
            </div>
          )}
          {comments.map((c) => renderComment(c, false))}
          {/* 무한스크롤 트리거 */}
          <div ref={commentBottomRef} style={{ height: 16 }} />
          {loadingMoreComments && (
            <div style={{ textAlign: 'center', padding: '8px 0', color: 'var(--L-sub)', fontSize: 12 }}>불러오는 중...</div>
          )}
        </div>
      </div>

      {/* 하단 고정 영역: 댓글 입력바 */}
      <div style={{ position: 'fixed', bottom: 0, left: 0, right: 0, maxWidth: 640, marginLeft: 'auto', marginRight: 'auto', background: 'var(--L-bg)' }}>
        <CommentBar
          replyTo={replyToNick}
          onClick={() => onOpenCompose(undefined, undefined)}
        />
      </div>

      {/* 댓글 작성 바텀시트 */}
      {composeOpen && (
        <CommentComposeSheet
          value={commentText}
          onChange={(v) => { onCommentTextChange(v); }}
          onSubmit={onCommentSubmit}
          onClose={onCommentComposeClose}
          replyTo={replyToNick}
          error={submitError}
        />
      )}

      {/* 초대 바텀시트 */}
      {inviteSheetOpen && onInviteClose && onInviteSent && (
        <InviteSheet
          postId={post.id}
          initialToken={activeInviteToken}
          onClose={onInviteClose}
          onSent={onInviteSent}
        />
      )}

      {/* 게스트 안내 시트 */}
      {guestSheetOpen && user && onGuestSheetClose && (
        <GuestInfoSheet user={user} onClose={onGuestSheetClose} />
      )}
    </div>
  );
}

// View B: 작성자 + VOTING + partner 없음 — C3_ResultSolo (Tone P)
function C3ResultSolo({
  post,
  voteResult,
  comments,
  jury,
  juryExhausted,
  onRetryJury,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
  jury: JuryResult | null;
  juryExhausted?: boolean;
  onRetryJury?: () => void;
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
          onMore={() => {}}
        />
        <SideStory
          side="r"
          label="상대방"
          body={post.partnerBodyPublished || ''}
          clamp={false}
          selected={false}
          onSelect={undefined}
          onMore={undefined}
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

      {/* AI 배심원 섹션 */}
      <div style={{ marginBottom: 20 }}>
        <JurySection jury={jury} jurorCount={post.jurorCount ?? 0} exhausted={juryExhausted} onRetry={onRetryJury} />
      </div>

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
      </div>
    </div>
  );
}

// View C: 작성자 + VOTING + partner 있음 — C3_ResultPair (Tone P)
function C3ResultPair({
  post,
  voteResult,
  comments,
  jury,
  juryExhausted,
  onRetryJury,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  comments: Comment[];
  jury: JuryResult | null;
  juryExhausted?: boolean;
  onRetryJury?: () => void;
}) {
  const handleShare = async () => {
    const url = typeof window !== 'undefined' ? window.location.href : '';
    try {
      if (typeof navigator !== 'undefined' && navigator.share) {
        await navigator.share({ title: post.title, url });
      } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(url);
      }
    } catch { /* 사용자 취소 등 — 무시 */ }
  };

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

      {/* AI 배심원 섹션 */}
      <div style={{ marginBottom: 20 }}>
        <JurySection jury={jury} jurorCount={post.jurorCount ?? 0} exhausted={juryExhausted} onRetry={onRetryJury} />
      </div>

      {/* 결과 공유 버튼 */}
      <button
        onClick={handleShare}
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
  jury,
  juryExhausted,
  onRetryJury,
}: {
  post: PostDetail;
  voteResult: VoteResult | null;
  jury: JuryResult | null;
  juryExhausted?: boolean;
  onRetryJury?: () => void;
}) {
  const handleShare = async () => {
    const url = typeof window !== 'undefined' ? window.location.href : '';
    try {
      if (typeof navigator !== 'undefined' && navigator.share) {
        await navigator.share({ title: post.title, url });
      } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(url);
      }
    } catch { /* 사용자 취소 등 — 무시 */ }
  };

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

      {/* AI 배심원 섹션 */}
      <div style={{ marginBottom: 20 }}>
        <JurySection jury={jury} jurorCount={post.jurorCount ?? 0} exhausted={juryExhausted} onRetry={onRetryJury} />
      </div>

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
        onClick={handleShare}
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

export default function PostDetailClient({ params }: PageProps) {
  useGuestInit();
  const { setVote, clearVote, getVoteSide } = useVoteStore();
  const user = useUserStore((s) => s.user);
  const router = useRouter();
  const searchParams = useSearchParams();
  const highlightId = searchParams.get('highlight') ? Number(searchParams.get('highlight')) : null;
  const scrolledRef = useRef(false);

  const [post, setPost] = useState<PostDetail | null>(null);
  const [voteResult, setVoteResult] = useState<VoteResult | null>(null);
  const [juryResult, setJuryResult] = useState<JuryResult | null>(null);
  const [juryPollingExhausted, setJuryPollingExhausted] = useState(false);
  const [juryRetryKey, setJuryRetryKey] = useState(0);

  // Comment state
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentPage, setCommentPage] = useState(0);
  const [hasMoreComments, setHasMoreComments] = useState(true);
  const [loadingMoreComments, setLoadingMoreComments] = useState(false);
  const [initialCommentLoading, setInitialCommentLoading] = useState(true);
  const [highlightedId, setHighlightedId] = useState<number | null>(null);
  const [commentText, setCommentText] = useState('');
  const [replyToNick, setReplyToNick] = useState<string | undefined>(undefined);
  const [parentCommentId, setParentCommentId] = useState<number | null>(null);
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null);
  const [composeOpen, setComposeOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [likeToast, setLikeToast] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ commentId?: number; authorId?: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);
  const [deleteErrorToast, setDeleteErrorToast] = useState(false);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isVoting, setIsVoting] = useState(false);
  const commentBottomRef = useRef<HTMLDivElement>(null);

  // 초대 state
  const [inviteSheetOpen, setInviteSheetOpen] = useState(false);
  const [localInviteToken, setLocalInviteToken] = useState<string | null>(null);
  const [guestSheetOpen, setGuestSheetOpen] = useState(false);

  // 초기 포스트 + 댓글 로드
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
        setInitialCommentLoading(false);

        // 조회수 기록 (fire & forget) — 디바이스 기준 중복 방지
        postApi.recordView(params.id, getOrCreateDeviceId()).catch(() => {});

        // Get jury result — author only
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

  // 알림 클릭 시 highlight 댓글로 스크롤
  useEffect(() => {
    if (!highlightId || scrolledRef.current || initialCommentLoading) return;
    const el = document.getElementById(`comment-${highlightId}`);
    if (el) {
      scrolledRef.current = true;
      setHighlightedId(highlightId);
      setTimeout(() => {
        const top = el.getBoundingClientRect().top + window.scrollY - 60;
        window.scrollTo({ top, behavior: 'smooth' });
      }, 50);
      setTimeout(() => setHighlightedId(null), 2000);
    }
  }, [highlightId, comments, initialCommentLoading]);

  // AI 배심원 폴링
  useEffect(() => {
    if (!post?.isAuthor) return;
    const target = post.jurorCount ?? 0;
    if (target === 0) return;
    if (juryResult && juryResult.jurors.length >= target) return;

    setJuryPollingExhausted(false);
    let attempts = 0;
    const MAX = Math.max(60, target * 17);
    const timer = setInterval(async () => {
      attempts += 1;
      try {
        const data = await postApi.getJury(params.id);
        setJuryResult(data);
        if (data.jurors.length >= target || attempts >= MAX) {
          clearInterval(timer);
          if (data.jurors.length < target) setJuryPollingExhausted(true);
        }
      } catch {
        if (attempts >= MAX) {
          clearInterval(timer);
          setJuryPollingExhausted(true);
        }
      }
    }, 3000);
    return () => clearInterval(timer);
  }, [post?.isAuthor, post?.jurorCount, params.id, juryRetryKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRetryJury = useCallback(async () => {
    if (!post) return;
    try {
      await postApi.retryJury(post.id);
      setJuryPollingExhausted(false);
      setJuryRetryKey(k => k + 1);
    } catch {
      postApi.getJury(post.id).then(setJuryResult).catch(() => {});
    }
  }, [post]);

  // 댓글 추가 로드 (무한스크롤)
  const loadMoreComments = useCallback(async () => {
    if (loadingMoreComments || !hasMoreComments || !post || initialCommentLoading) return;
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
  }, [post, commentPage, loadingMoreComments, hasMoreComments, initialCommentLoading]);

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

  // Comment handlers
  const openCompose = (parentId?: number, replyNick?: string) => {
    setEditingCommentId(null);
    setCommentText('');
    setParentCommentId(parentId ?? null);
    setReplyToNick(replyNick);
    setComposeOpen(true);
  };

  const openEdit = (comment: Comment) => {
    setEditingCommentId(comment.id);
    setCommentText(comment.body);
    setParentCommentId(null);
    setReplyToNick(undefined);
    setSubmitError(null);
    setComposeOpen(true);
  };

  const closeCompose = () => {
    setComposeOpen(false);
    setParentCommentId(null);
    setReplyToNick(undefined);
    setEditingCommentId(null);
  };

  const applyEditInState = (commentId: number, newBody: string) => {
    setComments((prev) =>
      prev.map((c) => {
        if (c.id === commentId) return { ...c, body: newBody };
        if (c.replies?.some((r) => r.id === commentId)) {
          return { ...c, replies: c.replies.map((r) => (r.id === commentId ? { ...r, body: newBody } : r)) };
        }
        return c;
      })
    );
  };

  const removeFromState = (commentId: number) => {
    setComments((prev) =>
      prev
        .filter((c) => c.id !== commentId)
        .map((c) =>
          c.replies?.some((r) => r.id === commentId)
            ? { ...c, replies: c.replies.filter((r) => r.id !== commentId) }
            : c
        )
    );
  };

  const handleCommentSubmit = async () => {
    if (!commentText.trim()) return;
    setSubmitError(null);
    const body = commentText.trim();
    try {
      if (editingCommentId != null) {
        await commentApi.update(params.id, editingCommentId, body);
        applyEditInState(editingCommentId, body);
        setCommentText('');
        closeCompose();
        return;
      }
      await commentApi.add(params.id, body, parentCommentId || undefined);
      setCommentText('');
      closeCompose();
      const fresh = await commentApi.list(params.id, 0, COMMENT_PAGE_SIZE);
      setComments(fresh);
      setHasMoreComments(fresh.length === COMMENT_PAGE_SIZE);
      setCommentPage(1);
    } catch {
      setSubmitError(editingCommentId != null
        ? '댓글 수정에 실패했습니다. 다시 시도해 주세요.'
        : '댓글 등록에 실패했습니다. 다시 시도해 주세요.');
    }
  };

  const handleDeleteComment = (commentId: number) => {
    setDeleteTarget(commentId);
  };

  const confirmDelete = async () => {
    if (deleteTarget == null) return;
    const id = deleteTarget;
    setDeleteTarget(null);
    try {
      await commentApi.remove(params.id, id);
      removeFromState(id);
    } catch {
      setDeleteErrorToast(true);
      setTimeout(() => setDeleteErrorToast(false), 2500);
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

  const handleReportComment = (commentId: number, authorId: string) => {
    setReportTarget({ commentId, authorId });
    setReportOpen(true);
  };

  // 초대 관련
  const activeInviteToken = post?.inviteToken ?? localInviteToken;

  const handleInviteClick = () => {
    if (user?.isGuest) { setGuestSheetOpen(true); return; }
    setInviteSheetOpen(true);
  };

  // 파트너 도착 폴링 — 작성자가 초대 링크를 보낸 후
  useEffect(() => {
    if (!post?.isAuthor || !activeInviteToken || post.partnerBodyPublished) return;
    const timer = setInterval(async () => {
      try {
        const data = await postApi.get(params.id);
        if (data.paired || data.partnerBodyPublished) {
          setPost(data);
          clearInterval(timer);
        }
      } catch { /* ignore */ }
    }, 4000);
    return () => clearInterval(timer);
  }, [post?.id, post?.isAuthor, activeInviteToken, post?.partnerBodyPublished]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCancelVote = async () => {
    try {
      const result = await postApi.cancelVote(params.id);
      setVoteResult(result);
      setPost(prev => prev ? { ...prev, isVoted: false, hasVoted: false, myVoteSide: null } : null);
      clearVote(params.id);
    } catch (err: any) {
      if (err?.response?.status !== 404) console.error('Cancel vote failed:', err);
    }
  };

  const handleVote = async (optionId: number) => {
    if (post?.isVoted || post?.hasVoted || post?.voteResult?.myVotedOptionId != null) return;
    setIsVoting(true);
    const side = optionId === post?.voteOptions?.[0]?.id ? 'g' : 'r';
    try {
      const result = await postApi.vote(params.id, optionId);
      setVoteResult(result);
      setPost((prev) => prev ? { ...prev, isVoted: true, hasVoted: true, myVoteSide: side } : null);
      setVote(params.id, side);
    } catch (err: any) {
      if (err?.response?.status === 409) {
        setVote(params.id, side);
        setPost((prev) => prev ? { ...prev, isVoted: true, hasVoted: true, myVoteSide: side } : null);
      } else {
        console.error('Vote failed:', err);
        throw err;
      }
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

  // CLOSED: 별도 결과 화면
  if (post.status === 'CLOSED') {
    return <C3Closed post={post} voteResult={voteResult} jury={juryResult} juryExhausted={juryPollingExhausted} onRetryJury={handleRetryJury} />;
  }

  // 작성자·관람자 모두 동일한 C3StoryDetail — 작성자는 상대 슬롯 클릭 시 초대
  return (
    <>
      <C3StoryDetail
        post={post}
        voteResult={voteResult}
        comments={comments}
        onVote={handleVote}
        onCancelVote={handleCancelVote}
        onLike={handleLike}
        isVoting={isVoting}
        hasMoreComments={hasMoreComments}
        loadingMoreComments={loadingMoreComments}
        commentBottomRef={commentBottomRef}
        onOpenCompose={openCompose}
        onEditComment={openEdit}
        onDeleteComment={handleDeleteComment}
        onReportComment={handleReportComment}
        highlightedId={highlightedId}
        composeOpen={composeOpen}
        commentText={commentText}
        onCommentTextChange={setCommentText}
        onCommentSubmit={handleCommentSubmit}
        onCommentComposeClose={closeCompose}
        replyToNick={replyToNick}
        submitError={submitError}
        isAuthor={post.isAuthor ?? false}
        activeInviteToken={activeInviteToken}
        onInviteClick={handleInviteClick}
        inviteSheetOpen={inviteSheetOpen}
        onInviteClose={() => setInviteSheetOpen(false)}
        onInviteSent={(token) => { setLocalInviteToken(token); setInviteSheetOpen(false); }}
        guestSheetOpen={guestSheetOpen}
        onGuestSheetClose={() => setGuestSheetOpen(false)}
        user={user}
      />
      <ConfirmDialog
        open={deleteTarget != null}
        title="이 댓글을 삭제할까요?"
        confirmLabel="삭제"
        cancelLabel="취소"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
      {deleteErrorToast && (
        <div style={{ position: 'fixed', bottom: 80, left: '50%', transform: 'translateX(-50%)', background: 'var(--L-ink)', color: 'var(--L-bg)', fontSize: 13, padding: '10px 20px', borderRadius: 20, zIndex: 400, whiteSpace: 'nowrap' }}>
          댓글 삭제에 실패했습니다. 다시 시도해 주세요.
        </div>
      )}
      {likeToast && (
        <div style={{ position: 'fixed', bottom: 80, left: '50%', transform: 'translateX(-50%)', background: 'var(--L-ink)', color: 'var(--L-bg)', fontSize: 13, padding: '10px 20px', borderRadius: 20, zIndex: 300, whiteSpace: 'nowrap' }}>
          좋아요는 로그인 또는 게스트 시작 후 가능합니다.
        </div>
      )}
      <ReportModal
        isOpen={reportOpen}
        onClose={() => { setReportOpen(false); setReportTarget(null); }}
        postId={params.id}
        commentId={reportTarget?.commentId}
        authorId={reportTarget?.authorId}
      />
    </>
  );
}
