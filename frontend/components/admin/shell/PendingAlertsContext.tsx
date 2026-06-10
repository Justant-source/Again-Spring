'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { api } from '@/lib/api/client';

interface PendingAlerts {
  pendingReports: number;
  pendingInquiries: number;
  marketingPending: number;
  aiFailures: number;
}

const PendingAlertsContext = createContext<PendingAlerts>({
  pendingReports: 0,
  pendingInquiries: 0,
  marketingPending: 0,
  aiFailures: 0,
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
    marketingPending: 0,
    aiFailures: 0,
  });

  useEffect(() => {
    let isMounted = true;
    let intervalId: NodeJS.Timeout | null = null;

    const fetchAlerts = async () => {
      try {
        const res = await api.get<any>('/api/admin/dashboard/action-center').catch(() => ({ data: {} }));

        if (isMounted) {
          const data = res.data || {};
          setAlerts({
            pendingReports: data.pendingReports ?? 0,
            pendingInquiries: data.openInquiries ?? 0,
            marketingPending: (data.marketingAwaitingApproval ?? 0) + (data.marketingFailed ?? 0),
            aiFailures: (data.aiFailuresToday ?? 0) + (data.aiBlockedToday ?? 0),
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
