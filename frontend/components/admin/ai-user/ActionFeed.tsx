'use client';

import { useState, useEffect, useCallback } from 'react';
import { RefreshControl } from '@/components/admin/RefreshControl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  PenLine, MessageCircle, CornerDownRight, Vote, Heart, ExternalLink,
} from 'lucide-react';
import {
  getActionFeed,
  type ActionFeedItem,
} from '@/lib/api/admin/ai-user';

type StatusFilter = 'all' | 'FAILED' | 'BLOCKED';

export function ActionFeed({ className }: { className?: string }) {
  const [feeds, setFeeds] = useState<ActionFeedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [autoRefresh, setAutoRefresh] = useState(true);

  const fetchFeeds = useCallback(async () => {
    setLoading(true);
    try {
      const status = statusFilter === 'all' ? undefined : statusFilter;
      const result = await getActionFeed(50, status);
      setFeeds(result.feeds);
    } catch (e) {
      console.error('Failed to fetch action feeds:', e);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchFeeds();
  }, [fetchFeeds]);

  useEffect(() => {
    if (!autoRefresh) return;
    const id = setInterval(fetchFeeds, 30_000);
    return () => clearInterval(id);
  }, [autoRefresh, fetchFeeds]);

  const getActionIcon = (action: string) => {
    switch (action) {
      case 'POST':
        return <PenLine className="h-4 w-4 text-blue-600" />;
      case 'COMMENT':
        return <MessageCircle className="h-4 w-4 text-green-600" />;
      case 'REPLY':
        return <CornerDownRight className="h-4 w-4 text-purple-600" />;
      case 'VOTE':
        return <Vote className="h-4 w-4 text-orange-600" />;
      case 'LIKE':
        return <Heart className="h-4 w-4 text-red-600" />;
      default:
        return null;
    }
  };

  const getStatusBadge = (status: string, failed: boolean, blocked: boolean) => {
    if (blocked) {
      return <Badge className="bg-orange-100 text-orange-700 border-orange-200 text-xs">차단됨</Badge>;
    }
    if (failed) {
      return <Badge className="bg-red-100 text-red-700 border-red-200 text-xs">실패</Badge>;
    }
    return <Badge className="bg-green-100 text-green-700 border-green-200 text-xs">완료</Badge>;
  };

  const getTierBadge = (tier: string | null) => {
    if (!tier) return null;
    const colors: Record<string, string> = {
      HEAVY: 'bg-purple-100 text-purple-700 border-purple-200',
      STANDARD: 'bg-blue-100 text-blue-700 border-blue-200',
      LIGHT: 'bg-gray-100 text-gray-700 border-gray-200',
    };
    return <Badge className={`${colors[tier] || colors.STANDARD} text-xs`}>{tier}</Badge>;
  };

  const getRelativeTime = (isoTime: string) => {
    try {
      const now = new Date();
      const then = new Date(isoTime);
      const diff = Math.floor((now.getTime() - then.getTime()) / 1000);
      if (diff < 60) return '방금 전';
      if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
      if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
      return `${Math.floor(diff / 86400)}일 전`;
    } catch {
      return '시간 불명';
    }
  };

  const parseDetail = (detail: string | null) => {
    if (!detail) return { backend: null, error: null };
    try {
      const obj = JSON.parse(detail);
      return {
        backend: obj.backend || null,
        error: obj.error || null,
      };
    } catch {
      return { backend: null, error: null };
    }
  };

  const filteredFeeds = feeds;

  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-6 ${className || ''}`} data-testid="ai-action-feed">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-800">실시간 행동 피드</h3>
        <RefreshControl
          onRefresh={fetchFeeds}
          loading={loading}
          autoRefreshSeconds={30}
        />
      </div>

      {/* 필터 버튼 */}
      <div className="flex gap-2 mb-4">
        {(['all', 'FAILED', 'BLOCKED'] as StatusFilter[]).map(filter => (
          <Button
            key={filter}
            variant={statusFilter === filter ? 'default' : 'outline'}
            size="sm"
            onClick={() => setStatusFilter(filter)}
            className="text-xs"
          >
            {filter === 'all' ? '전체' : filter === 'FAILED' ? '실패' : '차단'}
          </Button>
        ))}
      </div>

      {/* 행동 리스트 */}
      <div className="max-h-96 overflow-y-auto space-y-2">
        {filteredFeeds.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">
            {loading ? '불러오는 중...' : '최근 행동 없음'}
          </p>
        ) : (
          filteredFeeds.map(item => {
            const detail = parseDetail(item.detail);
            return (
              <div
                key={item.id}
                className="flex items-start gap-3 p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors"
              >
                <div className="shrink-0 pt-1">{getActionIcon(item.action)}</div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-medium text-gray-800">
                      {item.personaNickname || item.personaId}
                    </span>
                    {item.personaTier && getTierBadge(item.personaTier)}
                    {getStatusBadge(item.status, item.failed, item.blocked)}
                  </div>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs text-gray-500">
                      {item.action}
                    </span>
                    {item.targetId && (
                      <a
                        href={`/community/${item.targetId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs text-blue-600 hover:text-blue-700 flex items-center gap-1"
                      >
                        {item.targetType || '게시글'} ID: {item.targetId.slice(0, 8)}...
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    )}
                  </div>
                  {(detail.backend || detail.error) && (
                    <div className="text-[11px] text-gray-400 mt-1 space-y-0.5">
                      {detail.backend && <div>백엔드: {detail.backend}</div>}
                      {detail.error && <div>오류: {detail.error}</div>}
                    </div>
                  )}
                  <div className="text-[11px] text-gray-400 mt-1">
                    {getRelativeTime(item.createdAt)}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
