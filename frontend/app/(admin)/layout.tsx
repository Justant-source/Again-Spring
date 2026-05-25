'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { permissionsFor } from '@/lib/constants/userPermissions';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();
  const router = useRouter();

  // Client-side ADMIN guard — 비로그인 시 로그인 페이지, 비ADMIN 시 메인으로 강제 이동
  useEffect(() => {
    if (!hasHydrated) return; // localStorage 복원 완료 대기
    if (!user) {
      router.replace('/login?next=/admin');
      return;
    }
    const perms = permissionsFor(user);
    const canAccessDashboard = perms.admin.canAccessDashboard;
    const canAccessMarketing = perms.admin.canAccessMarketing;

    if (!canAccessDashboard && !canAccessMarketing) {
      router.replace('/');
    }
  }, [hasHydrated, user, router]);

  // 권한 검증되지 않은 동안 로딩 표시
  if (!hasHydrated || !user) {
    return (
      <div style={{ padding: 40, fontFamily: 'sans-serif', color: '#888' }}>
        권한 확인 중…
      </div>
    );
  }

  const perms = permissionsFor(user);
  if (!perms.admin.canAccessDashboard && !perms.admin.canAccessMarketing) {
    return (
      <div style={{ padding: 40, fontFamily: 'sans-serif', color: '#888' }}>
        권한 확인 중…
      </div>
    );
  }

  return children;
}
