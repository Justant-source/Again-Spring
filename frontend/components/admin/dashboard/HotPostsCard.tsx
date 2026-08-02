'use client';

import { HotPostDto } from '@/lib/api/admin/dashboard';
import { Badge } from '@/components/ui/badge';
import { MessageCircle, Heart, Eye, Zap } from 'lucide-react';

interface HotPostsCardProps {
  posts: HotPostDto[];
  loading?: boolean;
}

export function HotPostsCard({ posts, loading }: HotPostsCardProps) {
  if (loading) {
    return (
      <div className="p-6 bg-white rounded-lg border" data-testid="admin-hot-posts">
        <div className="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-12 bg-gray-200 rounded animate-pulse"></div>
          ))}
        </div>
      </div>
    );
  }

  if (posts.length === 0) {
    return (
      <div className="p-6 bg-white rounded-lg border" data-testid="admin-hot-posts">
        <h2 className="text-sm font-semibold text-gray-900 mb-4">핫 게시글</h2>
        <p className="text-sm text-gray-500">최근 핫 게시글이 없어요.</p>
      </div>
    );
  }

  return (
    <div className="p-6 bg-white rounded-lg border" data-testid="admin-hot-posts">
      <h2 className="text-sm font-semibold text-gray-900 mb-4">핫 게시글</h2>
      <div className="space-y-3">
        {posts.map((post, index) => (
          <div key={post.id} className="flex items-start gap-3 pb-3 border-b last:border-b-0">
            {/* Rank */}
            <div className="text-xs font-semibold text-gray-400 w-6 flex-shrink-0 pt-0.5">
              #{index + 1}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <div className="flex items-start gap-2">
                <a
                  href={`/community/${post.id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-sm font-medium text-blue-600 hover:text-blue-800 underline line-clamp-2 flex-1"
                >
                  {post.title}
                </a>
                {post.synthetic && (
                  <Badge variant="secondary" className="text-xs flex-shrink-0 mt-0.5">
                    AI
                  </Badge>
                )}
              </div>

              {/* Stats */}
              <div className="flex items-center gap-3 mt-1.5 text-xs text-gray-500">
                <span className="flex items-center gap-1">
                  <Heart size={12} className="text-red-500" />
                  {post.voteCount}
                </span>
                <span className="flex items-center gap-1">
                  <MessageCircle size={12} className="text-blue-500" />
                  {post.commentCount}
                </span>
                <span className="flex items-center gap-1">
                  <Eye size={12} className="text-gray-500" />
                  {post.viewCount}
                </span>
                <span className="flex items-center gap-1">
                  <Zap size={12} className="text-amber-500" />
                  {post.score.toFixed(1)}
                </span>
              </div>
            </div>

            {/* Content Manage Link */}
            <a
              href={`/admin/content?postId=${post.id}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-gray-400 hover:text-gray-600 underline flex-shrink-0"
            >
              관리
            </a>
          </div>
        ))}
      </div>
    </div>
  );
}
