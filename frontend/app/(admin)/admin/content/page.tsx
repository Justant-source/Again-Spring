'use client';

import { useState, useCallback, useEffect } from 'react';
import Link from 'next/link';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { EditPostDialog } from '@/components/admin/content/EditPostDialog';
import { EditCommentDialog } from '@/components/admin/content/EditCommentDialog';
import {
  listAdminPosts,
  listAdminComments,
  deletePost,
  blockPost,
  unblockPost,
  deleteComment,
  blockComment,
  unblockComment,
  AdminPost,
  AdminComment,
} from '@/lib/api/admin/content';
import { AdminSection } from '@/components/admin/AdminSection';
import { AiImproveDialog } from '@/components/admin/content/AiImproveDialog';
import { MoreVertical, ExternalLink, Sparkles } from 'lucide-react';

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

export default function AdminContentPage() {
  const [activeTab, setActiveTab] = useState<'posts' | 'comments'>('posts');

  // Posts state
  const [posts, setPosts] = useState<AdminPost[]>([]);
  const [postsPage, setPostsPage] = useState(0);
  const [postsTotalPages, setPostsTotalPages] = useState(0);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postAuthorTypeFilter, setPostAuthorTypeFilter] = useState('ALL');
  const [postCategoryFilter, setPostCategoryFilter] = useState('');
  const [postSearchQuery, setPostSearchQuery] = useState('');
  const [selectedPost, setSelectedPost] = useState<AdminPost | null>(null);
  const [improvePost, setImprovePost] = useState<AdminPost | null>(null);

  // Comments state
  const [comments, setComments] = useState<AdminComment[]>([]);
  const [commentsPage, setCommentsPage] = useState(0);
  const [commentsTotalPages, setCommentsTotalPages] = useState(0);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentStatusFilter, setCommentStatusFilter] = useState('ACTIVE');
  const [commentSearchQuery, setCommentSearchQuery] = useState('');
  const [selectedComment, setSelectedComment] = useState<AdminComment | null>(null);
  const [improveComment, setImproveComment] = useState<AdminComment | null>(null);

  // Load posts
  const loadPosts = useCallback(
    async (page: number) => {
      setPostsLoading(true);
      try {
        const res = await listAdminPosts({
          page,
          size: 20,
          synthetic:
            postAuthorTypeFilter === 'AI'
              ? true
              : postAuthorTypeFilter === 'USER'
              ? false
              : undefined,
          category: postCategoryFilter === 'ALL' ? undefined : postCategoryFilter || undefined,
          search: postSearchQuery || undefined,
        });
        setPosts(res.content);
        setPostsTotalPages(res.totalPages);
        setPostsPage(page);
      } catch (error) {
        console.error('Failed to load posts:', error);
      } finally {
        setPostsLoading(false);
      }
    },
    [postAuthorTypeFilter, postCategoryFilter, postSearchQuery]
  );

  // Load comments
  const loadComments = useCallback(
    async (page: number) => {
      setCommentsLoading(true);
      try {
        const res = await listAdminComments({
          page,
          size: 20,
          status: commentStatusFilter === 'ALL' ? undefined : commentStatusFilter,
          search: commentSearchQuery || undefined,
        });
        setComments(res.content);
        setCommentsTotalPages(res.totalPages);
        setCommentsPage(page);
      } catch (error) {
        console.error('Failed to load comments:', error);
      } finally {
        setCommentsLoading(false);
      }
    },
    [commentStatusFilter, commentSearchQuery]
  );

  // Load on mount and when filters change
  useEffect(() => {
    loadPosts(0);
  }, [postAuthorTypeFilter, postCategoryFilter, postSearchQuery, loadPosts]);

  useEffect(() => {
    loadComments(0);
  }, [commentStatusFilter, commentSearchQuery, loadComments]);

  // Post actions
  const handleDeletePost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"을(를) 삭제하시겠습니까?`)) return;
    try {
      await deletePost(post.id);
      loadPosts(postsPage);
    } catch (error) {
      console.error('Failed to delete post:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  const handleBlockPost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"을(를) 차단하시겠습니까?`)) return;
    try {
      await blockPost(post.id);
      loadPosts(postsPage);
    } catch (error) {
      console.error('Failed to block post:', error);
      alert('차단에 실패했습니다.');
    }
  };

  const handleUnblockPost = async (post: AdminPost) => {
    if (!window.confirm(`게시글 "${post.title}"의 차단을 해제하시겠습니까?`)) return;
    try {
      await unblockPost(post.id);
      loadPosts(postsPage);
    } catch (error) {
      console.error('Failed to unblock post:', error);
      alert('차단 해제에 실패했습니다.');
    }
  };

  // Comment actions
  const handleDeleteComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
    try {
      await deleteComment(comment.id);
      loadComments(commentsPage);
    } catch (error) {
      console.error('Failed to delete comment:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  const handleBlockComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글을 차단하시겠습니까?')) return;
    try {
      await blockComment(comment.id);
      loadComments(commentsPage);
    } catch (error) {
      console.error('Failed to block comment:', error);
      alert('차단에 실패했습니다.');
    }
  };

  const handleUnblockComment = async (comment: AdminComment) => {
    if (!window.confirm('댓글의 차단을 해제하시겠습니까?')) return;
    try {
      await unblockComment(comment.id);
      loadComments(commentsPage);
    } catch (error) {
      console.error('Failed to unblock comment:', error);
      alert('차단 해제에 실패했습니다.');
    }
  };

  return (
    <AdminSection title="콘텐츠 관리">
      <Tabs value={activeTab} onValueChange={(v: any) => setActiveTab(v)}>
        <TabsList>
          <TabsTrigger value="posts">게시글</TabsTrigger>
          <TabsTrigger value="comments">댓글·대댓글</TabsTrigger>
        </TabsList>

        {/* Posts Tab */}
        <TabsContent value="posts" className="space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap gap-3 items-end">
            <div className="flex-1 min-w-[200px]">
              <label className="text-sm font-medium block mb-1">작성자 유형</label>
              <Select value={postAuthorTypeFilter} onValueChange={setPostAuthorTypeFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">전체</SelectItem>
                  <SelectItem value="AI">AI 유저</SelectItem>
                  <SelectItem value="USER">일반 유저</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex-1 min-w-[200px]">
              <label className="text-sm font-medium block mb-1">카테고리</label>
              <Select value={postCategoryFilter} onValueChange={setPostCategoryFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="전체" />
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
              <label className="text-sm font-medium block mb-1">검색 (제목)</label>
              <Input
                placeholder="제목 검색..."
                value={postSearchQuery}
                onChange={(e) => setPostSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {/* Table */}
          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AdminPost>
              data={posts}
              loading={postsLoading}
              columns={[
                {
                  key: 'title',
                  header: '제목',
                  render: (row) => (
                    <span className="truncate max-w-xs" title={row.title}>
                      {row.title || '(제목 없음)'}
                    </span>
                  ),
                },
                {
                  key: 'authorId',
                  header: '작성자',
                  render: (row) => (
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs text-gray-600 truncate max-w-[80px]">{row.authorId}</span>
                      {row.synthetic && (
                        <Badge className="text-[10px] px-1 py-0 bg-purple-100 text-purple-700 border border-purple-200 font-normal">
                          AI
                        </Badge>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'category',
                  header: '카테고리',
                  render: (row) => (
                    <Badge variant="secondary">
                      {CATEGORY_LABELS[row.category] || row.category}
                    </Badge>
                  ),
                },
                {
                  key: 'viewCount',
                  header: '조회수',
                  render: (row) => <span>{row.viewCount || 0}</span>,
                },
                {
                  key: 'createdAt',
                  header: '등록일',
                  render: (row) => (
                    <span className="text-xs text-gray-600">
                      {new Date(row.createdAt).toLocaleDateString('ko-KR')}
                    </span>
                  ),
                },
                {
                  key: 'actions',
                  header: '액션',
                  render: (row) => (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-48">
                        <DropdownMenuItem asChild>
                          <Link href={`/community/posts/${row.id}`} target="_blank">
                            <ExternalLink className="h-4 w-4 mr-2" />
                            상세보기
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setSelectedPost(row)}>
                          수정
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => setImprovePost(row)}
                          className="text-purple-600"
                        >
                          <Sparkles className="h-4 w-4 mr-2" />
                          AI 개선{row.synthetic ? '' : ' (학습 데이터)'}
                        </DropdownMenuItem>
                        {row.status !== 'BLOCKED' && (
                          <DropdownMenuItem onClick={() => handleBlockPost(row)}>
                            차단
                          </DropdownMenuItem>
                        )}
                        {row.status === 'BLOCKED' && (
                          <DropdownMenuItem onClick={() => handleUnblockPost(row)}>
                            차단 해제
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem
                          onClick={() => handleDeletePost(row)}
                          className="text-red-600"
                        >
                          삭제
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>

          {/* Pagination */}
          <AdminPagination
            page={postsPage}
            totalPages={postsTotalPages}
            onPageChange={(page) => loadPosts(page)}
          />
        </TabsContent>

        {/* Comments Tab */}
        <TabsContent value="comments" className="space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap gap-3 items-end">
            <div className="flex-1 min-w-[200px]">
              <label className="text-sm font-medium block mb-1">상태</label>
              <Select value={commentStatusFilter} onValueChange={setCommentStatusFilter}>
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
              <label className="text-sm font-medium block mb-1">검색 (내용)</label>
              <Input
                placeholder="내용 검색..."
                value={commentSearchQuery}
                onChange={(e) => setCommentSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {/* Table */}
          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AdminComment>
              data={comments}
              loading={commentsLoading}
              columns={[
                {
                  key: 'body',
                  header: '내용',
                  render: (row) => (
                    <span className="truncate max-w-sm" title={row.body}>
                      {row.body || '(내용 없음)'}
                    </span>
                  ),
                },
                {
                  key: 'authorId',
                  header: '작성자',
                  render: (row) => (
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs text-gray-600 truncate max-w-[80px]">{row.authorId}</span>
                      {row.synthetic && (
                        <Badge className="text-[10px] px-1 py-0 bg-purple-100 text-purple-700 border border-purple-200 font-normal">
                          AI
                        </Badge>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'postId',
                  header: '게시글 ID',
                  render: (row) => (
                    <Link href={`/community/posts/${row.postId}`} target="_blank">
                      <span className="text-xs text-blue-600 hover:underline cursor-pointer">
                        {row.postId}
                      </span>
                    </Link>
                  ),
                },
                {
                  key: 'parentCommentId',
                  header: '대댓글',
                  render: (row) =>
                    row.parentCommentId ? (
                      <span className="text-sm">✓</span>
                    ) : (
                      <span className="text-xs text-gray-400">-</span>
                    ),
                },
                {
                  key: 'status',
                  header: '상태',
                  render: (row) => {
                    const statusInfo = COMMENT_STATUS_LABELS[row.status] || {
                      label: row.status,
                      variant: 'outline',
                    };
                    return <Badge variant={statusInfo.variant}>{statusInfo.label}</Badge>;
                  },
                },
                {
                  key: 'createdAt',
                  header: '등록일',
                  render: (row) => (
                    <span className="text-xs text-gray-600">
                      {new Date(row.createdAt).toLocaleDateString('ko-KR')}
                    </span>
                  ),
                },
                {
                  key: 'actions',
                  header: '액션',
                  render: (row) => (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-48">
                        <DropdownMenuItem onClick={() => setSelectedComment(row)}>
                          수정
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => setImproveComment(row)}
                          className="text-purple-600"
                        >
                          <Sparkles className="h-4 w-4 mr-2" />
                          AI 개선{row.synthetic ? '' : ' (학습 데이터)'}
                        </DropdownMenuItem>
                        {row.status !== 'BLOCKED' && (
                          <DropdownMenuItem onClick={() => handleBlockComment(row)}>
                            차단
                          </DropdownMenuItem>
                        )}
                        {row.status === 'BLOCKED' && (
                          <DropdownMenuItem onClick={() => handleUnblockComment(row)}>
                            차단 해제
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem
                          onClick={() => handleDeleteComment(row)}
                          className="text-red-600"
                        >
                          삭제
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>

          {/* Pagination */}
          <AdminPagination
            page={commentsPage}
            totalPages={commentsTotalPages}
            onPageChange={(page) => loadComments(page)}
          />
        </TabsContent>
      </Tabs>

      {/* Dialogs */}
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

      {/* AI 개선 다이얼로그 — 게시글 */}
      <AiImproveDialog
        post={improvePost}
        onClose={() => setImprovePost(null)}
        onCommitted={() => {
          setImprovePost(null);
          loadPosts(postsPage);
        }}
      />

      {/* AI 개선 다이얼로그 — 댓글 */}
      <AiImproveDialog
        comment={improveComment}
        onClose={() => setImproveComment(null)}
        onCommitted={() => {
          setImproveComment(null);
          loadComments(commentsPage);
        }}
      />
    </AdminSection>
  );
}
