'use client';

import { useEffect, useState } from 'react';
import { RotateCw } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface RefreshControlProps {
  onRefresh: () => void;
  loading?: boolean;
  autoRefreshSeconds?: number;
  'data-testid'?: string;
}

export function RefreshControl({
  onRefresh,
  loading = false,
  autoRefreshSeconds = 60,
  'data-testid': testId,
}: RefreshControlProps) {
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(autoRefreshSeconds > 0);
  const [lastRefreshTime, setLastRefreshTime] = useState<string>('');

  // Auto-refresh timer
  useEffect(() => {
    if (!autoRefreshEnabled || autoRefreshSeconds <= 0) return;

    const intervalId = setInterval(() => {
      onRefresh();
    }, autoRefreshSeconds * 1000);

    return () => clearInterval(intervalId);
  }, [autoRefreshEnabled, autoRefreshSeconds, onRefresh]);

  // Update last refresh time on initial load and when refresh happens
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setLastRefreshTime(
        `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
      );
    };
    updateTime();
  }, []);

  const handleRefresh = () => {
    onRefresh();
    const now = new Date();
    setLastRefreshTime(
      `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
    );
  };

  return (
    <div className="flex items-center gap-3" data-testid={testId}>
      <span className="text-xs text-gray-500">
        마지막 갱신: {lastRefreshTime || '--:--:--'}
      </span>

      <Button
        variant="ghost"
        size="sm"
        onClick={handleRefresh}
        disabled={loading}
        className="h-7 w-7 p-0"
        title="새로고침"
      >
        <RotateCw size={14} className={loading ? 'animate-spin' : ''} />
      </Button>

      {autoRefreshSeconds > 0 && (
        <label className="flex items-center gap-1 text-xs cursor-pointer">
          <input
            type="checkbox"
            checked={autoRefreshEnabled}
            onChange={(e) => setAutoRefreshEnabled(e.target.checked)}
            className="w-3 h-3"
            title="자동 갱신 토글"
          />
          <span className="text-gray-600">자동 ({autoRefreshSeconds}초)</span>
        </label>
      )}
    </div>
  );
}
