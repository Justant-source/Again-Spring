/**
 * 중재자 메시지 분할 유틸리티
 * 중재자 메시지가 4줄을 초과하거나 300자 이상이면 두 부분으로 나눔
 */

export interface SplitResult {
  first: string;
  second: string;
}

/**
 * 중재자 메시지를 분할할 필요가 있는지 판단하고, 필요하면 분할
 * @param content 메시지 본문
 * @returns 분할이 필요하면 {first, second}, 아니면 null
 */
export function splitMediatorMessage(content: string): SplitResult | null {
  const lines = content.split('\n');
  const isLong = lines.length > 4 || content.length >= 300;

  if (!isLong) return null;

  const mid = Math.floor(content.length / 2);
  const searchRange = 100; // mid 근처 ±100자 범위 내에서 분할점 찾기

  // mid 근처에서 문장 끝 찾기 (。, !, ?, 또는 \n)
  let bestSplit = mid;
  let bestDistance = Number.MAX_VALUE;

  // 문장 끝 패턴: 。, !, ?, ? (그 뒤의 공백은 옵션)
  const sentenceEndPattern = /[.!?。]\s*/g;
  let match;

  while ((match = sentenceEndPattern.exec(content)) !== null) {
    const splitPoint = match.index + match[0].length;
    const distance = Math.abs(splitPoint - mid);

    if (distance < bestDistance && distance <= searchRange) {
      bestDistance = distance;
      bestSplit = splitPoint;
    }
  }

  // 문장 끝을 못 찾은 경우, \n을 기준으로 분할 시도
  if (bestDistance === Number.MAX_VALUE) {
    const newlinePattern = /\n/g;
    while ((match = newlinePattern.exec(content)) !== null) {
      const splitPoint = match.index + 1; // \n 다음 위치
      const distance = Math.abs(splitPoint - mid);

      if (distance < bestDistance && distance <= searchRange) {
        bestDistance = distance;
        bestSplit = splitPoint;
      }
    }
  }

  // 여전히 분할점을 못 찾은 경우, mid 근처 공백 기준으로 분할
  if (bestDistance === Number.MAX_VALUE) {
    const start = Math.max(0, mid - searchRange);
    const end = Math.min(content.length, mid + searchRange);
    const searchArea = content.substring(start, end);

    const spaceIndex = searchArea.lastIndexOf(' ');
    if (spaceIndex !== -1) {
      bestSplit = start + spaceIndex + 1;
    } else {
      // 공백도 못 찾으면 정확히 절반
      bestSplit = mid;
    }
  }

  const first = content.slice(0, bestSplit).trim();
  const second = content.slice(bestSplit).trim();

  // 두 부분 모두 유효한지 확인
  if (!first || !second) return null;

  return { first, second };
}

/**
 * 두 번째 부분 길이 기반으로 지연 시간 계산
 * 기본값: 2000~3000ms (secondPart 길이에 비례, 1글자당 약 15ms)
 * @param secondPartLength 두 번째 부분의 글자 수
 * @returns 지연 시간 (ms)
 */
export function calculateTypingDelay(secondPartLength: number): number {
  const baseDelay = Math.max(2000, Math.min(3000, secondPartLength * 15));
  return baseDelay;
}
