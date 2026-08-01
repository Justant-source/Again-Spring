'use client';

import { useState, useCallback, useEffect } from 'react';
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
import { EditScheduledPostDialog } from '@/components/admin/content/EditScheduledPostDialog';
import { EditPublishedThreadDialog } from '@/components/admin/content/EditPublishedThreadDialog';
import { CreateMarketingJobDialog } from '@/components/admin/content/CreateMarketingJobDialog';
import { CreateContentDialog } from '@/components/admin/content/CreateContentDialog';
import {
  listAdminPosts,
  getAdminPost,
  deletePost,
  blockPost,
  unblockPost,
  adjustPostLikes,
  listScheduledHoldings,
  cancelScheduledHolding,
  AdminPost,
  ScheduledHoldingSummary,
} from '@/lib/api/admin/content';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { AiImproveDialog } from '@/components/admin/content/AiImproveDialog';
import { formatNumber } from '@/lib/utils/adminFormat';
import { MoreVertical, ExternalLink, Sparkles, Zap, GitCompare, Plus, Minus } from 'lucide-react';

const CATEGORY_LABELS: Record<string, string> = {
  COUPLE: '연인',
  MARRIED: '부부',
  FRIEND: '친구',
  FAMILY: '가족',
  WORK: '직장',
  OTHER: '기타',
};

function formatKst(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
}

export default function AdminContentPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [authorTypeFilter, setAuthorTypeFilter] = useState('ALL');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const [posts, setPosts] = useState<AdminPost[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);

  const [improvePost, setImprovePost] = useState<AdminPost | null>(null);
  const [marketingPostId, setMarketingPostId] = useState<string | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [likeAdjustLoading, setLikeAdjustLoading] = useState<string | null>(null);
  const [editPublishedId, setEditPublishedId] = useState<string | null>(null);

  const [mainTab, setMainTab] = useState<'published' | 'holding'>('published');
  const [holdings, setHoldings] = useState<ScheduledHoldingSummary[]>([]);
  const [holdingsLoading, setHoldingsLoading] = useState(false);
  const [editHoldingId, setEditHoldingId] = useState<string | null>(null);

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

  useEffect(() => {
    loadPosts();
    loadHoldings();
  }, [loadPosts, loadHoldings]);

  useEffect(() => {
    if (mainTab === 'holding') loadHoldings();
  }, [mainTab, loadHoldings]);

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
      setPosts(posts.map((p) => (p.id === post.id ? { ...p, likeCount: result.likeCount } : p)));
    } catch (error) {
      console.error('Failed to adjust likes:', error);
      alert('좋아요 조정에 실패했습니다.');
    } finally {
      setLikeAdjustLoading(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <AdminPageHeader title="콘텐츠 관리" />
        {mainTab === 'published' && (
          <Button onClick={() => setCreateDialogOpen(true)} className="flex items-center gap-2">
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

        <TabsContent value="published" className="space-y-4 mt-4" data-testid="admin-published-panel">
          <div className="flex flex-wrap gap-3 items-end">
            <div className="flex-1 min-w-[160px]">
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
            <div className="flex-1 min-w-[160px]">
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

          <div className="bg-white rounded-lg border">
            <AdminTable<AdminPost>
              data={posts}
              loading={postsLoading}
              emptyMessage="공개된 글이 없습니다."
              columns={[
                {
                  key: 'title',
                  header: '제목',
                  render: (row) => (
                    <button
                      type="button"
                      className="text-left font-medium text-blue-700 hover:underline"
                      onClick={() => setEditPublishedId(row.id)}
                      data-testid={`admin-published-row-${row.id}`}
                    >
                      {row.title || '(제목 없음)'}
                    </button>
                  ),
                },
                {
                  key: 'author',
                  header: '작성자',
                  render: (row) => (
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="text-xs text-gray-600 font-mono truncate max-w-[100px]">
                        {row.authorId}
                      </span>
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
                  render: (row) => CATEGORY_LABELS[row.category] || row.category || '—',
                },
                {
                  key: 'createdAt',
                  header: '작성 시각 (KST)',
                  render: (row) => formatKst(row.createdAt),
                },
                {
                  key: 'commentCount',
                  header: '댓글',
                  render: (row) => row.commentCount ?? 0,
                },
                {
                  key: 'likes',
                  header: '좋아요',
                  render: (row) => (
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0"
                        disabled={likeAdjustLoading === `post-${row.id}`}
                        onClick={() => handleAdjustPostLikes(row, -1)}
                      >
                        <Minus className="h-3 w-3" />
                      </Button>
                      <span className="text-sm w-6 text-center">{formatNumber(row.likeCount ?? 0)}</span>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0"
                        disabled={likeAdjustLoading === `post-${row.id}`}
                        onClick={() => handleAdjustPostLikes(row, 1)}
                      >
                        <Plus className="h-3 w-3" />
                      </Button>
                    </div>
                  ),
                },
                {
                  key: 'status',
                  header: '상태',
                  render: (row) => (
                    <Badge variant={row.status === 'BLOCKED' ? 'destructive' : 'secondary'}>
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
                        <DropdownMenuItem onClick={() => setEditPublishedId(row.id)}>
                          스레드 수정
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link href={`/community/${row.id}`} target="_blank">
                            <ExternalLink className="h-4 w-4 mr-2" />
                            공개 보기
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setImprovePost(row)} className="text-purple-600">
                          <Sparkles className="h-4 w-4 mr-2" />
                          AI 개선
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link href={`/admin/content/${row.id}/compare`}>
                            <GitCompare className="h-4 w-4 mr-2" />
                            원본 비교
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setMarketingPostId(row.id)}>
                          <Zap className="h-4 w-4 mr-2" />
                          마케팅
                        </DropdownMenuItem>
                        {row.status !== 'BLOCKED' && (
                          <DropdownMenuItem onClick={() => handleBlockPost(row)}>차단</DropdownMenuItem>
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
                  render: (row) => formatKst(row.scheduledPublishAt),
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

      <EditPublishedThreadDialog
        postId={editPublishedId}
        onClose={() => setEditPublishedId(null)}
        onSaved={loadPosts}
        onDeleted={loadPosts}
      />
      <EditScheduledPostDialog
        holdingId={editHoldingId}
        onClose={() => setEditHoldingId(null)}
        onSaved={loadHoldings}
        onCancelled={loadHoldings}
      />

      <AiImproveDialog
        post={improvePost}
        onClose={() => setImprovePost(null)}
        onCommitted={() => {
          setImprovePost(null);
          loadPosts();
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
        onCreated={() => {
          setCreateDialogOpen(false);
          loadPosts();
        }}
      />
    </div>
  );
}
