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
import { anonymizeUser } from '@/lib/api/admin/users';
import { toast } from 'sonner';

interface AnonymizeUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: string;
  userName: string;
  onSuccess?: () => void;
}

export function AnonymizeUserDialog({
  open,
  onOpenChange,
  userId,
  userName,
  onSuccess,
}: AnonymizeUserDialogProps) {
  const [loading, setLoading] = useState(false);

  const handleConfirm = async () => {
    setLoading(true);
    try {
      await anonymizeUser(userId);
      toast.success(`${userName} 사용자를 익명화했습니다.`);
      onOpenChange(false);
      onSuccess?.();
    } catch (error) {
      toast.error('사용자 익명화에 실패했습니다.');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>사용자 익명화</DialogTitle>
          <DialogDescription>
            이 작업은 되돌릴 수 없습니다.
          </DialogDescription>
        </DialogHeader>

        <div className="bg-red-50 border border-red-200 rounded-md p-4 my-4">
          <p className="text-sm text-red-900 font-semibold mb-2">경고: 돌이킬 수 없는 작업</p>
          <ul className="text-sm text-red-800 space-y-1 ml-4 list-disc">
            <li>이메일 주소 삭제</li>
            <li>비밀번호 정보 삭제</li>
            <li>OAuth 계정 연결 해제</li>
            <li>닉네임을 "삭제된 사용자"로 변경</li>
          </ul>
        </div>

        <p className="text-sm text-gray-700">
          <strong>{userName}</strong>({userId})의 개인정보를 완전히 삭제하시겠습니까?
        </p>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            취소
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={loading}
          >
            {loading ? '처리 중…' : '익명화하기'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
