import React from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { LineChart, Line, ResponsiveContainer } from 'recharts';

interface AdminStatCardProps {
  label: string;
  value: string | number;
  delta?: string;
  deltaPositive?: boolean;
  sparkline?: number[];
  href?: string;
}

export function AdminStatCard({ label, value, delta, deltaPositive, sparkline, href }: AdminStatCardProps) {
  const content = (
    <Card className={`p-4 border ${href ? 'hover:bg-gray-50 cursor-pointer transition-colors' : ''}`}>
      <div className="text-xs text-gray-500 mb-2">{label}</div>
      <div className="flex items-baseline gap-2 mb-3">
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
      {sparkline && sparkline.length > 0 && (
        <ResponsiveContainer width="100%" height={40}>
          <LineChart data={sparkline.map((v, i) => ({ value: v }))} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
            <Line
              type="monotone"
              dataKey="value"
              stroke="#5F8F76"
              dot={false}
              strokeWidth={1.5}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </Card>
  );

  if (href) {
    return (
      <a href={href} target="_self">
        {content}
      </a>
    );
  }

  return content;
}
