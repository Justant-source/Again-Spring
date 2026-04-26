'use client';

import Image from 'next/image';
import { getMetaphorById, getMetaphorImagePath } from '@/lib/constants/metaphors';
import type { MetaphorCard } from '@/lib/types';

const COLORS = {
  lavender: { bg: '#F0ECFB', border: '#C4B5F5', accent: '#7C5CBF', badge: '#EDE8F8' },
  green:    { bg: '#EDFBF0', border: '#A8D8B0', accent: '#3A8C4C', badge: '#E5F7E8' },
  pink:     { bg: '#FDF0F4', border: '#F5C0CF', accent: '#C44A70', badge: '#FCEAF0' },
};

const CARD_TITLES_DEFAULT = ['두 분의 욕구', '함께 자라는 길', '다음 한 걸음'];

interface MetaphorCardsProps {
  metaphorId?: string; // V1.5: 12종 SVG 일러스트 기반
  cards?: MetaphorCard[]; // 기존 텍스트 카드 (fallback)
  mode?: 'solo' | 'pair';
}

export function MetaphorCards({ metaphorId, cards, mode = 'pair' }: MetaphorCardsProps) {
  // V1.5: metaphorId가 있으면 SVG 일러스트 카드 우선
  if (metaphorId) {
    const metaphor = getMetaphorById(metaphorId);
    if (!metaphor) return null;

    return (
      <div>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>
          {mode === 'solo' ? '나의 마음은' : '두 분의 마음은'}
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 16,
            padding: '20px 0',
          }}
        >
          <Image
            src={getMetaphorImagePath(metaphor.filename)}
            alt={metaphor.label}
            width={200}
            height={200}
            style={{ display: 'block' }}
          />
          <div className="serif" style={{ fontSize: 20, textAlign: 'center', lineHeight: 1.4 }}>
            <strong>{metaphor.label}</strong> 같아요
          </div>
          <div
            style={{
              fontSize: 13,
              color: 'var(--P-sub)',
              textAlign: 'center',
              lineHeight: 1.7,
              maxWidth: 240,
            }}
          >
            {metaphor.meaning}
          </div>
        </div>
      </div>
    );
  }

  // Fallback: 기존 텍스트 카드
  if (!cards || cards.length === 0) return null;

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>
        {mode === 'solo' ? '나의 이야기' : '두 분의 이야기'}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {cards.map((card, i) => {
          const c = COLORS[card.color];
          return (
            <div
              key={i}
              style={{
                background: c.bg,
                border: `1px solid ${c.border}`,
                borderRadius: 14,
                padding: '16px 18px',
              }}
            >
              <div
                style={{
                  display: 'inline-block',
                  fontSize: 11,
                  color: c.accent,
                  background: c.badge,
                  borderRadius: 6,
                  padding: '2px 8px',
                  marginBottom: 8,
                  fontWeight: 500,
                }}
              >
                {CARD_TITLES_DEFAULT[i] ?? `카드 ${i + 1}`}
              </div>
              <div
                className="serif"
                style={{ fontSize: 15, fontWeight: 600, color: c.accent, marginBottom: 8, lineHeight: 1.4 }}
              >
                {card.title}
              </div>
              <div style={{ fontSize: 13, color: '#4A4A48', lineHeight: 1.75 }}>
                {card.body}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
