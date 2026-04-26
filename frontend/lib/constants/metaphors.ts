export interface Metaphor {
  id: string;
  filename: string;
  label: string;
  meaning: string;
  group: 'avoidance' | 'tension' | 'protection' | 'loneliness' | 'hesitation' | 'recovery';
}

export const METAPHORS: Metaphor[] = [
  {
    id: 'locked-mailbox',
    filename: '01-locked-mailbox.svg',
    label: '잠겨있는 우체통',
    meaning: '마음을 받았는데 열어보지 않은 채 쌓여있는 상태',
    group: 'avoidance',
  },
  {
    id: 'boiling-kettle',
    filename: '02-boiling-kettle.svg',
    label: '끓는 주전자',
    meaning: '작은 일에도 곧 터질 것 같이 끓고 있는 상태',
    group: 'tension',
  },
  {
    id: 'locked-door',
    filename: '03-locked-door.svg',
    label: '걸어 잠근 문',
    meaning: '더 이상 들어올 수 없게 마음의 빗장을 채운 상태',
    group: 'avoidance',
  },
  {
    id: 'too-big-umbrella',
    filename: '04-too-big-umbrella.svg',
    label: '너무 큰 우산',
    meaning: '상대를 지키려다 오히려 거리감을 만든 상태',
    group: 'protection',
  },
  {
    id: 'person-in-rain',
    filename: '05-person-in-rain.svg',
    label: '비 맞는 사람',
    meaning: '누군가 알아봐주길 기다리며 그대로 서있는 상태',
    group: 'loneliness',
  },
  {
    id: 'frozen-pond',
    filename: '06-frozen-pond.svg',
    label: '얼어붙은 연못',
    meaning: '흐르지 못하고 멈춰버린 감정',
    group: 'avoidance',
  },
  {
    id: 'cracked-window',
    filename: '07-cracked-window.svg',
    label: '금 간 유리창',
    meaning: '깨지지는 않았지만 작은 충격에도 흔들리는 상태',
    group: 'tension',
  },
  {
    id: 'empty-chair',
    filename: '08-empty-chair.svg',
    label: '빈 의자',
    meaning: '함께 있어도 마음은 없는 자리',
    group: 'avoidance',
  },
  {
    id: 'overflowing-cup',
    filename: '09-overflowing-cup.svg',
    label: '넘치는 컵',
    meaning: '더 이상 받아들일 수 없을 만큼 가득 찬 상태',
    group: 'tension',
  },
  {
    id: 'rope-bridge',
    filename: '10-rope-bridge.svg',
    label: '흔들리는 다리',
    meaning: '건너고 싶지만 무서워서 머뭇거리는 관계',
    group: 'hesitation',
  },
  {
    id: 'half-open-letter',
    filename: '11-half-open-letter.svg',
    label: '반쯤 열린 편지',
    meaning: '말하고 싶은데 끝까지 못 한 마음',
    group: 'hesitation',
  },
  {
    id: 'two-trees-roots',
    filename: '12-two-trees-roots.svg',
    label: '뿌리 얽힌 두 나무',
    meaning: '떨어져 보여도 깊은 곳은 연결되어 있어요',
    group: 'recovery',
  },
];

export function getMetaphorById(id: string): Metaphor | undefined {
  return METAPHORS.find((m) => m.id === id);
}

export function getMetaphorImagePath(filename: string): string {
  return `/illustrations/metaphors/${filename}`;
}
