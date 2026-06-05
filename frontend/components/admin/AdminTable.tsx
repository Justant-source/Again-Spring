import React from 'react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

interface ColumnDef<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
}

interface AdminTableProps<T> {
  data: T[];
  columns: ColumnDef<T>[];
  loading?: boolean;
  emptyMessage?: string;
  className?: string;
  rowKey?: (row: T) => string | number;
  onRowClick?: (row: T) => void;
}

export function AdminTable<T>({
  data,
  columns,
  loading,
  emptyMessage = '데이터 없음',
  className,
  rowKey,
  onRowClick,
}: AdminTableProps<T>) {
  if (loading) {
    return <p className="text-sm text-gray-500 p-3">불러오는 중…</p>;
  }

  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-400 p-3">{emptyMessage}</p>;
  }

  return (
    <div className={cn('overflow-x-auto', className)}>
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((col) => (
              <TableHead key={col.key} className="font-semibold">
                {col.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((row, idx) => {
            const key = rowKey ? rowKey(row) : idx;
            return (
              <TableRow
                key={key}
                onClick={() => onRowClick?.(row)}
                className={onRowClick ? 'cursor-pointer hover:bg-gray-50' : ''}
              >
                {columns.map((col) => (
                  <TableCell key={col.key}>
                    {col.render ? col.render(row) : String((row as any)[col.key] || '')}
                  </TableCell>
                ))}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

export default AdminTable;
