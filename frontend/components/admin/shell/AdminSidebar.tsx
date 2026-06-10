'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { NAV_GROUPS } from './nav-config';
import { usePendingAlerts } from './PendingAlertsContext';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ChevronRight, ChevronLeft } from 'lucide-react';
import * as Icons from 'lucide-react';

interface AdminSidebarProps {
  isOpen?: boolean;
  onClose?: () => void;
}

export function AdminSidebar({ isOpen, onClose }: AdminSidebarProps) {
  const pathname = usePathname();
  const user = useUserStore((s) => s.user);
  const alerts = usePendingAlerts();
  const [collapsed, setCollapsed] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem('admin-sidebar-collapsed');
    if (saved) setCollapsed(JSON.parse(saved));
  }, []);

  useEffect(() => {
    const checkMobile = () => setIsMobile(window.innerWidth < 1024);
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  const toggleCollapse = () => {
    setCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem('admin-sidebar-collapsed', JSON.stringify(next));
      return next;
    });
  };

  // Active if exact match OR starts with href/ (but /admin only exact)
  const isActive = (href: string) => {
    if (href === '/admin') return pathname === '/admin';
    return pathname === href || pathname.startsWith(href + '/');
  };

  const getLucideIcon = (iconName: string) => {
    const IconComponent = (Icons as any)[iconName];
    return IconComponent || Icons.FileText;
  };

  const navContent = (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Collapse toggle (desktop only) */}
      {!isMobile && (
        <div className="flex justify-end p-2 border-b shrink-0">
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleCollapse}
            className="h-8 w-8 p-0"
            title={collapsed ? '사이드바 확장' : '사이드바 축소'}
          >
            {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </Button>
        </div>
      )}

      {/* Nav groups */}
      <nav className="flex-1 overflow-y-auto py-3 px-2">
        {NAV_GROUPS.map((group) => (
          <div key={group.label} className="mb-5">
            {!collapsed && (
              <div className="px-3 pb-1 text-xs font-semibold text-gray-400 uppercase tracking-wider">
                {group.label}
              </div>
            )}
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = getLucideIcon(item.icon);
                const active = isActive(item.href);

                // Determine badge count based on type
                let badgeCount = 0;
                if (item.badge === 'reports') {
                  badgeCount = alerts.pendingReports;
                } else if (item.badge === 'inquiries') {
                  badgeCount = alerts.pendingInquiries;
                } else if (item.badge === 'marketing') {
                  badgeCount = alerts.marketingPending;
                } else if (item.badge === 'aiUser') {
                  badgeCount = alerts.aiFailures;
                }

                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                      active
                        ? 'bg-blue-50 text-blue-700 font-medium'
                        : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                    }`}
                    title={collapsed ? item.label : undefined}
                    onClick={isMobile ? onClose : undefined}
                  >
                    <Icon size={18} className="shrink-0" />
                    {!collapsed && (
                      <>
                        <span className="flex-1 truncate">{item.label}</span>
                        {badgeCount > 0 && (
                          <Badge variant="destructive" className="text-xs">
                            {badgeCount}
                          </Badge>
                        )}
                      </>
                    )}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      {/* Footer */}
      <div className={`shrink-0 border-t px-3 py-3 text-xs text-gray-500 ${collapsed ? 'text-center' : ''}`}>
        {collapsed ? '👤' : (user?.email || '관리자')}
      </div>
    </div>
  );

  // Mobile: Sheet drawer
  if (isMobile) {
    return (
      <Sheet open={isOpen} onOpenChange={onClose}>
        <SheetContent side="left" className="w-64 p-0 bg-white">
          {navContent}
        </SheetContent>
      </Sheet>
    );
  }

  // Desktop: static flex child (NOT position:fixed — AdminShell handles layout)
  return (
    <aside
      className={`shrink-0 bg-white border-r h-full flex flex-col transition-all duration-200 ${
        collapsed ? 'w-16' : 'w-60'
      }`}
    >
      {navContent}
    </aside>
  );
}
