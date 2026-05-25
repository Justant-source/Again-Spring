import React from 'react';

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
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }} className={className}>
      {search && (
        <input
          value={search.value}
          onChange={(e) => search.onChange(e.target.value)}
          placeholder={search.placeholder || '검색'}
          style={{
            flex: 1,
            minWidth: 200,
            padding: '9px 12px',
            border: '1px solid #ddd',
            borderRadius: 6,
            fontSize: 13,
          }}
        />
      )}
      {status && (
        <>
          <span style={{ fontSize: 12, color: '#888' }}>필터:</span>
          {status.options.map((opt) => (
            <button
              key={opt.value}
              onClick={() => status.onChange(opt.value)}
              style={{
                padding: '4px 10px',
                borderRadius: 14,
                fontSize: 12,
                border: status.value === opt.value ? '1px solid #1A1A2E' : '1px solid #ddd',
                background: status.value === opt.value ? '#1A1A2E' : 'white',
                color: status.value === opt.value ? 'white' : '#555',
                cursor: 'pointer',
              }}
            >
              {opt.label}
            </button>
          ))}
        </>
      )}
    </div>
  );
}
