/**
 * 다시봄 메타포 일러스트 카탈로그 (SSOT)
 *
 * SVG 팔레트 (6색 — 일러스트당 최대 3색):
 *   #FFF8F0  크림 웜    — 배경 fill  (기본 포함)
 *   #A08670  웜 브라운  — 주선·획    (기본 포함)
 *   #5C4030  딥 브라운  — 강조선
 *   #F4A896  살몬 핑크  — 감정 포인트
 *   #A8C8B4  세이지 그린 — 자연·희망 포인트
 *   #FBF3EC  연크림     — 섬세한 fill 변형
 *
 * viewBox="0 0 240 240", fill="none"
 * stroke-width 1.5–2, strokeLinecap="round", strokeLinejoin="round"
 * 사람 실루엣 최소화, 단순 사물로 감정 상황 상징
 */

export type MetaphorUiContext =
  | 'report-header'           // 결과 리포트 상단 대표 이미지
  | 'share-card'              // 카카오/SNS 공유 카드
  | 'session-end'             // 세션 종료 화면 장식
  | 'onboarding-intro'        // 온보딩 도입부 배경·장식
  | 'empty-state'             // 빈 상태 화면 (데이터 없음)
  | 'marketing-cover'         // 마케팅 카드뉴스 COVER 슬라이드
  | 'marketing-scene'         // 마케팅 SCENE 슬라이드 (갈등 장면 인용 옆)
  | 'marketing-naver-inline'  // 네이버 블로그 본문 인라인
  | 'marketing-quote-card';   // 마케팅 인용 카드 배경 장식

export type MetaphorRelationType =
  | 'couple'
  | 'marriage'
  | 'friend'
  | 'family'
  | 'parent_child'
  | 'colleague'
  | 'all';

export interface Metaphor {
  id: string;
  filename: string;
  label: string;
  meaning: string;
  group: 'avoidance' | 'tension' | 'protection' | 'loneliness' | 'hesitation' | 'recovery';

  /** NVC 기반 감정 태그 — 이 일러스트가 표현하는 감정 */
  emotions: string[];

  /** NVC 욕구 태그 — 이 일러스트 속에 담긴 욕구 */
  needs: string[];

  /** 앱·마케팅에서 쓰일 UI 위치 */
  uiContexts: MetaphorUiContext[];

  /** 어울리는 관계 유형 */
  relationTypes: MetaphorRelationType[];

  /**
   * 시각 감정 강도
   * warm = 포근·따뜻 / neutral = 중립·차분 / heavy = 무겁·진중
   */
  tone: 'warm' | 'neutral' | 'heavy';

  /**
   * Claude Design 브리프 — 이 일러스트를 새로 만들거나 변형할 때 사용.
   * 형태·시각 특징을 1–2 문장으로 압축.
   */
  designPrompt: string;
}

