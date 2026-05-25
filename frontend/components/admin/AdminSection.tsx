import React from 'react';

interface AdminSectionProps {
  title: string;
  subtitle?: string;
  badge?: { text: string; color: string };
  children: React.ReactNode;
  className?: string;
}

export function AdminSection({
  title, subtitle, badge, children, className,
}: AdminSectionProps) {
  return (
    <div
      className={className}
      style={{
        marginBottom: 22,
        padding: 20,
        background: 'white',
        borderRadius: 12,
        border: '1px solid #e7e3d8',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: subtitle ? 4 : 14 }}>
        <h2 style={{ fontSize: 15, fontWeight: 600, color: '#1A1A2E', margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          {title}
          {badge && (
            <span
              style={{
                background: badge.color, color: 'white',
                padding: '2px 8px', borderRadius: 10, fontSize: 11, fontWeight: 600,
              }}
            >
              {badge.text}
            </span>
          )}
        </h2>
      </div>
      {subtitle && <p style={{ fontSize: 11, color: '#888', margin: '0 0 14px' }}>{subtitle}</p>}
      {children}
    </div>
  );
}
