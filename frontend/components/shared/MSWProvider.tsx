'use client';

import { useEffect, type ReactNode } from 'react';

/**
 * Initializes MSW in the browser during development so Mock API handlers
 * intercept fetch calls. In production / SSR this is a no-op passthrough.
 * Children always render immediately; MSW starts in the background.
 * Playwright E2E tests use page.route() intercepts instead of the service worker.
 */
export function MSWProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'development') return;
    if (typeof window === 'undefined') return;

    import('@/mocks/browser').then(async ({ worker }) => {
      try {
        await worker.start({
          onUnhandledRequest: 'bypass',
          serviceWorker: { url: '/mockServiceWorker.js' },
          quiet: true,
        });
      } catch (err) {
        console.warn('[MSW] Service worker start failed:', err);
      }
    });
  }, []);

  return <>{children}</>;
}
