// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (NeedsMap2D, NeedsMapVenn, NeedsMapBars)
'use client';

import React from 'react';

interface Position {
  x: number;
  y: number;
}

interface NeedsMapProps {
  positionA: Position;
  positionB: Position | null;
  axisX: string;
  axisY?: string;
  labelA?: string;
  labelB?: string;
  reasonA?: string;
  reasonB?: string;
  size?: number;
  variant?: '2d' | 'venn' | 'bars';
}

export function NeedsMap({
  positionA,
  positionB,
  axisX,
  axisY = '연결',
  labelA = '서현',
  labelB = '준호',
  reasonA,
  reasonB,
  size = 280,
  variant = '2d',
}: NeedsMapProps) {
  // Map position range -100..100 to pixel coordinates
  // x: 0→left 50%, 100→left 100% (right edge)
  // y: 0→top 50%, 100→top 100% (bottom edge)
  const mapX = (x: number) => {
    const normalized = (x + 100) / 200; // -100..100 → 0..1
    return normalized * 100;
  };
  const mapY = (y: number) => {
    const normalized = (y + 100) / 200;
    return normalized * 100;
  };

  if (variant === 'venn') {
    return (
      <div style={{ position: 'relative', width: size, height: size * 0.7, margin: '0 auto' }}>
        <svg viewBox="0 0 280 196" width={size} height={size * 0.7}>
          <circle cx="100" cy="98" r="78" fill="var(--P-a)" opacity="0.42" />
          <circle cx="180" cy="98" r="78" fill="var(--P-b)" opacity="0.42" />
          <text x="60" y="102" fontSize="14" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">
            {axisX.split(' ')[0]}
          </text>
          <text x="200" y="102" fontSize="14" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">
            {axisY}
          </text>
          <text x="130" y="102" fontSize="11" fill="var(--P-ink)" fontFamily="var(--font-serif)" fontStyle="italic">
            함께
          </text>
          <text x="40" y="30" fontSize="12" fill="var(--P-ink)" fontWeight="500">
            {labelA}
          </text>
          <text x="210" y="30" fontSize="12" fill="var(--P-ink)" fontWeight="500">
            {labelB}
          </text>
        </svg>
      </div>
    );
  }

  if (variant === 'bars') {
    // Extract axis labels
    const axisLeft = axisX.split('–')[0].trim();
    const axisRight = axisX.split('–')[1]?.trim() || '연결';
    const axisTop = axisY?.split('–')[0].trim() || '안정';
    const axisBottom = axisY?.split('–')[1]?.trim() || '변화';

    const posAX = mapX(positionA.x);
    const posBX = positionB ? mapX(positionB.x) : 50;
    const posAY = mapY(positionA.y);
    const posBY = positionB ? mapY(positionB.y) : 50;

    return (
      <div style={{ width: size, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {[
          [axisLeft, axisRight, posAX, posBX],
          [axisTop, axisBottom, posAY, posBY],
        ].map(([l, r, a, b], i) => (
          <div key={i}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 12,
                color: 'var(--P-sub)',
                fontFamily: 'var(--font-serif)',
                fontStyle: 'italic',
                marginBottom: 6,
              }}
            >
              <span>{l}</span>
              <span>{r}</span>
            </div>
            <div
              style={{
                position: 'relative',
                height: 10,
                background: 'var(--P-card)',
                border: '1px solid var(--P-border)',
                borderRadius: 5,
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  top: '50%',
                  left: `${(a as number) * 100}%`,
                  transform: 'translate(-50%,-50%)',
                  width: 14,
                  height: 14,
                  borderRadius: '50%',
                  background: 'var(--P-a)',
                }}
              />
              {positionB && (
                <div
                  style={{
                    position: 'absolute',
                    top: '50%',
                    left: `${(b as number) * 100}%`,
                    transform: 'translate(-50%,-50%)',
                    width: 14,
                    height: 14,
                    borderRadius: '50%',
                    background: 'var(--P-b)',
                  }}
                />
              )}
            </div>
          </div>
        ))}
      </div>
    );
  }

  // Default 2D variant
  const posAPercX = mapX(positionA.x);
  const posAPercY = mapY(positionA.y);
  const posBPercX = positionB ? mapX(positionB.x) : 50;
  const posBPercY = positionB ? mapY(positionB.y) : 50;

  const axisLeft = axisX.split('–')[0].trim();
  const axisRight = axisX.split('–')[1]?.trim() || '연결';
  const axisTop = axisY?.split('–')[0].trim() || '안정';
  const axisBottom = axisY?.split('–')[1]?.trim() || '변화';

  return (
    <div style={{ position: 'relative', width: size, height: size, margin: '0 auto' }}>
      {/* Axes */}
      <div style={{ position: 'absolute', top: '50%', left: 0, right: 0, height: 1, background: 'var(--P-border)' }} />
      <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, width: 1, background: 'var(--P-border)' }} />

      {/* Tick grid */}
      {[0.25, 0.75].map((p) => (
        <React.Fragment key={p}>
          <div
            style={{
              position: 'absolute',
              top: `${p * 100}%`,
              left: 0,
              right: 0,
              height: 1,
              background: 'var(--P-border)',
              opacity: 0.5,
            }}
          />
          <div
            style={{
              position: 'absolute',
              left: `${p * 100}%`,
              top: 0,
              bottom: 0,
              width: 1,
              background: 'var(--P-border)',
              opacity: 0.5,
            }}
          />
        </React.Fragment>
      ))}

      {/* Axis labels */}
      <div
        style={{
          position: 'absolute',
          top: -14,
          left: '50%',
          transform: 'translateX(-50%)',
          fontFamily: 'var(--font-serif)',
          fontStyle: 'italic',
          fontSize: 13,
          color: 'var(--P-sub)',
        }}
      >
        {axisTop}
      </div>
      <div
        style={{
          position: 'absolute',
          bottom: -14,
          left: '50%',
          transform: 'translateX(-50%)',
          fontFamily: 'var(--font-serif)',
          fontStyle: 'italic',
          fontSize: 13,
          color: 'var(--P-sub)',
        }}
      >
        {axisBottom}
      </div>
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: -6,
          transform: 'translate(-100%, -50%)',
          fontFamily: 'var(--font-serif)',
          fontStyle: 'italic',
          fontSize: 13,
          color: 'var(--P-sub)',
        }}
      >
        {axisLeft}
      </div>
      <div
        style={{
          position: 'absolute',
          top: '50%',
          right: -6,
          transform: 'translate(100%, -50%)',
          fontFamily: 'var(--font-serif)',
          fontStyle: 'italic',
          fontSize: 13,
          color: 'var(--P-sub)',
        }}
      >
        {axisRight}
      </div>

      {/* A dot */}
      <div style={{ position: 'absolute', top: `${posAPercY}%`, left: `${posAPercX}%`, transform: 'translate(-50%,-50%)' }}>
        <div
          style={{
            width: 22,
            height: 22,
            borderRadius: '50%',
            background: 'var(--P-a)',
            boxShadow: '0 2px 10px rgba(244,168,150,0.5)',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: -4,
            left: 28,
            fontSize: 12,
            color: 'var(--P-ink)',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: 3,
          }}
        >
          {labelA}
          {reasonA && (
            <span
              title={reasonA}
              style={{ cursor: 'help', fontSize: 10, color: 'var(--P-sub)', lineHeight: 1 }}
            >
              ?
            </span>
          )}
        </div>
      </div>

      {/* B dot or placeholder */}
      {positionB ? (
        <div style={{ position: 'absolute', top: `${posBPercY}%`, left: `${posBPercX}%`, transform: 'translate(-50%,-50%)' }}>
          <div
            style={{
              width: 22,
              height: 22,
              borderRadius: '50%',
              background: 'var(--P-b)',
              boxShadow: '0 2px 10px rgba(168,200,180,0.5)',
            }}
          />
          <div
            style={{
              position: 'absolute',
              top: -4,
              right: 28,
              fontSize: 12,
              color: 'var(--P-ink)',
              fontWeight: 500,
              display: 'flex',
              alignItems: 'center',
              gap: 3,
            }}
          >
            {labelB}
            {reasonB && (
              <span
                title={reasonB}
                style={{ cursor: 'help', fontSize: 10, color: 'var(--P-sub)', lineHeight: 1 }}
              >
                ?
              </span>
            )}
          </div>
        </div>
      ) : (
        <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)' }}>
          <div
            style={{
              width: 22,
              height: 22,
              borderRadius: '50%',
              background: 'transparent',
              border: '2px dashed var(--P-border)',
            }}
          />
          <div
            style={{
              position: 'absolute',
              top: 28,
              left: '50%',
              transform: 'translateX(-50%)',
              fontSize: 12,
              color: 'var(--P-sub)',
              whiteSpace: 'nowrap',
            }}
          >
            아직 비어있어요
          </div>
        </div>
      )}

      {/* Connecting dashed line */}
      {positionB && (
        <svg style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }} viewBox={`0 0 ${size} ${size}`}>
          <line
            x1={size * (posAPercX / 100)}
            y1={size * (posAPercY / 100)}
            x2={size * (posBPercX / 100)}
            y2={size * (posBPercY / 100)}
            stroke="var(--P-sub)"
            strokeWidth="1"
            strokeDasharray="3 4"
            opacity="0.6"
          />
        </svg>
      )}
    </div>
  );
}
