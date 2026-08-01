'use client';

import { useState, useCallback, useEffect, useMemo } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AdminTable } from '@/components/admin/AdminTable';
import { EditPostDialog } from '@/components/admin/content/EditPostDialog';
import { EditCommentDialog } from '@/components/admin/content/EditCommentDialog';
import { EditScheduledPostDialog } from '@/components/admin/content/EditScheduledPostDialog';
import { CreateMarketingJobDialog } from '@/components/admin/content/CreateMarketingJobDialog';
import { CreateContentDialog } from '@/components/admin/content/CreateContentDialog';
import {
  listAdminPosts,
  listAdminComments,
  getAdminPost,
  deletePost,
  blockPost,
  unblockPost,
  deleteComment,
  blockComment,
  unblockComment,
  adjustPostLikes,
  adjustCommentLikes,
  listScheduledHoldings,
  cancelScheduledHolding,
  AdminPost,
  AdminComment,
  ScheduledHoldingSummary,
} from '@/lib/api/admin/content';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { AiImproveDialog } from '@/components/admin/content/AiImproveDialog';
import { formatDate, formatNumber } from '@/lib/utils/adminFormat';
import { MoreVertical, ExternalLink, Sparkles, Zap, GitCompare, Plus, Minus } from 'lucide-react';

const COMMENT_STATUS_LABELS: Record<string, { label: string; variant: any }> = {
  ACTIVE: { label: '활성', variant: 'default' },
  BLOCKED: { label: '차단됨', variant: 'destructive' },
};

const CATEGORY_LABELS: Record<string, string> = {
  COUPLE: '연인',
  MARRIED: '부부',
  FRIEND: '친구',
  FAMILY: '가족',
  WORK: '직장',
  OTHER: '기타',
};

// Union type for unified content
type UnifiedContent =
  | { type: 'post'; id: string; data: AdminPost }
  | { type: 'comment'; id: string; data: AdminComment }
  | { type: 'reply'; id: string; data: AdminComment };

