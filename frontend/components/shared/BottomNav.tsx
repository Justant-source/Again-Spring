'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { isNavVisible } from '@/lib/utils/navVisibility';
import { GuestInfoSheet } from '@/components/shared/GuestInfoSheet';

// ─── SVG 탭 아이콘 ────────────────────────────────────────────────────

function PlazaIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 11l9-7 9 7" />
      <path d="M5 10v9a1 1 0 001 1h12a1 1 0 001-1v-9" />
    </svg>
  );
}

function NotificationsIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 8a6 6 0 0112 0c0 7 3 8 3 8H3s3-1 3-8" />
      <path d="M10 21a2 2 0 004 0" />
    </svg>
  );
}

function ActivityIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 6h16" />
      <path d="M4 12h16" />
      <path d="M4 18h10" />
    </svg>
  );
}

function WriteIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4 12.5-12.5z" />
    </svg>
  );
}

function AdminIcon({ active }: { active: boolean }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={active ? 2 : 1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
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
  const [guestSheetOpen, setGuestSheetOpen] = useState(false);

  // 광장·알림·마이페이지에서만 표시
  if (!isNavVisible(pathname)) return null;

  const showAdmin = perms.ui.showAdminEntryButton;
  const isGuest = user?.isGuest ?? true;
  // 알림은 게스트·회원·admin 모두에게 표시 (백엔드가 게스트에게도 알림 생성)

  // /profile/info 등 서브경로에서 탭이 활성화되지 않도록 정확한 경로만 매칭
  const isActive = (href: string) => pathname === href;

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
    fontSize: 10,
    fontWeight: active ? 600 : 400,
    letterSpacing: '-0.2px',
    lineHeight: 1,
    whiteSpace: 'nowrap',
  });

  const handleWrite = () => router.push('/community/new');

  return (
    <>
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
        paddingBottom: 'max(8px, env(safe-area-inset-bottom, 0px))',
      }}
    >
      {/* PC 최대 너비 640 제한 */}
      <div style={{
        maxWidth: 640,
        margin: '0 auto',
        display: 'flex',
        alignItems: 'stretch',
        overflow: 'visible',
      }}>
        {/* ── 광장 ── */}
        <Link
          href="/community"
          data-testid="nav-plaza"
          style={{ ...baseTab, color: isActive('/community') ? 'var(--P-ink)' : 'var(--P-sub)' }}
        >
          <PlazaIcon active={isActive('/community')} />
          <span style={lbl(isActive('/community'))}>광장</span>
        </Link>

        {/* ── 알림 (게스트는 제약 안내 시트, 회원·관리자는 알림 페이지) ── */}
        {isGuest ? (
          <button
            type="button"
            onClick={() => setGuestSheetOpen(true)}
            data-testid="nav-notifications"
            style={{
              ...baseTab,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--P-sub)',
            }}
          >
            <NotificationsIcon active={false} />
            <span style={lbl(false)}>알림</span>
          </button>
        ) : (
          <Link
            href="/notifications"
            data-testid="nav-notifications"
            style={{ ...baseTab, color: isActive('/notifications') ? 'var(--P-ink)' : 'var(--P-sub)' }}
          >
            <NotificationsIcon active={isActive('/notifications')} />
            <span style={lbl(isActive('/notifications'))}>알림</span>
          </Link>
        )}

        {/* ── 글쓰기 / 관리 탭 (관리자) ── */}
        {!showAdmin ? (
          <button
            type="button"
            onClick={handleWrite}
            aria-label="글쓰기"
            data-testid="nav-write-post"
            style={{
              ...baseTab,
              flex: 1,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: isActive('/community/new') ? 'var(--P-ink)' : 'var(--P-sub)',
            }}
          >
            <WriteIcon active={isActive('/community/new')} />
            <span style={lbl(isActive('/community/new'))}>글쓰기</span>
          </button>
        ) : (
          <Link
            href="/admin"
            data-testid="nav-admin"
            style={{ ...baseTab, flex: 1.4, color: isActive('/admin') ? 'var(--P-ink)' : 'var(--P-sub)' }}
          >
            <AdminIcon active={isActive('/admin')} />
            <span style={lbl(isActive('/admin'))}>관리</span>
          </Link>
        )}

        {/* ── 내 활동 (게스트는 제약 안내 시트, 회원은 마이페이지) ── */}
        {isGuest ? (
          <button
            type="button"
            onClick={() => setGuestSheetOpen(true)}
            data-testid="nav-activity"
            style={{
              ...baseTab,
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--P-sub)',
            }}
          >
            <ActivityIcon active={false} />
            <span style={lbl(false)}>내 활동</span>
          </button>
        ) : (
          <Link
            href="/profile"
            data-testid="nav-activity"
            style={{ ...baseTab, color: isActive('/profile') ? 'var(--P-ink)' : 'var(--P-sub)' }}
          >
            <ActivityIcon active={isActive('/profile')} />
            <span style={lbl(isActive('/profile'))}>내 활동</span>
          </Link>
        )}
      </div>
    </nav>

      {/* 게스트 제약 안내 시트 — nav(zIndex 200) 바깥 형제로 렌더 */}
      {guestSheetOpen && user && (
        <GuestInfoSheet user={user} onClose={() => setGuestSheetOpen(false)} />
      )}
    </>
  );
}
