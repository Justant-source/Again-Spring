'use client';

import { useState, useEffect } from 'react';
import { postApi, PostDetail, JuryResult, VoteResult } from '@/lib/api/community/postApi';
import { commentApi, Comment } from '@/lib/api/community/commentApi';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';
import LegalNoticeBox from '@/components/shared/LegalNoticeBox';

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

export default function CommunityPostPage({ params }: PageProps) {
  const [post, setPost] = useState<PostDetail | null>(null);
  const [voteResult, setVoteResult] = useState<VoteResult | null>(null);
  const [juryResult, setJuryResult] = useState<JuryResult | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [loading, setLoading] = useState(true);
  const [crisisOpen, setCrisisOpen] = useState(false);
  const [expandedJurors, setExpandedJurors] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadPost = async () => {
      try {
        setLoading(true);
        const postData = await postApi.get(params.id);
        setPost(postData);

        if (postData.visibility === 'PUBLIC') {
          const votesData = await postApi.vote(params.id, postData.voteOptions[0]?.id || 1);
          setVoteResult(votesData);
        } else {
          const juryData = await postApi.getJury(params.id);
          setJuryResult(juryData);
        }

        const commentsData = await commentApi.list(params.id);
        setComments(commentsData);
      } catch (err) {
        console.error('Failed to load post:', err);
        setError('사연을 불러올 수 없습니다');
      } finally {
        setLoading(false);
      }
    };

    loadPost();
  }, [params.id]);

  const handleCommentSubmit = async () => {
    if (!commentText.trim()) return;

    const keywordCheck = checkKeywords(commentText);
    if (keywordCheck.level === 1) {
      setCrisisOpen(true);
      return;
    }

    try {
      const newComment = await commentApi.add(params.id, commentText.trim());
      setComments([...comments, newComment]);
      setCommentText('');
    } catch (err) {
      console.error('Failed to add comment:', err);
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

  return (
    <div>
      {/* 제목 및 본문 */}
      <h1 style={{ fontSize: 18, fontWeight: 600, color: 'var(--P-ink)', marginBottom: 12 }}>
        {post.title}
      </h1>

      <div
        style={{
          padding: '16px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          borderRadius: 8,
          fontSize: 13,
          lineHeight: 1.8,
          color: 'var(--P-ink)',
          whiteSpace: 'pre-wrap',
          marginBottom: 20,
        }}
      >
        {post.bodyPublished}
      </div>

      <LegalNoticeBox data-testid="ratio-legal-notice" />

      {/* 투표 섹션 (PUBLIC) */}
      {post.visibility === 'PUBLIC' && voteResult && (
        <div style={{ marginTop: 20, marginBottom: 20 }}>
          <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>투표</div>
          <div data-testid="vote-distribution" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {voteResult.options.map((option) => {
              const barWidth = Math.max((option.percentage || 0), 3);
              return (
                <div key={option.id}>
                  <div style={{ fontSize: 12, marginBottom: 4, display: 'flex', justifyContent: 'space-between' }}>
                    <span>{option.label}</span>
                    <span style={{ color: 'var(--P-sub)' }}>{option.percentage}% ({option.count}명)</span>
                  </div>
                  <div
                    style={{
                      width: '100%',
                      height: 24,
                      background: '#E8E6E0',
                      borderRadius: 6,
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${barWidth}%`,
                        background: 'var(--P-a)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 11,
                        fontWeight: 500,
                        color: option.percentage > 10 ? '#5C4030' : 'transparent',
                        transition: 'all 0.3s',
                      }}
                    >
                      {option.percentage > 10 && `${option.percentage}%`}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 12 }}>
            총 {voteResult.totalVotes}명 투표
          </div>
        </div>
      )}

      {/* 배심원 섹션 (PRIVATE) */}
      {post.visibility === 'PRIVATE' && juryResult && (
        <div style={{ marginTop: 20, marginBottom: 20 }}>
          <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>배심원 결과</div>
          <div data-testid="jury-distribution" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {juryResult.distribution.map((option, idx) => {
              const barWidth = Math.max((option.percentage || 0), 3);
              return (
                <div key={idx}>
                  <div style={{ fontSize: 12, marginBottom: 4, display: 'flex', justifyContent: 'space-between' }}>
                    <span>{option.label}</span>
                    <span style={{ color: 'var(--P-sub)' }}>{option.percentage}% ({option.count}명)</span>
                  </div>
                  <div
                    style={{
                      width: '100%',
                      height: 24,
                      background: '#E8E6E0',
                      borderRadius: 6,
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${barWidth}%`,
                        background: 'var(--P-a)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 11,
                        fontWeight: 500,
                        color: option.percentage > 10 ? '#5C4030' : 'transparent',
                        transition: 'all 0.3s',
                      }}
                    >
                      {option.percentage > 10 && `${option.percentage}%`}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* 배심원 개별 의견 아코디언 */}
          <div style={{ marginTop: 16 }}>
            {juryResult.jurors.map((juror, idx) => {
              const isExpanded = expandedJurors.has(idx);
              return (
                <div
                  key={idx}
                  style={{
                    marginBottom: 8,
                    border: '1px solid var(--P-border)',
                    borderRadius: 6,
                    overflow: 'hidden',
                  }}
                >
                  <button
                    onClick={() => {
                      const newSet = new Set(expandedJurors);
                      if (newSet.has(idx)) {
                        newSet.delete(idx);
                      } else {
                        newSet.add(idx);
                      }
                      setExpandedJurors(newSet);
                    }}
                    style={{
                      width: '100%',
                      padding: '12px 14px',
                      background: 'var(--P-card)',
                      border: 'none',
                      textAlign: 'left',
                      cursor: 'pointer',
                      fontSize: 12,
                      color: 'var(--P-ink)',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}
                  >
                    <span>
                      {juror.gender} {juror.ageGroup}
                    </span>
                    <span style={{ color: 'var(--P-sub)' }}>{isExpanded ? '▲' : '▼'}</span>
                  </button>
                  {isExpanded && (
                    <div style={{ padding: '12px 14px', borderTop: '1px solid var(--P-border)', fontSize: 12, lineHeight: 1.6 }}>
                      <div style={{ marginBottom: 8, color: 'var(--P-ink)', fontWeight: 500 }}>
                        {juror.chosenOptionLabel}
                      </div>
                      <div style={{ color: 'var(--P-sub)' }}>
                        {juror.empathyComment}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 12, padding: '12px', background: 'var(--P-card)', borderRadius: 6 }}>
            {juryResult.legalNotice}
          </div>
        </div>
      )}

      {/* 댓글 섹션 */}
      <div style={{ marginTop: 30, borderTop: '1px solid var(--P-border)', paddingTop: 20 }}>
        <h2 style={{ fontSize: 14, fontWeight: 600, color: 'var(--P-ink)', marginBottom: 16 }}>
          댓글 {comments.length}
        </h2>

        {/* 댓글 입력 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <textarea
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="댓글을 입력하세요"
              rows={2}
              style={{
                flex: 1,
                padding: '10px 12px',
                border: '1px solid var(--P-border)',
                borderRadius: 6,
                fontSize: 12,
                fontFamily: 'inherit',
                lineHeight: 1.5,
                outline: 'none',
                resize: 'none',
                color: 'var(--P-ink)',
                background: 'white',
              }}
            />
            <button
              onClick={handleCommentSubmit}
              disabled={!commentText.trim()}
              style={{
                padding: '10px 14px',
                background: 'var(--P-ink)',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                fontSize: 12,
                cursor: 'pointer',
                fontWeight: 500,
                opacity: commentText.trim() ? 1 : 0.5,
                transition: 'all 0.2s',
                alignSelf: 'flex-start',
              }}
              onMouseEnter={(e) => {
                if (commentText.trim()) {
                  e.currentTarget.style.opacity = '0.85';
                }
              }}
              onMouseLeave={(e) => {
                if (commentText.trim()) {
                  e.currentTarget.style.opacity = '1';
                }
              }}
            >
              등록
            </button>
          </div>
        </div>

        {/* 댓글 목록 */}
        {comments.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {comments.map((comment) => (
              <div
                key={comment.id}
                style={{
                  padding: '12px 14px',
                  background: 'var(--P-card)',
                  border: '1px solid var(--P-border)',
                  borderRadius: 6,
                }}
              >
                <div style={{ fontSize: 11, color: 'var(--P-sub)', marginBottom: 6 }}>
                  {comment.authorId} · {formatDate(comment.createdAt)}
                </div>
                <div style={{ fontSize: 12, color: 'var(--P-ink)', lineHeight: 1.6, marginBottom: 8 }}>
                  {comment.body}
                </div>
                <button
                  data-testid="comment-like-btn"
                  onClick={() => {
                    commentApi.toggleLike(params.id, comment.id);
                  }}
                  style={{
                    padding: '4px 8px',
                    background: 'transparent',
                    border: '1px solid var(--P-border)',
                    borderRadius: 4,
                    fontSize: 11,
                    color: 'var(--P-sub)',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'var(--P-ink)';
                    e.currentTarget.style.color = 'var(--P-ink)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--P-border)';
                    e.currentTarget.style.color = 'var(--P-sub)';
                  }}
                >
                  공감 {comment.likeCount}
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '20px', fontSize: 12, color: 'var(--P-sub)' }}>
            아직 댓글이 없습니다
          </div>
        )}
      </div>

      <CrisisResourceModal
        open={crisisOpen}
        onClose={() => setCrisisOpen(false)}
        severity="advisory"
      />
    </div>
  );
}
