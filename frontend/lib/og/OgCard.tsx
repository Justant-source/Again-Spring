/**
 * OG 카드 JSX (Satori / next/og ImageResponse 용).
 *
 * Satori 제약: flexbox 서브셋만 (grid 없음, CSS var 없음, 웹폰트 없음).
 * 색상은 factionColors.ts SSOT 기준.
 */
import type { OgPostData } from './fetchPostForOg';
import { OG_CAT_LABELS } from './fetchPostForOg';

// ── 색상 상수 (factionColors.ts SSOT) ─────────────────────────────
const AUTHOR_COLOR = '#C9785A';   // 작성자 피치
const AUTHOR_DK    = '#A55C3E';   // 작성자 라벨
const AUTHOR_MUTED = '#F6E6DD';   // votes=0 시 muted fill
const PARTNER_COLOR = '#5F8F76';  // 상대방 세이지
const PARTNER_DK    = '#487961';  // 상대방 라벨
const PARTNER_MUTED = '#E6EFE8';  // votes=0 시 muted fill

const BRAND_INK  = '#2E3A2E';
const BRAND_SUB  = '#7C8A77';
const BRAND_BG   = '#EDF1E8';
const BRAND_CARD = '#F7F9F2';
const BRAND_BORDER = '#D3DCC9';

// ── Satori는 React 없이 JSX만 소비 — 타입 alias ──────────────────
type CSSProp = Record<string, string | number>;

interface OgCardProps {
  /** null → fallback 카드 (비공개/삭제/오류) */
  data: OgPostData | null;
}

export function OgCard({ data }: OgCardProps) {
  // ── fallback 카드 ────────────────────────────────────────────────
  if (!data) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          width: '100%',
          height: '100%',
          background: BRAND_BG,
          border: `1px solid ${BRAND_BORDER}`,
          borderRadius: 24,
          padding: 64,
          fontFamily: 'NotoSansKR',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <div style={{ display: 'flex', fontSize: 28, fontWeight: 700, color: BRAND_INK, marginBottom: 16 }}>
          다시봄
        </div>
        <div style={{ display: 'flex', fontSize: 22, color: BRAND_SUB, textAlign: 'center' }}>
          관계 회복을 돕는 AI 중재자
        </div>
        <div style={{ display: 'flex', marginTop: 24, fontSize: 16, color: BRAND_SUB }}>
          againspring.net
        </div>
      </div>
    );
  }

  const { title, category, authorPct, partnerPct, totalVotes } = data;
  const hasVotes = totalVotes > 0;

  // 비율 바 색상 — votes 없으면 muted
  const authorFill  = hasVotes ? AUTHOR_COLOR  : AUTHOR_MUTED;
  const partnerFill = hasVotes ? PARTNER_COLOR : PARTNER_MUTED;

  // 세그먼트 너비가 너무 좁으면 % 텍스트 숨김 (16% 미만)
  const showAuthorPct  = authorPct  >= 16;
  const showPartnerPct = partnerPct >= 16;

  const catLabel = OG_CAT_LABELS[(category || '').toUpperCase()] ?? '기타';

  const pillStyle: CSSProp = {
    display: 'flex',
    alignItems: 'center',
    background: BRAND_CARD,
    borderRadius: 999,
    padding: '6px 16px',
    fontSize: 20,
    color: BRAND_SUB,
    fontWeight: 400,
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        width: '100%',
        height: '100%',
        background: BRAND_BG,
        padding: '60px 72px',
        fontFamily: 'NotoSansKR',
      }}
    >
      {/* 헤더: 브랜드명 + 카테고리 pill */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <span style={{ display: 'flex', fontSize: 28, fontWeight: 700, color: BRAND_INK }}>
          다시봄
        </span>
        <span style={pillStyle}>{catLabel}</span>
      </div>

      {/* 제목 — 최대 2줄, 폰트 크기 캡 */}
      <div
        style={{
          display: 'flex',
          marginTop: 40,
          fontSize: title.length > 22 ? 40 : 48,
          fontWeight: 700,
          color: BRAND_INK,
          lineHeight: 1.35,
          // Satori: overflow/line-clamp 지원 제한적 → fetchPostForOg 에서 하드 truncate 보장
        }}
      >
        {title}
      </div>

      {/* 비율 바 */}
      <div
        style={{
          display: 'flex',
          marginTop: 48,
          height: 96,
          borderRadius: 16,
          overflow: 'hidden',
          border: `1px solid ${BRAND_BORDER}`,
        }}
      >
        {/* 작성자(피치) 세그먼트 */}
        <div
          style={{
            display: 'flex',
            width: `${authorPct}%`,
            background: authorFill,
            alignItems: 'center',
            paddingLeft: 28,
          }}
        >
          {showAuthorPct && (
            <span
              style={{
                display: 'flex',
                fontSize: 30,
                fontWeight: 700,
                color: '#FFFFFF',
              }}
            >
              작성자 {authorPct}%
            </span>
          )}
        </div>
        {/* 상대방(세이지) 세그먼트 */}
        <div
          style={{
            display: 'flex',
            flex: 1,
            background: partnerFill,
            alignItems: 'center',
            justifyContent: 'flex-end',
            paddingRight: 28,
          }}
        >
          {showPartnerPct && (
            <span
              style={{
                display: 'flex',
                fontSize: 30,
                fontWeight: 700,
                color: '#FFFFFF',
              }}
            >
              {partnerPct}% 상대방
            </span>
          )}
        </div>
      </div>

      {/* 라벨 행 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: 16,
        }}
      >
        <span style={{ display: 'flex', fontSize: 22, color: AUTHOR_DK }}>
          작성자에 공감
        </span>
        <span style={{ display: 'flex', fontSize: 22, color: PARTNER_DK }}>
          상대방에 공감
        </span>
      </div>

      {/* 푸터 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          marginTop: 'auto',
        }}
      >
        <span style={{ display: 'flex', fontSize: 24, color: BRAND_SUB }}>
          {hasVotes
            ? `${totalVotes.toLocaleString('ko-KR')}명이 함께 봤어요`
            : '아직 공감을 기다리고 있어요'}
        </span>
        <span style={{ display: 'flex', fontSize: 20, color: BRAND_SUB }}>
          againspring.net
        </span>
      </div>
    </div>
  );
}
