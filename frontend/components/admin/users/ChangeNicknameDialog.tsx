'use client';

import { useState, useEffect } from 'react';
import { changeNickname } from '@/lib/api/admin/users';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { AlertCircle } from 'lucide-react';

interface Props {
  userId: string | null;
  currentNickname?: string;
  onClose: () => void;
  onChanged: (newNickname: string) => void;
}

export function ChangeNicknameDialog({ userId, currentNickname, onClose, onChanged }: Props) {
  const [nickname, setNickname] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (userId) {
      setNickname(currentNickname || '');
      setError('');
    }
  }, [userId, currentNickname]);

  if (!userId) return null;

  async function handleSave() {
    if (!nickname.trim()) {
      setError('닉네임을 입력해주세요.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const result = await changeNickname(userId!, nickname.trim());
      onChanged(result.newNickname);
      onClose();
    } catch (err: any) {
      const msg = err?.response?.data?.message || '닉네임 변경에 실패했습니다.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={!!userId} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md bg-white">
        <DialogHeader>
          <DialogTitle>닉네임 강제 변경</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {error && (
            <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-md">
              <AlertCircle className="w-4 h-4 text-red-600 mt-0.5 shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="nickname-input">새 닉네임</Label>
            <Input
              id="nickname-input"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="새 닉네임 입력"
              disabled={submitting}
              onKeyDown={(e) => e.key === 'Enter' && handleSave()}
              className="bg-white"
            />
            <p className="text-xs text-gray-500">
              현재: <span className="font-medium">{currentNickname || '(없음)'}</span>
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleSave} disabled={submitting || !nickname.trim()}>
            {submitting ? '변경 중...' : '변경'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
