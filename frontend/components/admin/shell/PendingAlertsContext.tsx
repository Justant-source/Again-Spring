'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { api } from '@/lib/api/client';

interface PendingAlerts {
  pendingReports: number;
  pendingInquiries: number;
}

const PendingAlertsContext = createContext<PendingAlerts>({
  pendingReports: 0,
  pendingInquiries: 0,
});

export function usePendingAlerts() {
  const context = useContext(PendingAlertsContext);
  if (!context) {
    throw new Error('usePendingAlerts must be used within PendingAlertsProvider');
  }
  return context;
}

interface PendingAlertsProviderProps {
  children: React.ReactNode;
}

export function PendingAlertsProvider({ children }: PendingAlertsProviderProps) {
  const [alerts, setAlerts] = useState<PendingAlerts>({
    pendingReports: 0,
    pendingInquiries: 0,
  });

  useEffect(() => {
    let isMounted = true;
    let intervalId: NodeJS.Timeout | null = null;

    const fetchAlerts = async () => {
      try {
        const [reportsRes, inquiriesRes] = await Promise.all([
          api.get('/api/admin/reports/count').catch(() => ({ data: { count: 0 } })),
          api.get('/api/admin/inquiries/count').catch(() => ({ data: { count: 0 } })),
        ]);

        if (isMounted) {
          setAlerts({
            pendingReports: reportsRes.data?.count ?? 0,
            pendingInquiries: inquiriesRes.data?.count ?? 0,
          });
        }
      } catch (error) {
        // Silently fail - alerts are non-critical
        console.error('Failed to fetch pending alerts:', error);
      }
    };

    // Initial fetch
    fetchAlerts();

    // Poll every 30 seconds
    intervalId = setInterval(fetchAlerts, 30000);

    return () => {
      isMounted = false;
      if (intervalId) clearInterval(intervalId);
    };
  }, []);

  return (
    <PendingAlertsContext.Provider value={alerts}>
      {children}
    </PendingAlertsContext.Provider>
  );
}
