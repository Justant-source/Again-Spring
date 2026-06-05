import React from 'react';
import { Button } from '@/components/ui/button';
import { ChevronsLeft, ChevronLeft, ChevronRight, ChevronsRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface AdminPaginationProps {
  page?: number;
  currentPage?: number;  // alias for page
  totalPages: number;
  onPageChange: (page: number) => void | Promise<void>;
  loading?: boolean;
  className?: string;
}

export function AdminPagination({
  page: pageProp,
  currentPage,
  totalPages,
  onPageChange,
  loading,
  className,
}: AdminPaginationProps) {
  const page = pageProp ?? (currentPage !== undefined ? currentPage - 1 : 0);
  const handleChange = (p: number) => { void onPageChange(p); };
  const isFirstPage = page === 0;
  const isLastPage = page >= totalPages - 1;

  return (
    <div className={cn('flex justify-center items-center gap-2 mt-4', className)}>
      <Button
        variant="outline"
        size="sm"
        onClick={() => handleChange(0)}
        disabled={isFirstPage || loading}
        title="처음"
      >
        <ChevronsLeft size={16} />
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() => handleChange(page - 1)}
        disabled={isFirstPage || loading}
        title="이전"
      >
        <ChevronLeft size={16} />
      </Button>
      <span className="text-sm text-gray-600 px-3 whitespace-nowrap">
        {page + 1} / {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        onClick={() => handleChange(page + 1)}
        disabled={isLastPage || loading}
        title="다음"
      >
        <ChevronRight size={16} />
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() => handleChange(totalPages - 1)}
        disabled={isLastPage || loading}
        title="마지막"
      >
        <ChevronsRight size={16} />
      </Button>
    </div>
  );
}
