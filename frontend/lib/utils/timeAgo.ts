/**
 * createdAt(ISO) → 광장 피드 카드용 상대 시간 표기
 * "방금" / "N분 전" / "N시간 전" / "N일 전" / "YYYY.MM.DD"
 */
export function timeAgo(iso?: string): string {
  if (!iso) return '';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const diffSec = Math.max(0, Math.floor((Date.now() - then) / 1000));
  if (diffSec < 60) return '방금';

  const min = Math.floor(diffSec / 60);
  if (min < 60) return `${min}분 전`;

  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;

  const day = Math.floor(hr / 24);
  if (day < 7) return `${day}일 전`;

  const d = new Date(then);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}
