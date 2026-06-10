'use client';

import { useEffect, useState } from 'react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { PickerPost, listAdminPostsForPicker } from '@/lib/api/admin/marketing';

interface PostPickerDialogProps {
  open: boolean;
  onClose: () => void;
  onSelect: (postId: string) => void;
}

export function PostPickerDialog({
  open,
  onClose,
  onSelect,
}: PostPickerDialogProps) {
  const [posts, setPosts] = useState<PickerPost[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    loadPosts();
  }, [open]);

  const loadPosts = async () => {
    setLoading(true);
    try {
      const data = await listAdminPostsForPicker(0);
      setPosts(data);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  };

  const filteredPosts = posts.filter((post) =>
    post.title.toLowerCase().includes(search.toLowerCase())
  );

  const handleSelect = () => {
    if (selectedPostId) {
      onSelect(selectedPostId);
      onClose();
      setSelectedPostId(null);
      setSearch('');
    }
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>사연 선택</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div>
            <label className="text-sm font-medium text-gray-700 block mb-2">
              제목으로 검색
            </label>
            <Input
              placeholder="검색..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="text-sm"
            />
          </div>

          <div className="border rounded-lg overflow-hidden">
            {loading ? (
              <div className="p-4 text-center text-gray-400 text-sm">
                로드 중...
              </div>
            ) : filteredPosts.length === 0 ? (
              <div className="p-4 text-center text-gray-400 text-sm">
                {posts.length === 0 ? '게시글이 없어요.' : '검색 결과가 없어요.'}
              </div>
            ) : (
              <div className="max-h-60 overflow-y-auto">
                {filteredPosts.map((post) => (
                  <button
                    key={post.id}
                    onClick={() => setSelectedPostId(post.id)}
                    className={`w-full p-3 border-b text-left hover:bg-blue-50 transition-colors ${
                      selectedPostId === post.id ? 'bg-blue-100' : ''
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-800 truncate">
                          {post.title}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">
                          {post.authorNickname || '익명'} · {post.createdAt}
                        </p>
                      </div>
                      <div className="flex gap-1 text-xs whitespace-nowrap">
                        <Badge variant="outline">{post.voteCount}</Badge>
                        <Badge variant="outline">{post.commentCount}</Badge>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button
            onClick={handleSelect}
            disabled={!selectedPostId}
          >
            선택
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
