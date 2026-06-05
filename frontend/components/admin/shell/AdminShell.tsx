'use client';

import { useState } from 'react';
import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';
import { PendingAlertsProvider } from './PendingAlertsContext';

interface AdminShellProps {
  children: React.ReactNode;
}

export function AdminShell({ children }: AdminShellProps) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <PendingAlertsProvider>
      {/* Fixed topbar — spans full width */}
      <AdminTopBar onMobileMenuClick={() => setSidebarOpen(true)} />

      {/* Below topbar: sidebar + main in a flex row */}
      <div className="flex pt-14 h-screen overflow-hidden">
        {/* Sidebar — flex child (not fixed), so main content is pushed right naturally */}
        <AdminSidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        {/* Main content */}
        <main className="flex-1 overflow-auto bg-gray-50">
          <div className="p-6">
            {children}
          </div>
        </main>
      </div>
    </PendingAlertsProvider>
  );
}

export default AdminShell;
