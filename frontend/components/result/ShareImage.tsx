'use client';

import { ShareCardBlurredLetter } from './ShareCardBlurredLetter';
import { ShareCardMetaphor } from './ShareCardMetaphor';
import { ShareCardRatio } from './ShareCardRatio';
import { ShareCardHorsemen } from './ShareCardHorsemen';
import type { Report, CommunicationStyle } from '@/lib/types';

export type ShareCardVariant = 'b' | 'c' | 'd' | 'e';

interface ShareImageProps {
  variant: ShareCardVariant;
  report: Report;
  styleA?: CommunicationStyle;
  styleB?: CommunicationStyle;
  nameA?: string;
  nameB?: string;
}

export function ShareImage({ variant, report, nameA, nameB }: ShareImageProps) {
  switch (variant) {
    case 'b':
      return <ShareCardBlurredLetter report={report} nameA={nameA} />;
    case 'c':
      return <ShareCardMetaphor report={report} />;
    case 'd':
      return <ShareCardRatio report={report} nameA={nameA} nameB={nameB} />;
    case 'e':
      return <ShareCardHorsemen report={report} />;
    default:
      return null;
  }
}

export function renderShareImageHTML(
  variant: ShareCardVariant,
  report: Report,
  nameA = '서현',
  nameB = '준호',
): string {
  return `<!-- Share Image: ${variant} -->`;
}