export default function AdminContentPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  // Filters
  const [contentTypeFilter, setContentTypeFilter] = useState('ALL');
  const [authorTypeFilter, setAuthorTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Data state
  const [posts, setPosts] = useState<AdminPost[]>([]);
  const [comments, setComments] = useState<AdminComment[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);
  const [commentsLoading, setCommentsLoading] = useState(false);

  // Dialog states
  const [selectedPost, setSelectedPost] = useState<AdminPost | null>(null);
  const [selectedComment, setSelectedComment] = useState<AdminComment | null>(null);
  const [improvePost, setImprovePost] = useState<AdminPost | null>(null);
  const [improveComment, setImproveComment] = useState<AdminComment | null>(null);
  const [marketingPostId, setMarketingPostId] = useState<string | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [likeAdjustLoading, setLikeAdjustLoading] = useState<string | null>(null);

  const [mainTab, setMainTab] = useState<'published' | 'holding'>('published');
  const [holdings, setHoldings] = useState<ScheduledHoldingSummary[]>([]);
  const [holdingsLoading, setHoldingsLoading] = useState(false);
  const [editHoldingId, setEditHoldingId] = useState<string | null>(null);

  // Load posts
  const loadPosts = useCallback(async () => {
    setPostsLoading(true);
    try {
      const res = await listAdminPosts({
        page: 0,
        size: 100,
        synthetic:
          authorTypeFilter === 'AI'
            ? true
            : authorTypeFilter === 'USER'
            ? false
            : undefined,
        category: categoryFilter === 'ALL' ? undefined : categoryFilter || undefined,
        search: searchQuery || undefined,
      });
      setPosts(res.content);
    } catch (error) {
      console.error('Failed to load posts:', error);
    } finally {
      setPostsLoading(false);
    }
  }, [authorTypeFilter, categoryFilter, searchQuery]);

  // Load comments
  const loadComments = useCallback(async () => {
    setCommentsLoading(true);
    try {
      const res = await listAdminComments({
        page: 0,
        size: 100,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        search: searchQuery || undefined,
      });
      setComments(res.content);
    } catch (error) {
      console.error('Failed to load comments:', error);
    } finally {
      setCommentsLoading(false);
    }
  }, [statusFilter, searchQuery]);

  const loadHoldings = useCallback(async () => {
    setHoldingsLoading(true);
    try {
      const rows = await listScheduledHoldings('ALL_PENDING');
      setHoldings(rows);
    } catch (error) {
      console.error('Failed to load scheduled holdings:', error);
      setHoldings([]);
    } finally {
      setHoldingsLoading(false);
    }
  }, []);

  // Handle openImprove query param
  useEffect(() => {
    const improveId = searchParams.get('openImprove');
    if (!improveId) return;
    getAdminPost(improveId)
      .then((post) => {
        setImprovePost(post);
        router.replace('/admin/content');
      })
      .catch(() => {});
  }, []);

  // Load data on mount and when filters change
  useEffect(() => {
    loadPosts();
    loadComments();
    loadHoldings();
  }, [loadPosts, loadComments, loadHoldings]);

  useEffect(() => {
    if (mainTab === 'holding') loadHoldings();
  }, [mainTab, loadHoldings]);

  // Merge and filter unified content
  const unifiedContent = useMemo((): UnifiedContent[] => {
    const merged: UnifiedContent[] = [];

    posts.forEach((post) => {
      merged.push({ type: 'post', id: post.id, data: post });
    });

    comments.forEach((comment) => {
      const type = comment.parentCommentId ? 'reply' : 'comment';
      merged.push({ type, id: String(comment.id), data: comment });
    });

    // Apply content type filter
    let filtered = merged;
    if (contentTypeFilter !== 'ALL') {
      filtered = filtered.filter((item) => item.type === contentTypeFilter);
    }

    // Apply status filter (for comments only)
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((item) => {
        if (item.type === 'post') return true; // posts don't filter by status here
        return (item.data as AdminComment).status === statusFilter;
      });
    }

    return filtered;
  }, [posts, comments, contentTypeFilter, statusFilter]);

  // Post actions
  const handleDeletePost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"을(를) 삭제하시겠습니까?`)) return;
    try {
      await deletePost(post.id);
      loadPosts();
    } catch (error) {
      console.error('Failed to delete post:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  const handleBlockPost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"을(를) 차단하시겠습니까?`)) return;
    try {
      await blockPost(post.id);
      loadPosts();
    } catch (error) {
      console.error('Failed to block post:', error);
      alert('차단에 실패했습니다.');
    }
  };

  const handleUnblockPost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"의 차단을 해제하시겠습니까?`)) return;
    try {
      await unblockPost(post.id);
      loadPosts();
    } catch (error) {
      console.error('Failed to unblock post:', error);
      alert('차단 해제에 실패했습니다.');
    }
  };

  const handleAdjustPostLikes = async (post: AdminPost, delta: 1 | -1) => {
    setLikeAdjustLoading(`post-${post.id}`);
    try {
      const result = await adjustPostLikes(post.id, delta);
      setPosts(
        posts.map((p) => (p.id === post.id ? { ...p, likeCount: result.likeCount } : p))
      );
    } catch (error) {
      console.error('Failed to adjust likes:', error);
      alert('좋아요 조정에 실패했습니다.');
    } finally {
      setLikeAdjustLoading(null);
    }
  };

  // Comment actions
  const handleDeleteComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
    try {
      await deleteComment(comment.id);
      loadComments();
    } catch (error) {
      console.error('Failed to delete comment:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  const handleBlockComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글을 차단하시겠습니까?')) return;
    try {
      await blockComment(comment.id);
      loadComments();
    } catch (error) {
      console.error('Failed to block comment:', error);
      alert('차단에 실패했습니다.');
    }
  };

  const handleUnblockComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글의 차단을 해제하시겠습니까?')) return;
    try {
      await unblockComment(comment.id);
      loadComments();
    } catch (error) {
      console.error('Failed to unblock comment:', error);
      alert('차단 해제에 실패했습니다.');
    }
  };

  const handleAdjustCommentLikes = async (comment: AdminComment, delta: 1 | -1) => {
    setLikeAdjustLoading(`comment-${comment.id}`);
    try {
      const result = await adjustCommentLikes(comment.id, delta);
      setComments(
        comments.map((c) => (c.id === comment.id ? { ...c, likeCount: result.likeCount } : c))
      );
    } catch (error) {
      console.error('Failed to adjust likes:', error);
      alert('좋아요 조정에 실패했습니다.');
    } finally {
      setLikeAdjustLoading(null);
    }
  };

  const handleContentCreated = () => {
    setCreateDialogOpen(false);
    loadPosts();
    loadComments();
  };

  const loading = postsLoading || commentsLoading;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <AdminPageHeader title="콘텐츠 관리" />
        {mainTab === 'published' && (
          <Button
            onClick={() => setCreateDialogOpen(true)}
            className="flex items-center gap-2"
          >
            <Plus className="h-4 w-4" />
            추가
          </Button>
        )}
      </div>

      <Tabs
        value={mainTab}
        onValueChange={(v) => setMainTab(v as 'published' | 'holding')}
        data-testid="admin-content-tabs"
      >
        <TabsList>
          <TabsTrigger value="published" data-testid="admin-content-tab-published">
            공개됨
          </TabsTrigger>
          <TabsTrigger value="holding" data-testid="admin-content-tab-holding">
            예약 홀딩
            {holdings.length > 0 && (
              <Badge variant="secondary" className="ml-2">
                {holdings.length}
              </Badge>
            )}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="published" className="space-y-6 mt-4">
      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex-1 min-w-[200px]">
          <label className="text-sm font-medium block mb-1">유형</label>
          <Select value={contentTypeFilter} onValueChange={setContentTypeFilter}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">전체</SelectItem>
              <SelectItem value="post">게시글</SelectItem>
              <SelectItem value="comment">댓글</SelectItem>
              <SelectItem value="reply">대댓글</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex-1 min-w-[200px]">
          <label className="text-sm font-medium block mb-1">작성자 유형</label>
          <Select value={authorTypeFilter} onValueChange={setAuthorTypeFilter}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">전체</SelectItem>
              <SelectItem value="AI">AI</SelectItem>
              <SelectItem value="USER">사람</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex-1 min-w-[200px]">
          <label className="text-sm font-medium block mb-1">상태</label>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">전체</SelectItem>
              <SelectItem value="ACTIVE">활성</SelectItem>
              <SelectItem value="BLOCKED">차단됨</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex-1 min-w-[200px]">
          <label className="text-sm font-medium block mb-1">카테고리</label>
          <Select value={categoryFilter} onValueChange={setCategoryFilter}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">전체</SelectItem>
              <SelectItem value="COUPLE">연인</SelectItem>
              <SelectItem value="MARRIED">부부</SelectItem>
              <SelectItem value="FRIEND">친구</SelectItem>
              <SelectItem value="FAMILY">가족</SelectItem>
              <SelectItem value="WORK">직장</SelectItem>
              <SelectItem value="OTHER">기타</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex-1 min-w-[200px]">
          <label className="text-sm font-medium block mb-1">검색</label>
          <Input
            placeholder="제목, 내용 검색..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Unified Table */}
      <div className="border rounded-lg overflow-hidden">
        <AdminTable<UnifiedContent>
          data={unifiedContent}
          loading={loading}
          columns={[
            {
              key: 'type',
              header: '유형',
              render: (row) => {
                const labels = {
                  post: '게시글',
                  comment: '댓글',
                  reply: '대댓글',
                };
                return <Badge variant="outline">{labels[row.type]}</Badge>;
              },
            },
            {
              key: 'content',
              header: '제목 / 본문',
              render: (row) => {
                if (row.type === 'post') {
                  const post = row.data as AdminPost;
                  return (
                    <span className="truncate max-w-xs" title={post.title}>
                      {post.title || '(제목 없음)'}
                    </span>
                  );
                } else {
                  const comment = row.data as AdminComment;
                  return (
                    <span className="truncate max-w-sm" title={comment.body}>
                      {comment.body || '(내용 없음)'}
                    </span>
                  );
                }
              },
            },
            {
              key: 'author',
              header: '작성자',
              render: (row) => {
                const data = row.type === 'post' ? (row.data as AdminPost) : (row.data as AdminComment);
                return (
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className="text-xs text-gray-600 truncate max-w-[80px]">
                      {data.authorId}
                    </span>
                    {data.synthetic && (
                      <Badge className="text-[10px] px-1 py-0 bg-purple-100 text-purple-700 border border-purple-200 font-normal">
                        AI
                      </Badge>
                    )}
                    {data.createdByAdmin && (
                      <Badge className="text-[10px] px-1 py-0 bg-blue-100 text-blue-700 border border-blue-200 font-normal">
                        관리자생성
                      </Badge>
                    )}
                  </div>
                );
              },
            },
            {
              key: 'category',
              header: '카테고리',
              render: (row) => {
                if (row.type === 'post') {
                  const post = row.data as AdminPost;
                  return (
                    <Badge variant="secondary">
                      {CATEGORY_LABELS[post.category] || post.category}
                    </Badge>
                  );
                }
                return <span className="text-xs text-gray-400">-</span>;
              },
            },
            {
              key: 'viewCount',
              header: '조회수',
              render: (row) => {
                if (row.type === 'post') {
                  const post = row.data as AdminPost;
                  return <span>{formatNumber(post.viewCount || 0)}</span>;
                }
                return <span className="text-xs text-gray-400">-</span>;
              },
            },
            {
              key: 'likes',
              header: '좋아요',
              render: (row) => {
                const data = row.type === 'post' ? (row.data as AdminPost) : (row.data as AdminComment);
                const likeCount = data.likeCount ?? 0;
                const isLoading = likeAdjustLoading === `${row.type === 'post' ? 'post' : 'comment'}-${row.id}`;
                return (
                  <div className="flex items-center gap-1.5">
                    <span>{formatNumber(likeCount)}</span>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0"
                        onClick={(e) => {
                          e.stopPropagation();
                          if (row.type === 'post') {
                            handleAdjustPostLikes(row.data as AdminPost, -1);
                          } else {
                            handleAdjustCommentLikes(row.data as AdminComment, -1);
                          }
                        }}
                        disabled={isLoading}
                      >
                        <Minus className="h-3 w-3" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0"
                        onClick={(e) => {
                          e.stopPropagation();
                          if (row.type === 'post') {
                            handleAdjustPostLikes(row.data as AdminPost, 1);
                          } else {
                            handleAdjustCommentLikes(row.data as AdminComment, 1);
                          }
                        }}
                        disabled={isLoading}
                      >
                        <Plus className="h-3 w-3" />
                      </Button>
                    </div>
                  </div>
                );
              },
            },
            {
              key: 'status',
              header: '상태',
              render: (row) => {
                const data = row.type === 'post' ? (row.data as AdminPost) : (row.data as AdminComment);
                if (row.type === 'post') {
                  return <Badge variant="outline">{(data as AdminPost).status}</Badge>;
                } else {
                  const statusInfo = COMMENT_STATUS_LABELS[data.status] || {
                    label: data.status,
                    variant: 'outline',
                  };
                  return <Badge variant={statusInfo.variant}>{statusInfo.label}</Badge>;
                }
              },
            },
            {
              key: 'createdAt',
              header: '작성일',
              render: (row) => {
                const data = row.type === 'post' ? (row.data as AdminPost) : (row.data as AdminComment);
                return (
                  <span className="text-xs text-gray-600">
                    {formatDate(data.createdAt)}
                  </span>
                );
              },
            },
            {
              key: 'actions',
              header: '액션',
              render: (row) => {
                const isPost = row.type === 'post';
                const post = isPost ? (row.data as AdminPost) : null;
                const comment = !isPost ? (row.data as AdminComment) : null;

                return (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                        <MoreVertical className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-48">
                      {isPost && post && (
                        <>
                          <DropdownMenuItem asChild>
                            <Link href={`/community/posts/${post.id}`} target="_blank">
                              <ExternalLink className="h-4 w-4 mr-2" />
                              상세보기
                            </Link>
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => setSelectedPost(post)}>
                            수정
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => setImprovePost(post)}
                            className="text-purple-600"
                          >
                            <Sparkles className="h-4 w-4 mr-2" />
                            AI 개선
                          </DropdownMenuItem>
                          {post.synthetic && (
                            <DropdownMenuItem asChild className="text-blue-600">
                              <Link href={`/admin/content/${post.id}/compare`}>
                                <GitCompare className="h-4 w-4 mr-2" />
                                원본 비교
                              </Link>
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem
                            onClick={() => setMarketingPostId(post.id)}
                            className="text-blue-600"
                          >
                            <Zap className="h-4 w-4 mr-2" />
                            마케팅 요청
                          </DropdownMenuItem>
                          {post.status !== 'BLOCKED' && (
                            <DropdownMenuItem onClick={() => handleBlockPost(post)}>
                              차단
                            </DropdownMenuItem>
                          )}
                          {post.status === 'BLOCKED' && (
                            <DropdownMenuItem onClick={() => handleUnblockPost(post)}>
                              차단 해제
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem
                            onClick={() => handleDeletePost(post)}
                            className="text-red-600"
                          >
                            삭제
                          </DropdownMenuItem>
                        </>
                      )}
                      {!isPost && comment && (
                        <>
                          <DropdownMenuItem onClick={() => setSelectedComment(comment)}>
                            수정
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => setImproveComment(comment)}
                            className="text-purple-600"
                          >
                            <Sparkles className="h-4 w-4 mr-2" />
                            AI 개선
                          </DropdownMenuItem>
                          {comment.status !== 'BLOCKED' && (
                            <DropdownMenuItem onClick={() => handleBlockComment(comment)}>
                              차단
                            </DropdownMenuItem>
                          )}
                          {comment.status === 'BLOCKED' && (
                            <DropdownMenuItem onClick={() => handleUnblockComment(comment)}>
                              차단 해제
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem
                            onClick={() => handleDeleteComment(comment)}
                            className="text-red-600"
                          >
                            삭제
                          </DropdownMenuItem>
                        </>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                );
              },
            },
          ]}
          rowKey={(row) => `${row.type}-${row.id}`}
        />
      </div>
        </TabsContent>

        <TabsContent value="holding" className="space-y-4 mt-4" data-testid="admin-scheduled-holding-panel">
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={loadHoldings} disabled={holdingsLoading}>
              새로고침
            </Button>
          </div>
          <div className="bg-white rounded-lg border">
            <AdminTable<ScheduledHoldingSummary>
              data={holdings}
              loading={holdingsLoading}
              emptyMessage="예약 홀딩된 글이 없습니다. 새벽 배치가 생성하면 여기에 표시됩니다."
              columns={[
                {
                  key: 'title',
                  header: '제목',
                  render: (row) => (
                    <button
                      type="button"
                      className="text-left font-medium text-blue-700 hover:underline"
                      onClick={() => setEditHoldingId(row.id)}
                      data-testid={`admin-scheduled-row-${row.id}`}
                    >
                      {row.title}
                    </button>
                  ),
                },
                {
                  key: 'personaId',
                  header: '페르소나',
                  render: (row) => <span className="font-mono text-xs">{row.personaId}</span>,
                },
                {
                  key: 'category',
                  header: '카테고리',
                  render: (row) => CATEGORY_LABELS[row.category || ''] || row.category || '—',
                },
                {
                  key: 'scheduledPublishAt',
                  header: '글 발행 예정 (KST)',
                  render: (row) =>
                    row.scheduledPublishAt
                      ? new Date(row.scheduledPublishAt).toLocaleString('ko-KR', {
                          timeZone: 'Asia/Seoul',
                          hour12: false,
                        })
                      : '—',
                },
                {
                  key: 'itemCount',
                  header: '댓글 후보',
                  render: (row) => row.itemCount,
                },
                {
                  key: 'status',
                  header: '상태',
                  render: (row) => (
                    <Badge variant={row.status === 'FAILED' ? 'destructive' : 'secondary'}>
                      {row.status}
                    </Badge>
                  ),
                },
                {
                  key: 'actions',
                  header: '액션',
                  render: (row) => (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => setEditHoldingId(row.id)}>
                          {row.status === 'SCHEDULED' ? '수정' : '보기'}
                        </DropdownMenuItem>
                        {row.status === 'SCHEDULED' && (
                          <DropdownMenuItem
                            className="text-red-600"
                            onClick={async () => {
                              if (!window.confirm(`「${row.title}」홀딩을 취소할까요?`)) return;
                              try {
                                await cancelScheduledHolding(row.id);
                                loadHoldings();
                              } catch (e) {
                                console.error(e);
                                alert('취소에 실패했습니다.');
                              }
                            }}
                          >
                            홀딩 취소
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>
        </TabsContent>
      </Tabs>

      {/* Dialogs */}
      <EditScheduledPostDialog
        holdingId={editHoldingId}
        onClose={() => setEditHoldingId(null)}
        onSaved={loadHoldings}
        onCancelled={loadHoldings}
      />
      <EditPostDialog
        post={selectedPost}
        onClose={() => setSelectedPost(null)}
        onUpdated={(updated) => {
          setPosts(posts.map((p) => (p.id === updated.id ? updated : p)));
          setSelectedPost(null);
        }}
      />
      <EditCommentDialog
        comment={selectedComment}
        onClose={() => setSelectedComment(null)}
        onUpdated={(updated) => {
          setComments(comments.map((c) => (c.id === updated.id ? updated : c)));
          setSelectedComment(null);
        }}
      />

      <AiImproveDialog
        post={improvePost}
        onClose={() => setImprovePost(null)}
        onCommitted={() => {
          setImprovePost(null);
          loadPosts();
        }}
      />

      <AiImproveDialog
        comment={improveComment}
        onClose={() => setImproveComment(null)}
        onCommitted={() => {
          setImproveComment(null);
          loadComments();
        }}
      />

      <CreateMarketingJobDialog
        postId={marketingPostId}
        onClose={() => setMarketingPostId(null)}
        onCreated={() => {
          setMarketingPostId(null);
          alert('마케팅 제작을 요청했습니다.');
        }}
      />

      <CreateContentDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        onCreated={handleContentCreated}
      />
    </div>
  );
}
