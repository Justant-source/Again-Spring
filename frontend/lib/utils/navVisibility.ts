/**
 * 바텀 내비 가시성. BottomNav.tsx가 사용한다.
 */

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
