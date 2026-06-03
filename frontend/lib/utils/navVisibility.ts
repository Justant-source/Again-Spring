/**
 * 바텀 내비 / 법적 푸터 가시성 공용 헬퍼.
 * BottomNav.tsx와 LegalFooter.tsx가 동일 로직을 공유.
 */

/** 바텀 내비 시각적 높이 (safe-area 제외). layout.tsx paddingBottom 계산에 사용. */
export const BOTTOM_NAV_HEIGHT = 0;

/**
 * 바텀 내비를 표시할 경로 — 화이트리스트.
 * 디자인 스펙 기준: 광장 피드 · 알림 · 마이페이지만 표시.
 * 사연 상세·작성·인증·온보딩 등 몰입 화면은 모두 숨김.
 */
export const NAV_SHOW_PATHS = [
  '/community',     // 광장 피드 (exact)
  '/notifications', // 알림
  '/profile',       // 마이페이지 (탭 포함)
];

/** 경로가 바텀 내비를 표시해야 하는지 판단. */
export function isNavVisible(pathname: string): boolean {
  return (
    pathname === '/community' ||
    pathname === '/notifications' ||
    pathname === '/profile' ||
    pathname.startsWith('/profile/')
  );
}

/** @deprecated isNavVisible 로 대체. LegalFooter.tsx 에서만 사용 중. */
export const NAV_HIDE_PATHS = [
  '/onboarding', '/login', '/signup', '/guest', '/forgot', '/reset',
  '/auth/callback', '/admin', '/community/new', '/s/',
];

/** @deprecated isNavVisible 로 대체. LegalFooter.tsx 에서만 사용 중. */
export function isNavHidden(pathname: string): boolean {
  return NAV_HIDE_PATHS.some(p => pathname === p || pathname.startsWith(p + '/'));
}
