'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { notificationApi, NotificationItem } from '@/lib/api/community/notificationApi';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { GuestConvertModal } from '@/components/auth/GuestConvertModal';

export default function NotificationsPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showGuestConvert, setShowGuestConvert] = useState(false);

  useEffect(() => {
    if (hasHydrated && !user) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  useEffect(() => {
    if (hasHydrated && user?.isGuest) {
      setShowGuestConvert(true);
    }
  }, [hasHydrated, user?.isGuest]);

  useEffect(() => {
    if (!hasHydrated || !user) return;

    const fetchNotifications = async () => {
      try {
        setLoading(true);
        const data = await notificationApi.list();
        setNotifications(data);
      } catch (err) {
        console.error('Failed to fetch notifications:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchNotifications();
  }, [hasHydrated, user]);

  const handleMarkAllRead = async () => {
    try {
      await notificationApi.readAll();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, isRead: true }))
      );
    } catch (err) {
      console.error('Failed to mark all as read:', err);
    }
  };

  const handleNotificationClick = (notification: NotificationItem) => {
    if (notification.refPostId) {
      router.push(`/community/${notification.refPostId}`);
    }
  };

  const getNotificationDot = (type: string) => {
    switch (type) {
      case 'PARTNER_ANSWERED':
        return 'var(--L-point)'; // 알림 강조색
      case 'NEW_VOTE':
        return 'var(--faction-author)'; // 투표 알림 — 작성자(피치)
      default:
        return 'var(--L-sub)';
    }
  };

  const formatTime = (createdAt: string) => {
    const date = new Date(createdAt);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;
    if (diffHours < 24) return `${diffHours}시간 전`;
    if (diffDays < 7) return `${diffDays}일 전`;
    return date.toLocaleDateString('ko-KR');
  };

  if (!hasHydrated || !user) {
    return null;
  }

  return (
    <>
      <PhoneFrame tone="L">
        <PhoneHeader
          title="알림"
          tone="L"
          back={false}
          right={
            notifications.length > 0 ? (
              <button
                onClick={handleMarkAllRead}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--L-sub)',
                  fontSize: 12,
                  cursor: 'pointer',
                  textDecoration: 'underline',
                  padding: 0,
                  font: 'inherit',
                }}
              >
                모두 읽음
              </button>
            ) : undefined
          }
        />
        <div style={{ padding: '8px 28px 40px', flex: 1 }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--L-sub)', fontSize: 13 }}>
              로딩 중...
            </div>
          ) : notifications.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--L-sub)', fontSize: 13 }}>
              아직 알림이 없어요
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
              {notifications.map((notification, idx) => (
                <div
                  key={notification.id}
                  onClick={() => handleNotificationClick(notification)}
                  style={{
                    padding: '14px 0',
                    borderBottom: idx < notifications.length - 1 ? '1px solid var(--L-border)' : 'none',
                    cursor: notification.refPostId ? 'pointer' : 'default',
                    background: !notification.isRead ? 'var(--L-card)' : 'transparent',
                    marginLeft: -28,
                    marginRight: -28,
                    paddingLeft: 28,
                    paddingRight: 28,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                    {/* Dot indicator */}
                    <div
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: getNotificationDot(notification.type),
                        marginTop: 5,
                        flexShrink: 0,
                      }}
                    />
                    {/* Content */}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500, marginBottom: 4 }}>
                        {notification.title}
                      </div>
                      {notification.subtitle && (
                        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 6 }}>
                          {notification.subtitle}
                        </div>
                      )}
                      <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>
                        {formatTime(notification.createdAt)}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </PhoneFrame>

      {/* Guest Convert Modal */}
      <GuestConvertModal
        isOpen={showGuestConvert}
        onClose={() => setShowGuestConvert(false)}
        onSignup={() => {
          setShowGuestConvert(false);
          router.push('/signup');
        }}
      />
    </>
  );
}
