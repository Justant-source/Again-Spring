import React from 'react';

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
    return <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중…</p>;
  }

  if (!data || data.length === 0) {
    return <p style={{ color: '#aaa', fontSize: 13, padding: '12px 4px' }}>{emptyMessage}</p>;
  }

  return (
    <div style={{ overflowX: 'auto' }} className={className}>
      <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: '#f5f5f5' }}>
            {columns.map((col) => (
              <th
                key={col.key}
                style={{ padding: '8px 10px', textAlign: 'left', fontWeight: 600, fontSize: 12 }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row, idx) => {
            const key = rowKey ? rowKey(row) : idx;
            return (
              <tr
                key={key}
                onClick={() => onRowClick?.(row)}
                style={{
                  borderBottom: '1px solid #eee',
                  cursor: onRowClick ? 'pointer' : 'default',
                }}
                onMouseEnter={(e) => {
                  if (onRowClick) {
                    e.currentTarget.style.background = '#fafaf5';
                  }
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'transparent';
                }}
              >
                {columns.map((col) => (
                  <td key={col.key} style={{ padding: '8px 10px' }}>
                    {col.render ? col.render(row) : String((row as any)[col.key] || '')}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
