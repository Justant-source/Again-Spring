'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { usePendingAlerts } from './PendingAlertsContext';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Badge } from '@/components/ui/badge';
import { ChevronRight, LogOut, Menu } from 'lucide-react';
import { useState, useEffect } from 'react';

interface AdminTopBarProps {
  onMobileMenuClick?: () => void;
}

export function AdminTopBar({ onMobileMenuClick }: AdminTopBarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const userClear = useUserStore((s) => s.clear);
  const { pendingReports, pendingInquiries } = usePendingAlerts();

  const [breadcrumbs, setBreadcrumbs] = useState<Array<{ label: string; href: string }>>([]);

  // Generate breadcrumbs from pathname
  useEffect(() => {
    const segments = pathname
      .split('/')
      .filter((s) => s && s !== 'admin')
      .slice(0, 2); // Limit to admin + 1 level

    if (segments.length === 0) {
      setBreadcrumbs([{ label: '대시보드', href: '/admin' }]);
      return;
    }

    const crumbs: Array<{ label: string; href: string }> = [
      { label: '대시보드', href: '/admin' },
    ];

    let href = '/admin';
    segments.forEach((seg, i) => {
      href += '/' + seg;
      const label = seg
        .split('-')
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ');
      if (i === segments.length - 1) {
        crumbs.push({ label, href });
      }
    });

    setBreadcrumbs(crumbs);
  }, [pathname]);

  const handleLogout = () => {
    userClear();
    router.push('/');
  };

  const handleBackToApp = () => {
    router.push('/');
  };

  return (
    <header className="fixed top-0 left-0 right-0 h-14 bg-white border-b z-40">
      <div className="h-full max-w-full px-4 flex items-center justify-between gap-4">
        {/* Left: Mobile menu + Breadcrumbs */}
        <div className="flex items-center gap-2 text-sm flex-1 min-w-0">
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 lg:hidden"
            onClick={onMobileMenuClick}
          >
            <Menu size={18} />
          </Button>
          <div className="flex items-center gap-1 overflow-x-auto whitespace-nowrap hidden sm:flex">
            {breadcrumbs.map((crumb, i) => (
              <div key={crumb.href} className="flex items-center gap-1">
                {i > 0 && <ChevronRight size={14} className="text-gray-400 flex-shrink-0" />}
                <button
                  onClick={() => router.push(crumb.href)}
                  className="text-gray-700 hover:text-gray-900 transition-colors text-sm"
                >
                  {crumb.label}
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Right: Alerts & User Menu */}
        <div className="flex items-center gap-3">
          {/* Pending alerts badges */}
          <div className="flex gap-2">
            {pendingReports > 0 && (
              <Badge variant="destructive" className="bg-red-600">
                신고 {pendingReports}
              </Badge>
            )}
            {pendingInquiries > 0 && (
              <Badge variant="destructive" className="bg-orange-600">
                문의 {pendingInquiries}
              </Badge>
            )}
          </div>

          {/* User dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm" className="text-sm font-medium">
                {user?.nickname || '관리자'}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem disabled className="text-xs text-gray-500">
                {user?.email}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleBackToApp}>
                다시봄으로 돌아가기
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleLogout} className="text-red-600">
                <LogOut size={14} className="mr-2" />
                로그아웃
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}
