'use client';

import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { suspendUser } from '@/lib/api/admin/users';
import { toast } from 'sonner';

interface SuspendUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: string;
  userName: string;
  onSuccess?: () => void;
}

export function SuspendUserDialog({
  open,
  onOpenChange,
  userId,
  userName,
  onSuccess,
}: SuspendUserDialogProps) {
  const [loading, setLoading] = useState(false);
  const [reason, setReason] = useState('');
  const [suspendedUntil, setSuspendedUntil] = useState<string>('');

  const handleSubmit = async () => {
    if (!reason.trim()) {
      toast.error('정지 사유를 입력해주세요.');
      return;
    }

    setLoading(true);
    try {
      await suspendUser(userId, {
        reason,
        suspendedUntil: suspendedUntil || null,
      });
      toast.success(`${userName} 사용자를 정지했습니다.`);
      onOpenChange(false);
      setReason('');
      setSuspendedUntil('');
      onSuccess?.();
    } catch (error) {
      toast.error('사용자 정지에 실패했습니다.');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>사용자 정지</DialogTitle>
          <DialogDescription>
            {userName}({userId})를 정지합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="reason">정지 사유 *</Label>
            <Input
              id="reason"
              placeholder="예: 규칙 위반"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="suspended-until">정지 종료일 (선택사항)</Label>
            <Input
              id="suspended-until"
              type="date"
              value={suspendedUntil}
              onChange={(e) => setSuspendedUntil(e.target.value)}
              disabled={loading}
            />
            <p className="text-xs text-gray-500">
              입력하지 않으면 무기한 정지됩니다.
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={loading}>
            {loading ? '처리 중…' : '정지하기'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
