'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  listUsers,
  unsuspendUser,
  forceLogoutUser,
  exportUsersAsCSV,
  type AdminUserListItem,
  type PageResponse,
} from '@/lib/api/admin/users';
import { ChangeNicknameDialog } from '@/components/admin/users/ChangeNicknameDialog';
import { updateUserRoles } from '@/lib/api/admin';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { AdminSection } from '@/components/admin/AdminSection';
import { SuspendUserDialog } from '@/components/admin/users/SuspendUserDialog';
import { AnonymizeUserDialog } from '@/components/admin/users/AnonymizeUserDialog';
import { toast } from 'sonner';

const STATUS_FILTERS = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'SUSPENDED', label: '정지됨' },
];

const ROLE_FILTERS = [
  { value: '', label: '전체' },
  { value: 'ADMIN', label: 'ADMIN' },
  { value: 'TESTER', label: 'TESTER' },
  { value: 'USER', label: 'USER' },
  { value: 'GUEST', label: 'GUEST' },
  { value: 'AI_USER', label: 'AI USER' },
];

export default function UsersPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<AdminUserListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [includeGuest, setIncludeGuest] = useState(false);

  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
  const [anonymizeDialogOpen, setAnonymizeDialogOpen] = useState(false);
  const [nicknameChangeUserId, setNicknameChangeUserId] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<AdminUserListItem | null>(null);

  const fetchUsers = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const res = await listUsers({
        page,
        size: 20,
        includeGuest,
      });

      setData(res.content);
      setTotalPages(res.totalPages);
      setCurrentPage(res.number);
    } catch (error) {
      toast.error('사용자 목록을 불러올 수 없습니다.');
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [includeGuest]);

  useEffect(() => {
    fetchUsers(0);
  }, [includeGuest, fetchUsers]);

  const handleStatusFilterChange = (value: string) => {
    setStatusFilter(value);
    setCurrentPage(0);
  };

  const handleRoleFilterChange = (value: string) => {
    setRoleFilter(value);
    setCurrentPage(0);
  };

  const filteredData = data
    .filter((user) => {
      if (statusFilter && user.status !== statusFilter) return false;
      if (roleFilter) {
        if (roleFilter === 'AI_USER') return user.synthetic;
        if (roleFilter === 'GUEST') return user.isGuest;
        if (roleFilter === 'USER') return user.roles?.includes('USER') && !user.synthetic;
        return user.roles?.includes(roleFilter);
      }
      return true;
    });

  const handleSuspend = (user: AdminUserListItem) => {
    setSelectedUser(user);
    setSuspendDialogOpen(true);
  };

  const handleUnsuspend = async (user: AdminUserListItem) => {
    try {
      await unsuspendUser(user.id);
      toast.success(`${user.nickname} 사용자 정지를 해제했습니다.`);
      fetchUsers(currentPage);
    } catch (error) {
      toast.error('정지 해제에 실패했습니다.');
      console.error(error);
    }
  };

  const handleForceLogout = async (user: AdminUserListItem) => {
    try {
      await forceLogoutUser(user.id);
      toast.success(`${user.nickname} 사용자를 강제 로그아웃했습니다.`);
    } catch (error) {
      toast.error('강제 로그아웃에 실패했습니다.');
      console.error(error);
    }
  };

  const handleAnonymize = (user: AdminUserListItem) => {
    setSelectedUser(user);
    setAnonymizeDialogOpen(true);
  };

  const handleChangeRole = async (user: AdminUserListItem) => {
    const currentRoles = user.roles || [];
    const isTeser = currentRoles.includes('TESTER');
    const newRoles = isTeser
      ? currentRoles.filter((r) => r !== 'TESTER')
      : [...currentRoles, 'TESTER'];

    try {
      await updateUserRoles(user.id, newRoles);
      toast.success(`${user.nickname}의 역할을 변경했습니다.`);
      fetchUsers(currentPage);
    } catch (error) {
      toast.error('역할 변경에 실패했습니다.');
      console.error(error);
    }
  };

  const handleExport = async () => {
    try {
      await exportUsersAsCSV();
      toast.success('CSV 파일이 다운로드되었습니다.');
    } catch (error) {
      toast.error('CSV 내보내기에 실패했습니다.');
      console.error(error);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge variant="outline" className="bg-green-50 text-green-700">활성</Badge>;
      case 'SUSPENDED':
        return <Badge variant="destructive">정지됨</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  const getRoleBadges = (roles: string[] = []) => {
    return roles.map((role) => (
      <Badge key={role} variant="secondary">
        {role}
      </Badge>
    ));
  };

  const columns = [
    {
      key: 'nickname',
      header: '닉네임',
      render: (user: AdminUserListItem) => (
        <Link href={`/admin/users/${user.id}`} className="text-blue-600 hover:underline">
          {user.nickname}
        </Link>
      ),
    },
    {
      key: 'email',
      header: '이메일',
      render: (user: AdminUserListItem) => user.email || '—',
    },
    {
      key: 'roles',
      header: '역할',
      render: (user: AdminUserListItem) => (
        <div className="flex gap-1 flex-wrap">
          {getRoleBadges(user.roles)}
        </div>
      ),
    },
    {
      key: 'status',
      header: '상태',
      render: (user: AdminUserListItem) => getStatusBadge(user.status),
    },
    {
      key: 'isGuest',
      header: '게스트',
      render: (user: AdminUserListItem) => (user.isGuest ? '예' : '아니오'),
    },
    {
      key: 'createdAt',
      header: '가입일',
      render: (user: AdminUserListItem) => new Date(user.createdAt).toLocaleDateString('ko-KR'),
    },
    {
      key: 'actions',
      header: '액션',
      render: (user: AdminUserListItem) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm">
              ···
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="bg-white border shadow-md">
            <DropdownMenuItem asChild>
              <Link href={`/admin/users/${user.id}`}>상세보기</Link>
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setNicknameChangeUserId(user.id)}>
              닉네임 변경
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => handleChangeRole(user)}>
              역할 변경 (TESTER)
            </DropdownMenuItem>
            {user.status === 'ACTIVE' ? (
              <DropdownMenuItem onClick={() => handleSuspend(user)}>정지</DropdownMenuItem>
            ) : (
              <DropdownMenuItem onClick={() => handleUnsuspend(user)}>해제</DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={() => handleForceLogout(user)}>
              강제로그아웃
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleAnonymize(user)}
              className="text-red-600"
            >
              익명화
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">회원 관리</h1>
        <Button onClick={handleExport} variant="outline">
          CSV 내보내기
        </Button>
      </div>

      <AdminSection title="검색 및 필터">
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium mb-2 block">상태</label>
            <div className="flex gap-2 flex-wrap">
              {STATUS_FILTERS.map((filter) => (
                <Button
                  key={filter.value}
                  variant={statusFilter === filter.value ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => handleStatusFilterChange(filter.value)}
                >
                  {filter.label}
                </Button>
              ))}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium mb-2 block">역할</label>
            <div className="flex gap-2 flex-wrap">
              {ROLE_FILTERS.map((filter) => (
                <Button
                  key={filter.value}
                  variant={roleFilter === filter.value ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => handleRoleFilterChange(filter.value)}
                >
                  {filter.label}
                </Button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="include-guest"
              checked={includeGuest}
              onChange={(e) => setIncludeGuest(e.target.checked)}
              className="rounded"
            />
            <label htmlFor="include-guest" className="text-sm">
              게스트 포함
            </label>
          </div>
        </div>
      </AdminSection>

      <AdminSection title="사용자 목록">
        <AdminTable
          data={filteredData}
          columns={columns}
          loading={loading}
          rowKey={(user) => user.id}
        />
        {totalPages > 1 && (
          <AdminPagination
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={fetchUsers}
          />
        )}
      </AdminSection>

      {selectedUser && (
        <>
          <SuspendUserDialog
            open={suspendDialogOpen}
            onOpenChange={setSuspendDialogOpen}
            userId={selectedUser.id}
            userName={selectedUser.nickname}
            onSuccess={() => fetchUsers(currentPage)}
          />
          <AnonymizeUserDialog
            open={anonymizeDialogOpen}
            onOpenChange={setAnonymizeDialogOpen}
            userId={selectedUser.id}
            userName={selectedUser.nickname}
            onSuccess={() => fetchUsers(currentPage)}
          />
        </>
      )}

      <ChangeNicknameDialog
        userId={nicknameChangeUserId}
        currentNickname={data.find(u => u.id === nicknameChangeUserId)?.nickname}
        onClose={() => setNicknameChangeUserId(null)}
        onChanged={(newNickname) => {
          setData(prev => prev.map(u => u.id === nicknameChangeUserId ? { ...u, nickname: newNickname } : u));
          setNicknameChangeUserId(null);
          toast.success('닉네임이 변경됐습니다.');
        }}
      />
    </div>
  );
}
