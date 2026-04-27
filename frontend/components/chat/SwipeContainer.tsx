'use client';

import { useState, useRef, ReactNode, useEffect } from 'react';

interface Props {
  children: ReactNode[];
  hint?: string;
}

export function SwipeContainer({ children, hint }: Props) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [showHint, setShowHint] = useState(true);
  const touchStartX = useRef<number | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setShowHint(false), 4000);
    return () => clearTimeout(t);
  }, []);

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null) return;
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    if (Math.abs(dx) < 60) return;
    if (dx < 0 && activeIndex < children.length - 1) {
      setActiveIndex(activeIndex + 1);
      setShowHint(false);
    } else if (dx > 0 && activeIndex > 0) {
      setActiveIndex(activeIndex - 1);
    }
    touchStartX.current = null;
  };

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        overflow: 'hidden',
      }}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      <div
        style={{
          display: 'flex',
          width: '200%',
          height: '100%',
          transform: `translateX(-${activeIndex * 50}%)`,
          transition: 'transform 0.32s cubic-bezier(0.25, 0.1, 0.25, 1)',
        }}
      >
        {children.map((child, i) => (
          <div key={i} style={{ width: '50%', height: '100%', flexShrink: 0 }}>
            {child}
          </div>
        ))}
      </div>

      {/* 인디케이터 */}
      <div
        style={{
          position: 'absolute',
          bottom: 70,
          left: '50%',
          transform: 'translateX(-50%)',
          display: 'flex',
          gap: 6,
          zIndex: 10,
        }}
      >
        {children.map((_, i) => (
          <div
            key={i}
            style={{
              width: i === activeIndex ? 16 : 6,
              height: 6,
              borderRadius: 3,
              background:
                i === activeIndex ? 'var(--P-ink)' : 'var(--P-border)',
              transition: 'all 0.2s',
            }}
          />
        ))}
      </div>

      {/* 스와이프 힌트 */}
      {showHint && hint && activeIndex === 0 && (
        <div
          style={{
            position: 'absolute',
            right: 14,
            top: '50%',
            transform: 'translateY(-50%)',
            fontSize: 11,
            color: 'var(--P-sub)',
            opacity: 0.7,
            pointerEvents: 'none',
            background: 'var(--P-card)',
            padding: '6px 10px',
            borderRadius: 14,
            border: '1px solid var(--P-border)',
          }}
        >
          {hint}
        </div>
      )}
    </div>
  );
}
