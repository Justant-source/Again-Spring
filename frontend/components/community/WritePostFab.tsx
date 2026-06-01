'use client';

import Link from 'next/link';
import { BOTTOM_NAV_HEIGHT } from '@/lib/utils/navVisibility';

/** 커뮤니티 사연 쓰기 플로팅 버튼.
 *  - 위치: 우하단, 하단 내비 위 16px
 *  - 가운데 '대화 시작' CTA와 라벨·위치 모두 달라 혼동 없음
 *  - /community/new 글쓰기 화면에서는 layout.tsx가 렌더를 차단
 */
export function WritePostFab() {
  return (
    <Link
      href="/community/new"
      aria-label="사연 쓰기"
      style={{
        position: 'fixed',
        right: 16,
        bottom: BOTTOM_NAV_HEIGHT + 16,
        zIndex: 190, // BottomNav(200) 아래, 위기 모달(999) 아래
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '12px 18px',
        background: 'var(--P-ink)',
        color: 'white',
        borderRadius: 999,
        textDecoration: 'none',
        boxShadow: '0 4px 16px rgba(60,40,20,0.22)',
        fontSize: 13,
        fontWeight: 600,
        letterSpacing: '-0.2px',
        whiteSpace: 'nowrap',
        transition: 'box-shadow 0.15s, transform 0.15s',
      }}
      onMouseEnter={(e) => { e.currentTarget.style.boxShadow = '0 6px 20px rgba(60,40,20,0.30)'; }}
      onMouseLeave={(e) => { e.currentTarget.style.boxShadow = '0 4px 16px rgba(60,40,20,0.22)'; }}
    >
      {/* 펜 아이콘 */}
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
      </svg>
      사연 쓰기
    </Link>
  );
}
