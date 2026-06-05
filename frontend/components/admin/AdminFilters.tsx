import React from 'react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

interface FilterOption {
  value: string;
  label: string;
}

interface AdminFiltersProps {
  search?: {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
  };
  status?: {
    value: string;
    onChange: (value: string) => void;
    options: FilterOption[];
  };
  className?: string;
}

export function AdminFilters({ search, status, className }: AdminFiltersProps) {
  return (
    <div className={cn('flex flex-wrap gap-3 items-center mb-4', className)}>
      {search && (
        <Input
          value={search.value}
          onChange={(e) => search.onChange(e.target.value)}
          placeholder={search.placeholder || '검색'}
          className="flex-1 min-w-[200px]"
        />
      )}
      {status && (
        <>
          <span className="text-xs text-gray-600 font-medium">필터:</span>
          <div className="flex gap-2">
            {status.options.map((opt) => (
              <Button
                key={opt.value}
                variant={status.value === opt.value ? 'default' : 'outline'}
                size="sm"
                onClick={() => status.onChange(opt.value)}
              >
                {opt.label}
              </Button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
