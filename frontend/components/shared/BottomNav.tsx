'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { isNavHidden } from '@/lib/utils/navVisibility';

// ─── SVG 탭 아이콘 ────────────────────────────────────────────────────

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z" />
      <path d="M9 21V12h6v9" />
    </svg>
  );
}

function CommunityIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="8" r="3.5" />
      <path d="M3 20c0-3.8 2.7-7 6-7" />
      <circle cx="17" cy="8" r="2.5" />
      <path d="M21 20c0-3-1.8-5.5-4-5.5" />
      <path d="M9 13c3.3 0 6 3.2 6 7" />
    </svg>
  );
}

function NotificationsIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

function ProfileIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4.4 3.6-8 8-8s8 3.6 8 8" />
    </svg>
  );
}

function AdminIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 2L3 7v5c0 5 4 9.7 9 11 5-1.3 9-6 9-11V7L12 2z" />
    </svg>
  );
}

// ─── 컴포넌트 ─────────────────────────────────────────────────────────

export function BottomNav() {
  const pathname = usePathname();
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const perms = permissionsFor(user);

  // 몰입 화면에서는 숨김 — 안전 불변: 채팅/결과/온보딩/인증 항상 숨김
  if (isNavHidden(pathname)) return null;

  const showAdmin = perms.ui.showAdminEntryButton;        // 관리자만: true
  const showNotifications = !user?.isGuest && !showAdmin; // 회원만 (관리자 제외)

  const isActive = (href: string) =>
    href === '/' ? pathname === '/' : (pathname === href || pathname.startsWith(href + '/'));

  const baseTab: React.CSSProperties = {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 3,
    padding: '6px 4px',
    textDecoration: 'none',
    minWidth: 0,
    transition: 'color 0.15s',
  };

  const lbl = (active: boolean): React.CSSProperties => ({
    fontSize: 12,
    fontWeight: active ? 600 : 400,
    letterSpacing: '-0.2px',
    lineHeight: 1,
    whiteSpace: 'nowrap',
  });

  // 가운데 CTA 클릭 핸들러 — 사연 올리기
  const handleCta = () => router.push('/community/new');

  return (
    <nav
      data-testid="bottom-nav"
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 200, // 위기 모달(999/9999)보다 반드시 낮게 — 안전 불변
        background: 'var(--P-card)',
        borderTop: '1px solid var(--P-border)',
        display: 'flex',
        alignItems: 'flex-end',
        overflow: 'visible', // 가운데 CTA 원형이 바 위로 돌출되도록
        paddingBottom: 'max(8px, env(safe-area-inset-bottom, 0px))',
      }}
    >
      {/* ── 홈 ── */}
      <Link
        href="/"
        data-testid="nav-home"
        style={{ ...baseTab, color: isActive('/') ? 'var(--P-ink)' : 'var(--P-sub)' }}
      >
        <HomeIcon active={isActive('/')} />
        <span style={lbl(isActive('/'))}>홈</span>
      </Link>

      {/* ── 커뮤니티 ── */}
      <Link
        href="/community"
        data-testid="nav-community"
        style={{ ...baseTab, color: isActive('/community') ? 'var(--P-ink)' : 'var(--P-sub)' }}
      >
        <CommunityIcon active={isActive('/community')} />
        <span style={lbl(isActive('/community'))}>커뮤니티</span>
      </Link>

      {/* ── 가운데 강조 CTA: 사연 올리기 / 관리 탭 (관리자) ── */}
      {!showAdmin ? (
        <button
          type="button"
          onClick={handleCta}
          aria-label="사연 올리기"
          data-testid="nav-write-post"
          style={{
            flex: 1.4,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 4,
            padding: '0 4px 6px',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            minWidth: 0,
          }}
        >
          {/* 원형 — 바 위로 16px 돌출 (overflow:visible 으로 노출) */}
          <div style={{
            width: 52,
            height: 52,
            borderRadius: '50%',
            background: 'var(--P-ink)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            // 원이 바 위로 올라옴 — flex align-items:flex-end 환경에서 marginTop이 음수면 위로 돌출
            marginTop: -16,
            flexShrink: 0,
            boxShadow: '0 -2px 12px rgba(60,40,20,0.10), 0 6px 24px rgba(60,40,20,0.20)',
            transition: 'box-shadow 0.15s, transform 0.15s',
          }}>
            <span style={{ color: 'white', fontSize: 20 }}>✎</span>
          </div>
          <span style={{
            fontSize: 12,
            fontWeight: 700,
            letterSpacing: '-0.3px',
            lineHeight: 1,
            color: 'var(--P-ink)',
            whiteSpace: 'nowrap',
          }}>
            사연 올리기
          </span>
        </button>
      ) : (
        /* 관리자: 사연 올리기 대신 관리 탭 */
        <Link
          href="/admin"
          data-testid="nav-admin"
          style={{ ...baseTab, flex: 1.4, color: isActive('/admin') ? 'var(--P-ink)' : 'var(--P-sub)' }}
        >
          <AdminIcon active={isActive('/admin')} />
          <span style={lbl(isActive('/admin'))}>관리</span>
        </Link>
      )}

      {/* ── 알림 (회원만 / 게스트·관리자 숨김) ── */}
      {showNotifications ? (
        <Link
          href="/notifications"
          data-testid="nav-notifications"
          style={{ ...baseTab, color: isActive('/notifications') ? 'var(--P-ink)' : 'var(--P-sub)' }}
        >
          <NotificationsIcon active={isActive('/notifications')} />
          <span style={lbl(isActive('/notifications'))}>알림</span>
        </Link>
      ) : (
        /* 게스트·관리자: 알림 자리를 스페이서로 채워 균형 유지 */
        <div style={{ flex: 1 }} aria-hidden="true" />
      )}

      {/* ── 내정보 ── */}
      <Link
        href="/profile"
        data-testid="nav-profile"
        style={{ ...baseTab, color: isActive('/profile') ? 'var(--P-ink)' : 'var(--P-sub)' }}
      >
        <ProfileIcon active={isActive('/profile')} />
        <span style={lbl(isActive('/profile'))}>내정보</span>
      </Link>
    </nav>
  );
}
