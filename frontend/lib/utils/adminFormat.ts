/**
 * 어드민 대시보드 날짜/숫자 포맷 공용 유틸
 * 분산된 toLocaleString/toLocaleDateString 호출을 통일
 */

/**
 * ISO 문자열 → 날짜만 표기 (ko-KR 기본 포맷)
 * 예: "2026-07-30T14:25:00Z" → "2026. 7. 30"
 */
export function formatDate(iso?: string | null): string {
  if (!iso) return '';
  try {
    const date = new Date(iso);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleDateString('ko-KR');
  } catch {
    return '';
  }
}

/**
 * ISO 문자열 → 날짜+시간 표기 (reports 페이지 표준)
 * 예: "2026-07-30T14:25:00Z" → "2026.07.30 14:25"
 */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '';
  try {
    const date = new Date(iso);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

/**
 * 숫자 → 천단위 구분 표기 (ko-KR 로케일)
 * 예: 1234567 → "1,234,567"
 */
export function formatNumber(n?: number | null): string {
  if (n == null) return '';
  try {
    return n.toLocaleString('ko-KR');
  } catch {
    return String(n);
  }
}
