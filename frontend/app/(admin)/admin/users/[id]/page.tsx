'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { getAdminUserDetail, updateUserRoles } from '@/lib/api/admin';
import {
  suspendUser,
  unsuspendUser,
  forceLogoutUser,
  anonymizeUser,
  type AdminUserListItem,
} from '@/lib/api/admin/users';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AdminSection } from '@/components/admin/AdminSection';
import { SuspendUserDialog } from '@/components/admin/users/SuspendUserDialog';
import { AnonymizeUserDialog } from '@/components/admin/users/AnonymizeUserDialog';
import { toast } from 'sonner';

interface AdminUserDetail {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  mbtiType?: string;
  communicationStyle?: string;
  provider?: string;
  roles?: string[];
  createdAt?: string;
  deletedAt?: string;
  onboardingCompletedAt?: string;
  termsAgreedAt?: string;
  privacyAgreedAt?: string;
  disclaimerAgreedAt?: string;
  marketingAgreedAt?: string;
  totalSessions: number;
  completedSessions: number;
  feedbackCount: number;
  lastSessionAt?: string;
  status?: string;
  suspendedUntil?: string | null;
  suspendedReason?: string | null;
}

export default function UserDetailPage() {
  const params = useParams();
  const router = useRouter();
  const userId = params.id as string;

  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<AdminUserDetail | null>(null);
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
  const [anonymizeDialogOpen, setAnonymizeDialogOpen] = useState(false);

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const data = await getAdminUserDetail(userId);
        setUser(data as AdminUserDetail);
      } catch (error) {
        toast.error('사용자 정보를 불러올 수 없습니다.');
        console.error(error);
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, [userId]);

  if (loading) {
    return <div className="text-center py-12">로딩 중…</div>;
  }

  if (!user) {
    return (
      <div className="text-center py-12">
        <p>사용자를 찾을 수 없습니다.</p>
        <Link href="/admin/users">
          <Button variant="outline" className="mt-4">
            목록으로 돌아가기
          </Button>
        </Link>
      </div>
    );
  }

  const handleChangeRole = async () => {
    if (!user) return;
    const currentRoles = user.roles || [];
    const isTester = currentRoles.includes('TESTER');
    const newRoles = isTester
      ? currentRoles.filter((r) => r !== 'TESTER')
      : [...currentRoles, 'TESTER'];

    try {
      await updateUserRoles(user.id, newRoles);
      setUser({ ...user, roles: newRoles });
      toast.success('역할이 변경되었습니다.');
    } catch (error) {
      toast.error('역할 변경에 실패했습니다.');
      console.error(error);
    }
  };

  const handleSuspend = () => {
    setSuspendDialogOpen(true);
  };

  const handleUnsuspend = async () => {
    try {
      await unsuspendUser(userId);
      setUser({ ...user, status: 'ACTIVE', suspendedUntil: null, suspendedReason: null });
      toast.success('사용자 정지를 해제했습니다.');
    } catch (error) {
      toast.error('정지 해제에 실패했습니다.');
      console.error(error);
    }
  };

  const handleForceLogout = async () => {
    try {
      await forceLogoutUser(userId);
      toast.success('사용자를 강제 로그아웃했습니다.');
    } catch (error) {
      toast.error('강제 로그아웃에 실패했습니다.');
      console.error(error);
    }
  };

  const handleAnonymize = () => {
    setAnonymizeDialogOpen(true);
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge className="bg-green-50 text-green-700">활성</Badge>;
      case 'SUSPENDED':
        return <Badge variant="destructive">정지됨</Badge>;
      default:
        return <Badge variant="outline">{status || 'ACTIVE'}</Badge>;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link href="/admin/users">
            <Button variant="outline" size="sm">
              ← 목록으로
            </Button>
          </Link>
          <div>
            <h1 className="text-2xl font-bold">{user.nickname}</h1>
            <p className="text-gray-600">{user.email || '—'}</p>
          </div>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">액션</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={handleChangeRole}>
              역할 변경 (TESTER)
            </DropdownMenuItem>
            {user.status === 'ACTIVE' ? (
              <DropdownMenuItem onClick={handleSuspend}>정지</DropdownMenuItem>
            ) : (
              <DropdownMenuItem onClick={handleUnsuspend}>정지 해제</DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={handleForceLogout}>강제로그아웃</DropdownMenuItem>
            <DropdownMenuItem onClick={handleAnonymize} className="text-red-600">
              익명화
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 기본 정보 */}
      <AdminSection title="기본 정보">
        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="text-sm font-medium text-gray-600">ID</label>
            <p className="text-sm text-gray-900 font-mono">{user.id}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">닉네임</label>
            <p className="text-sm text-gray-900">{user.nickname}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">이메일</label>
            <p className="text-sm text-gray-900">{user.email || '—'}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">계정 타입</label>
            <p className="text-sm text-gray-900">{user.isGuest ? '게스트' : '회원'}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">OAuth 제공자</label>
            <p className="text-sm text-gray-900">{user.provider || '—'}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">상태</label>
            <div className="mt-1">{getStatusBadge(user.status)}</div>
          </div>
        </div>
      </AdminSection>

      {/* 역할 */}
      <AdminSection title="역할">
        <div className="flex gap-2 flex-wrap">
          {user.roles && user.roles.length > 0 ? (
            user.roles.map((role) => (
              <Badge key={role} variant="secondary">
                {role}
              </Badge>
            ))
          ) : (
            <p className="text-sm text-gray-600">역할 없음</p>
          )}
        </div>
      </AdminSection>

      {/* 정지 상태 */}
      {user.status === 'SUSPENDED' && (
        <AdminSection title="정지 상태">
          <div className="grid gap-4">
            <div>
              <label className="text-sm font-medium text-gray-600">정지 사유</label>
              <p className="text-sm text-gray-900 mt-1">{user.suspendedReason || '—'}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">정지 종료일</label>
              <p className="text-sm text-gray-900 mt-1">
                {user.suspendedUntil
                  ? new Date(user.suspendedUntil).toLocaleString('ko-KR')
                  : '무기한'}
              </p>
            </div>
          </div>
        </AdminSection>
      )}

      {/* 프로필 정보 */}
      <AdminSection title="프로필 정보">
        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="text-sm font-medium text-gray-600">MBTI</label>
            <p className="text-sm text-gray-900">{user.mbtiType || '—'}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">커뮤니케이션 스타일</label>
            <p className="text-sm text-gray-900">{user.communicationStyle || '—'}</p>
          </div>
        </div>
      </AdminSection>

      {/* 가입 및 동의 정보 */}
      <AdminSection title="가입 및 동의">
        <div className="grid grid-cols-2 gap-6">
          <div>
            <label className="text-sm font-medium text-gray-600">가입일</label>
            <p className="text-sm text-gray-900">
              {user.createdAt ? new Date(user.createdAt).toLocaleString('ko-KR') : '—'}
            </p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">온보딩 완료</label>
            <p className="text-sm text-gray-900">
              {user.onboardingCompletedAt
                ? new Date(user.onboardingCompletedAt).toLocaleString('ko-KR')
                : '미완료'}
            </p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">약관 동의</label>
            <p className="text-sm text-gray-900">
              {user.termsAgreedAt ? new Date(user.termsAgreedAt).toLocaleDateString('ko-KR') : '미동의'}
            </p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">개인정보 동의</label>
            <p className="text-sm text-gray-900">
              {user.privacyAgreedAt
                ? new Date(user.privacyAgreedAt).toLocaleDateString('ko-KR')
                : '미동의'}
            </p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">면책 동의</label>
            <p className="text-sm text-gray-900">
              {user.disclaimerAgreedAt
                ? new Date(user.disclaimerAgreedAt).toLocaleDateString('ko-KR')
                : '미동의'}
            </p>
          </div>
          <div>
            <label className="text-sm font-medium text-gray-600">마케팅 동의</label>
            <p className="text-sm text-gray-900">
              {user.marketingAgreedAt
                ? new Date(user.marketingAgreedAt).toLocaleDateString('ko-KR')
                : '미동의'}
            </p>
          </div>
        </div>
      </AdminSection>

      {/* 활동 통계 */}
      <AdminSection title="활동 통계">
        <div className="grid grid-cols-3 gap-6">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">총 세션</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{user.totalSessions}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">완료 세션</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{user.completedSessions}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">의견 수</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{user.feedbackCount}</p>
            </CardContent>
          </Card>
        </div>
      </AdminSection>

      <SuspendUserDialog
        open={suspendDialogOpen}
        onOpenChange={setSuspendDialogOpen}
        userId={userId}
        userName={user.nickname}
        onSuccess={() => {
          // Refetch user data
          getAdminUserDetail(userId).then((data) => {
            setUser(data as AdminUserDetail);
          });
        }}
      />

      <AnonymizeUserDialog
        open={anonymizeDialogOpen}
        onOpenChange={setAnonymizeDialogOpen}
        userId={userId}
        userName={user.nickname}
        onSuccess={() => {
          router.push('/admin/users');
        }}
      />
    </div>
  );
}
