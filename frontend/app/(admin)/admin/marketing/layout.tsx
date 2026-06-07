'use client';

import { usePathname } from 'next/navigation';
import Link from 'next/link';

const MARKETING_TABS = [
  { label: '콘텐츠', href: '/admin/marketing/contents' },
  { label: '캘린더', href: '/admin/marketing/calendar' },
  { label: '템플릿', href: '/admin/marketing/templates' },
  { label: '해시태그', href: '/admin/marketing/hashtags' },
  { label: '설정', href: '/admin/marketing/settings' },
];

export default function MarketingLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <>
      {/* Horizontal tab nav strip */}
      <nav className="sticky top-14 z-30 bg-white border-b">
        <div className="max-w-full px-6 flex gap-0 overflow-x-auto">
          {MARKETING_TABS.map((tab) => {
            const isActive = pathname.startsWith(tab.href);
            return (
              <Link
                key={tab.href}
                href={tab.href}
                className={`px-5 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  isActive
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-600 hover:text-gray-900'
                }`}
              >
                {tab.label}
              </Link>
            );
          })}
        </div>
      </nav>

      {/* Page content */}
      <main className="p-6">
        {children}
      </main>
    </>
  );
}
