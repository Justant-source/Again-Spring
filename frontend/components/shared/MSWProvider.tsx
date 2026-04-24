'use client';

import { useEffect, useState, type ReactNode } from 'react';

/**
 * Initializes MSW in the browser during development so Mock API handlers
 * intercept fetch calls. In production / SSR this is a no-op passthrough.
 */
export function MSWProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(
    process.env.NODE_ENV !== 'development',
  );

  useEffect(() => {
    if (process.env.NODE_ENV !== 'development') return;
    if (typeof window === 'undefined') return;

    let cancelled = false;
    import('@/mocks/browser').then(async ({ worker }) => {
      await worker.start({
        onUnhandledRequest: 'bypass',
        serviceWorker: { url: '/mockServiceWorker.js' },
      });
      if (!cancelled) setReady(true);
    });

    return () => {
      cancelled = true;
    };
  }, []);

  if (!ready) return null;
  return <>{children}</>;
}
