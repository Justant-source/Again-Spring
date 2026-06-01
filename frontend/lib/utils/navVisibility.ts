/**
 * 바텀 내비 / 법적 푸터 가시성 공용 헬퍼.
 * BottomNav.tsx와 LegalFooter.tsx가 동일 로직을 공유.
 */

/** 바텀 내비 시각적 높이 (safe-area 제외). layout.tsx paddingBottom 계산에 사용. */
export const BOTTOM_NAV_HEIGHT = 74;

/**
 * 바텀 내비를 숨길 경로 패턴.
 * 안전 불변: 채팅·결과·온보딩·인증 플로우에서는 항상 숨김.
 * '/community/new'는 글쓰기 몰입 화면 — 피드·상세는 표시.
 */
export const NAV_HIDE_PATHS = [
  '/session/chat',
  '/session/result',
  '/session/new',
  '/session/join',
  '/session/history',
  '/session/category',
  '/three-way',
  '/onboarding',
  '/login',
  '/signup',
  '/guest',
  '/forgot',
  '/reset',
  '/auth/callback',
  '/admin',
  '/community/new',
];

/** 경로가 숨김 패턴에 해당하는지 정확히 매칭. */
export function isNavHidden(pathname: string): boolean {
  return NAV_HIDE_PATHS.some(p => pathname === p || pathname.startsWith(p + '/'));
}
