import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

interface AdminSectionProps {
  title: string;
  subtitle?: string;
  badge?: { text: string; color?: string };
  children: React.ReactNode;
  className?: string;
}

export function AdminSection({
  title,
  subtitle,
  badge,
  children,
  className,
}: AdminSectionProps) {
  return (
    <Card className={cn('mb-6', className)}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              {title}
              {badge && (
                <Badge variant="secondary" className={badge.color}>
                  {badge.text}
                </Badge>
              )}
            </CardTitle>
            {subtitle && (
              <p className="text-sm text-gray-500 mt-1">{subtitle}</p>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
