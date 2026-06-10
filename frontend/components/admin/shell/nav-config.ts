import type { LucideIcon } from 'lucide-react';

export type AdminNavBadgeType = 'reports' | 'inquiries' | 'marketing' | 'aiUser';

export interface AdminNavItem {
  label: string;
  href: string;
  icon: string; // lucide icon name
  badge?: AdminNavBadgeType;
}

export interface AdminNavGroup {
  label: string;
  items: AdminNavItem[];
}

export const NAV_GROUPS: AdminNavGroup[] = [
  {
    label: '개요',
    items: [
      { label: '대시보드', href: '/admin', icon: 'LayoutDashboard' },
    ],
  },
  {
    label: '운영',
    items: [
      { label: '회원관리', href: '/admin/users', icon: 'Users' },
      { label: '콘텐츠관리', href: '/admin/content', icon: 'FileText' },
      { label: '신고관리', href: '/admin/reports', icon: 'AlertCircle', badge: 'reports' },
      { label: '문의관리', href: '/admin/inquiries', icon: 'MessageSquare', badge: 'inquiries' },
    ],
  },
  {
    label: '인사이트',
    items: [
      { label: '통계', href: '/admin/stats', icon: 'BarChart3' },
      { label: '위기모니터링', href: '/admin/crisis', icon: 'AlertTriangle' },
    ],
  },
  {
    label: '커뮤니케이션',
    items: [
      { label: '공지관리', href: '/admin/announcements', icon: 'Megaphone' },
      { label: '알림발송', href: '/admin/notifications', icon: 'Bell' },
    ],
  },
  {
    label: '마케팅',
    items: [
      { label: '마케팅 잡', href: '/admin/marketing', icon: 'Zap', badge: 'marketing' },
    ],
  },
  {
    label: 'AI',
    items: [
      { label: 'AI 규칙관리', href: '/admin/ai-rules', icon: 'Sparkles' },
      { label: 'AI 생성 관제', href: '/admin/ai-user', icon: 'Cpu', badge: 'aiUser' },
    ],
  },
  {
    label: '시스템',
    items: [
      { label: '시스템', href: '/admin/system', icon: 'Settings' },
      { label: '감사로그', href: '/admin/audit', icon: 'ClipboardList' },
    ],
  },
];
