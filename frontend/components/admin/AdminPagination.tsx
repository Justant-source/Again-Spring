import React from 'react';

interface AdminPaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  loading?: boolean;
  className?: string;
}

export function AdminPagination({
  page, totalPages, onPageChange, loading, className,
}: AdminPaginationProps) {
  const pagerBtnStyle = (disabled: boolean): React.CSSProperties => ({
    padding: '6px 10px',
    fontSize: 12,
    background: disabled ? '#f5f5f5' : 'white',
    color: disabled ? '#aaa' : '#333',
    border: '1px solid #ddd',
    borderRadius: 4,
    cursor: disabled ? 'not-allowed' : 'pointer',
  });

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        gap: 6,
        marginTop: 16,
      }}
      className={className}
    >
      <button
        onClick={() => onPageChange(0)}
        disabled={page === 0 || loading}
        style={pagerBtnStyle(page === 0 || loading || false)}
      >
        « 처음
      </button>
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0 || loading}
        style={pagerBtnStyle(page === 0 || loading || false)}
      >
        ‹ 이전
      </button>
      <span style={{ fontSize: 12, color: '#555', padding: '0 12px' }}>
        {page + 1} / {totalPages}
      </span>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1 || loading}
        style={pagerBtnStyle(page >= totalPages - 1 || loading || false)}
      >
        다음 ›
      </button>
      <button
        onClick={() => onPageChange(totalPages - 1)}
        disabled={page >= totalPages - 1 || loading}
        style={pagerBtnStyle(page >= totalPages - 1 || loading || false)}
      >
        마지막 »
      </button>
    </div>
  );
}