export const METAPHORS: Metaphor[] = [
  {
    id: 'locked-mailbox',
    filename: '01-locked-mailbox.svg',
    label: '잠겨있는 우체통',
    meaning: '마음을 받았는데 열어보지 않은 채 쌓여있는 상태',
    group: 'avoidance',
    emotions: ['답답함', '서운함', '기다림', '무시당한 느낌'],
    needs: ['소통', '인정', '연결'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '자물쇠가 달린 우체통. 수신함 슬롯·받침대 포함. 닫힌 수직 구조를 강조하되 과하지 않게.',
  },
  {
    id: 'boiling-kettle',
    filename: '02-boiling-kettle.svg',
    label: '끓는 주전자',
    meaning: '작은 일에도 곧 터질 것 같이 끓고 있는 상태',
    group: 'tension',
    emotions: ['분노', '억울함', '좌절감', '터질 것 같은 답답함'],
    needs: ['공정함', '존중', '이해받음'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'family', 'colleague'],
    tone: 'heavy',
    designPrompt:
      '여러 줄 수증기가 솟구치는 주전자. 동적인 곡선 수증기 획 필수, 주둥이·몸체·손잡이 포함.',
  },
  {
    id: 'locked-door',
    filename: '03-locked-door.svg',
    label: '걸어 잠근 문',
    meaning: '더 이상 들어올 수 없게 마음의 빗장을 채운 상태',
    group: 'avoidance',
    emotions: ['거부감', '지침', '단절', '포기하고 싶은 마음'],
    needs: ['공간', '안전', '자율성'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['couple', 'marriage', 'family', 'friend'],
    tone: 'heavy',
    designPrompt:
      '자물쇠와 문고리가 있는 나무 문. 4분할 패널 구조, 견고하고 닫힌 느낌을 구조감으로 표현.',
  },
  {
    id: 'too-big-umbrella',
    filename: '04-too-big-umbrella.svg',
    label: '너무 큰 우산',
    meaning: '상대를 지키려다 오히려 거리감을 만든 상태',
    group: 'protection',
    emotions: ['부담스러움', '답답함', '고마우면서도 불편한', '거리감'],
    needs: ['자율성', '공간', '균형'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['couple', 'marriage', 'family', 'parent_child', 'friend'],
    tone: 'neutral',
    designPrompt:
      '캐노피가 지나치게 큰 우산, 아래 작은 사람 실루엣(원). 보호와 거리감을 비대칭 구조로 표현.',
  },
  {
    id: 'person-in-rain',
    filename: '05-person-in-rain.svg',
    label: '비 맞는 사람',
    meaning: '누군가 알아봐주길 기다리며 그대로 서있는 상태',
    group: 'loneliness',
    emotions: ['외로움', '슬픔', '고립감', '기다림'],
    needs: ['연결', '공감', '돌봄'],
    uiContexts: [
      'report-header',
      'share-card',
      'marketing-cover',
      'marketing-scene',
      'empty-state',
    ],
    relationTypes: ['all'],
    tone: 'heavy',
    designPrompt:
      '빗속에 홀로 선 사람 실루엣(머리 원+몸체 사각형). 대각선 빗줄기 여러 개, 그림자 타원.',
  },
  {
    id: 'frozen-pond',
    filename: '06-frozen-pond.svg',
    label: '얼어붙은 연못',
    meaning: '흐르지 못하고 멈춰버린 감정',
    group: 'avoidance',
    emotions: ['무감각', '무력감', '회피', '멈춰버린 느낌'],
    needs: ['회복', '표현', '흐름'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['couple', 'marriage', 'family'],
    tone: 'heavy',
    designPrompt:
      '유기적 형태 연못 위 균열 패턴. 물가 식물 2–3개, 균열선이 정적인 분위기를 강조.',
  },
  {
    id: 'cracked-window',
    filename: '07-cracked-window.svg',
    label: '금 간 유리창',
    meaning: '깨지지는 않았지만 작은 충격에도 흔들리는 상태',
    group: 'tension',
    emotions: ['불안', '긴장', '불신', '조마조마함'],
    needs: ['신뢰', '안전', '안정'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'heavy',
    designPrompt:
      '창틀 안에 방사형으로 퍼지는 균열. 십자형 창 프레임, 중심 균열점 원, 정밀한 갈라짐 선들.',
  },
  {
    id: 'empty-chair',
    filename: '08-empty-chair.svg',
    label: '빈 의자',
    meaning: '함께 있어도 마음은 없는 자리',
    group: 'avoidance',
    emotions: ['그리움', '공허함', '상실감', '아쉬움'],
    needs: ['연결', '함께함', '존재감'],
    uiContexts: [
      'report-header',
      'share-card',
      'marketing-cover',
      'marketing-scene',
      'empty-state',
    ],
    relationTypes: ['couple', 'marriage', 'friend', 'family', 'parent_child'],
    tone: 'heavy',
    designPrompt:
      '미니멀 의자 실루엣(등받이·좌석·다리 두 개). 그림자 타원, 빈 공간이 주인공인 구성.',
  },
  {
    id: 'overflowing-cup',
    filename: '09-overflowing-cup.svg',
    label: '넘치는 컵',
    meaning: '더 이상 받아들일 수 없을 만큼 가득 찬 상태',
    group: 'tension',
    emotions: ['벅참', '과부하', '지침', '한계 느낌'],
    needs: ['여유', '돌봄', '표현'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'family', 'colleague'],
    tone: 'neutral',
    designPrompt:
      '컵에서 두 줄기 이상 흘러넘치는 곡선 액체. 잎 장식·물방울 포함, 벅차면서도 아름다운 균형.',
  },
  {
    id: 'rope-bridge',
    filename: '10-rope-bridge.svg',
    label: '흔들리는 다리',
    meaning: '건너고 싶지만 무서워서 머뭇거리는 관계',
    group: 'hesitation',
    emotions: ['망설임', '두려움', '용기 내고 싶은', '기대'],
    needs: ['안전', '신뢰', '연결'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'onboarding-intro',
      'marketing-cover',
    ],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '좌우 두 절벽 판자를 잇는 여러 개의 곡선 밧줄. 나뭇가지 배경, 불안정하면서 연결된 느낌.',
  },
  {
    id: 'half-open-letter',
    filename: '11-half-open-letter.svg',
    label: '반쯤 열린 편지',
    meaning: '말하고 싶은데 끝까지 못 한 마음',
    group: 'hesitation',
    emotions: ['망설임', '안타까움', '말하고 싶은 마음', '두려움'],
    needs: ['표현', '이해받음', '연결'],
    uiContexts: [
      'report-header',
      'share-card',
      'session-end',
      'marketing-cover',
      'marketing-scene',
      'marketing-quote-card',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '반쯤 열린 편지 봉투. 편지지 내용 암시선 2–3개, 열린 봉투 플랩 곡선, 잎사귀 장식 옵션.',
  },
  {
    id: 'two-trees-roots',
    filename: '12-two-trees-roots.svg',
    label: '뿌리 얽힌 두 나무',
    meaning: '떨어져 보여도 깊은 곳은 연결되어 있어요',
    group: 'recovery',
    emotions: ['안도', '따뜻함', '희망', '연결된 느낌'],
    needs: ['연결', '화해', '성장'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'onboarding-intro',
      'marketing-cover',
      'marketing-quote-card',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '두 나무(각각 구름형 수관), 아래 뿌리들이 서로 얽히는 곡선들. 희망적이고 연결된 구성.',
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

export function getMetaphorById(id: string): Metaphor | undefined {
  return METAPHORS.find((m) => m.id === id);
}

export function getMetaphorImagePath(filename: string): string {
  return `/illustrations/metaphors/${filename}`;
}

/** 특정 UI 컨텍스트에 쓸 수 있는 모든 메타포 반환 */
export function getMetaphorsByContext(context: MetaphorUiContext): Metaphor[] {
  return METAPHORS.filter((m) => m.uiContexts.includes(context));
}

/** 특정 그룹의 모든 메타포 반환 */
export function getMetaphorsByGroup(group: Metaphor['group']): Metaphor[] {
  return METAPHORS.filter((m) => m.group === group);
}

/**
 * 주어진 조건에 가장 잘 맞는 메타포를 반환합니다.
 *
 * 우선순위:
 * 1. uiContext 필터 (hard — 해당 없으면 전체 pool 유지)
 * 2. relationType 필터 (soft — 매칭 없으면 'all' 허용 항목으로 후퇴)
 * 3. tone 필터 (soft — 매칭 없으면 무시)
 * 4. emotion(+2) / need(+3) 스코어링 — 점수 있는 항목 우선
 * 5. 남은 pool에서 랜덤 선택
 */
export function matchMetaphor(opts: {
  uiContext: MetaphorUiContext;
  relationType?: MetaphorRelationType;
  emotion?: string;
  need?: string;
  tone?: 'warm' | 'neutral' | 'heavy';
  exclude?: string[];
}): Metaphor {
  let pool = opts.exclude?.length
    ? METAPHORS.filter((m) => !opts.exclude!.includes(m.id))
    : [...METAPHORS];

  const byContext = pool.filter((m) => m.uiContexts.includes(opts.uiContext));
  if (byContext.length > 0) pool = byContext;

  if (opts.relationType) {
    const byRelation = pool.filter(
      (m) =>
        m.relationTypes.includes('all') ||
        m.relationTypes.includes(opts.relationType!),
    );
    if (byRelation.length > 0) pool = byRelation;
  }

  if (opts.tone) {
    const byTone = pool.filter((m) => m.tone === opts.tone);
    if (byTone.length > 0) pool = byTone;
  }

  if (opts.emotion || opts.need) {
    const scored = pool
      .map((m) => ({
        m,
        score:
          (opts.emotion && m.emotions.includes(opts.emotion) ? 2 : 0) +
          (opts.need && m.needs.includes(opts.need) ? 3 : 0),
      }))
      .sort((a, b) => b.score - a.score);
    if (scored[0]?.score > 0) return scored[0].m;
  }

  return pool[Math.floor(Math.random() * pool.length)] ?? METAPHORS[0];
}
