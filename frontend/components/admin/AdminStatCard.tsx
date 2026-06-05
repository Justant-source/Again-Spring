import React from 'react';
import { Card } from '@/components/ui/card';

interface AdminStatCardProps {
  label: string;
  value: string | number;
  delta?: string;
  deltaPositive?: boolean;
}

export function AdminStatCard({ label, value, delta, deltaPositive }: AdminStatCardProps) {
  return (
    <Card className="p-4 border">
      <div className="text-xs text-gray-500 mb-2">{label}</div>
      <div className="flex items-baseline gap-2">
        <div className="text-2xl font-semibold text-gray-900">{value}</div>
        {delta && (
          <div
            className={`text-xs font-medium ${
              deltaPositive ? 'text-green-700' : 'text-red-600'
            }`}
          >
            {deltaPositive ? '▲' : '▼'} {delta}
          </div>
        )}
      </div>
    </Card>
  );
}
