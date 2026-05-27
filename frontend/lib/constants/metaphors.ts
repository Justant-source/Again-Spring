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

  // ── 연인·부부 (13–24) ─────────────────────────────────────────────────────
  {
    id: 'two-compasses-apart',
    filename: '13-two-compasses-apart.svg',
    label: '엇갈린 나침반',
    meaning: '원하는 미래의 방향이 달라진 상태',
    group: 'avoidance',
    emotions: ['방향감 없음', '미래에 대한 불안', '어긋남', '혼란'],
    needs: ['공동의 비전', '소통', '이해'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['couple', 'marriage'],
    tone: 'neutral',
    designPrompt:
      '두 개의 나침반이 서로 다른 방향을 가리키며 나란히 놓인 모습. 바늘 방향 차이가 핵심.',
  },
  {
    id: 'melting-candle',
    filename: '14-melting-candle.svg',
    label: '녹아내리는 초',
    meaning: '열정·온기가 소진되어가는 상태',
    group: 'tension',
    emotions: ['지침', '소진', '열정 사라짐', '안타까움'],
    needs: ['활력', '회복', '따뜻함'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '거의 다 녹아내린 양초. 굵게 흘러내린 촛농, 가늘어진 불꽃. 소진된 온기를 표현.',
  },
  {
    id: 'parallel-rails',
    filename: '15-parallel-rails.svg',
    label: '평행한 기찻길',
    meaning: '같은 방향이지만 영원히 만나지 않는 두 레일',
    group: 'avoidance',
    emotions: ['단절감', '평행선', '답답함', '외로움'],
    needs: ['연결', '만남', '교류'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage'],
    tone: 'neutral',
    designPrompt:
      '멀리 수평선으로 뻗어나가는 평행한 두 레일. 투시감 강조, 영원히 만나지 않는 구조.',
  },
  {
    id: 'half-erased-note',
    filename: '16-half-erased-note.svg',
    label: '반쯤 지워진 메모',
    meaning: '썼다가 지운 흔적이 남은 메모 — 말하다 멈춘 마음',
    group: 'hesitation',
    emotions: ['망설임', '후회', '말하지 못한 마음', '두려움'],
    needs: ['표현', '용기', '이해받음'],
    uiContexts: ['report-header', 'share-card', 'marketing-scene', 'marketing-quote-card'],
    relationTypes: ['couple', 'marriage'],
    tone: 'neutral',
    designPrompt:
      '메모지에 몇 줄 글씨 중 일부가 지워진 상태. 지우개 흔적 선, 남은 줄과 지워진 줄의 대비.',
  },
  {
    id: 'tangled-thread',
    filename: '17-tangled-thread.svg',
    label: '감겨있는 실타래',
    meaning: '복잡하게 얽힌 관계',
    group: 'tension',
    emotions: ['혼란', '답답함', '복잡함', '얽힌 느낌'],
    needs: ['명확함', '정리', '이해'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'family'],
    tone: 'neutral',
    designPrompt:
      '여러 개의 실이 복잡하게 감기고 얽힌 실타래. 중심부 특히 촘촘하고 복잡하게.',
  },
  {
    id: 'dying-stove',
    filename: '18-dying-stove.svg',
    label: '꺼져가는 난로',
    meaning: '오래된 관계의 온기가 식어가는 상태',
    group: 'avoidance',
    emotions: ['냉기', '식어감', '거리감', '쓸쓸함'],
    needs: ['온기', '따뜻함', '활력'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '불씨가 거의 꺼진 난로. 재가 쌓인 모습, 희미한 연기 한 줄기만 남은 상태.',
  },
  {
    id: 'one-candle-out',
    filename: '19-one-candle-out.svg',
    label: '한쪽만 타는 두 촛불',
    meaning: '불균등한 노력',
    group: 'loneliness',
    emotions: ['외로움', '불균형', '서운함', '혼자인 느낌'],
    needs: ['균형', '공정함', '인정'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'friend'],
    tone: 'heavy',
    designPrompt:
      '나란히 선 두 개의 초 중 하나는 활활 타고 하나는 꺼진 상태. 불균형이 핵심.',
  },
  {
    id: 'empty-photo-frame',
    filename: '20-empty-photo-frame.svg',
    label: '빈 사진 액자',
    meaning: '함께였던 순간이 지워진 자리',
    group: 'loneliness',
    emotions: ['상실', '그리움', '공허함', '과거 집착'],
    needs: ['연결', '기억', '함께함'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'empty-state'],
    relationTypes: ['couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '벽에 걸린 빈 액자. 액자 테두리 디테일 있음, 내부 완전히 비어있음. 공허한 공간이 주인공.',
  },
  {
    id: 'pendulum',
    filename: '21-pendulum.svg',
    label: '흔들리는 추',
    meaning: '감정의 극과 극',
    group: 'tension',
    emotions: ['불안정', '극단적 감정', '혼란', '지침'],
    needs: ['안정', '예측가능성', '균형'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage'],
    tone: 'neutral',
    designPrompt:
      '크게 흔들리는 추. 진자의 호 궤적 점선, 극단까지 기운 상태. 불안정한 리듬 표현.',
  },
  {
    id: 'back-to-back-umbrellas',
    filename: '22-back-to-back-umbrellas.svg',
    label: '등진 두 우산',
    meaning: '서로 등을 지고 각자 우산을 쓴 상태',
    group: 'avoidance',
    emotions: ['단절', '등 돌림', '서운함', '방어적'],
    needs: ['연결', '화해', '이해'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'friend'],
    tone: 'neutral',
    designPrompt:
      '두 개의 우산이 서로 등을 맞댄 채 놓인 모습. 각자 다른 방향, 함께이지만 단절된 구성.',
  },
  {
    id: 'crumbling-sandcastle',
    filename: '23-crumbling-sandcastle.svg',
    label: '무너지는 모래성',
    meaning: '함께 쌓아온 것이 흔들리는 상태',
    group: 'tension',
    emotions: ['좌절', '허무함', '무너짐', '실망'],
    needs: ['안전', '안정', '신뢰'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '일부가 무너지고 있는 모래성. 부서지는 부분의 파편 선들, 아직 서있는 탑 부분과 대비.',
  },
  {
    id: 'empty-nest',
    filename: '24-empty-nest.svg',
    label: '빈 둥지',
    meaning: '역할이 끝난 후의 공허함',
    group: 'loneliness',
    emotions: ['공허함', '그리움', '허전함', '변화에 대한 적응'],
    needs: ['목적', '연결', '새로운 의미'],
    uiContexts: ['report-header', 'share-card', 'marketing-scene'],
    relationTypes: ['couple', 'marriage', 'parent_child'],
    tone: 'warm',
    designPrompt:
      '나뭇가지 위 텅 빈 새 둥지. 정교한 둥지 짜임새 선들, 비어있는 내부. 따뜻하면서도 허전한 느낌.',
  },

  // ── 친구·지인 (25–32) ────────────────────────────────────────────────────
  {
    id: 'dried-bouquet',
    filename: '25-dried-bouquet.svg',
    label: '말린 꽃다발',
    meaning: '식어버린 우정',
    group: 'avoidance',
    emotions: ['무관심', '식어버린 감정', '소외감', '아쉬움'],
    needs: ['연결', '관심', '우정'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['friend'],
    tone: 'neutral',
    designPrompt:
      '말라서 아래로 처진 꽃다발. 시든 꽃잎과 늘어진 리본. 한때 선물이었지만 바랜 상태.',
  },
  {
    id: 'emptying-hourglass',
    filename: '26-emptying-hourglass.svg',
    label: '비어가는 모래시계',
    meaning: '서서히 멀어지는 관계',
    group: 'avoidance',
    emotions: ['상실감', '무기력', '소원해짐', '시간의 흐름'],
    needs: ['연결', '소통', '관심'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['friend', 'couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '모래가 거의 다 아래로 내려간 모래시계. 위쪽 거의 비어있고 아래쪽 가득, 남은 모래 몇 알.',
  },
  {
    id: 'one-lit-bulb',
    filename: '27-one-lit-bulb.svg',
    label: '한쪽만 켜진 전구 두 개',
    meaning: '일방적인 노력',
    group: 'loneliness',
    emotions: ['일방적인 느낌', '서운함', '외로움', '허탈함'],
    needs: ['상호성', '인정', '균형'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['friend', 'couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '나란히 선 두 전구 중 하나만 빛남. 켜진 전구는 광선 표현, 꺼진 전구는 어두운 상태.',
  },
  {
    id: 'broken-thread',
    filename: '28-broken-thread.svg',
    label: '끊어진 실',
    meaning: '단절된 연결',
    group: 'avoidance',
    emotions: ['단절', '배신감', '슬픔', '끊어진 느낌'],
    needs: ['연결', '신뢰', '회복'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['friend', 'couple'],
    tone: 'heavy',
    designPrompt:
      '팽팽하게 연결되다 중간에서 끊어진 실. 두 끝 실이 풀어지며 늘어진 모습. 단절의 순간.',
  },
  {
    id: 'wrongly-folded-letter',
    filename: '29-wrongly-folded-letter.svg',
    label: '잘못 접힌 편지',
    meaning: '오해·전달 실패',
    group: 'hesitation',
    emotions: ['오해', '당황', '불편함', '말이 전달 안 된 느낌'],
    needs: ['소통', '명확함', '이해'],
    uiContexts: ['report-header', 'marketing-scene', 'marketing-quote-card'],
    relationTypes: ['friend', 'all'],
    tone: 'neutral',
    designPrompt:
      '삐뚤게 잘못 접혀있는 편지. 봉투에서 비뚤어져 나온 편지지, 어긋난 접힘 선들.',
  },
  {
    id: 'string-telephone',
    filename: '30-string-telephone.svg',
    label: '실 전화기 두 컵',
    meaning: '거리가 있어도 닿으려는 시도',
    group: 'hesitation',
    emotions: ['시도', '조심스러운 연결', '기대', '설렘'],
    needs: ['소통', '연결', '시도'],
    uiContexts: ['report-header', 'share-card', 'session-end', 'marketing-cover'],
    relationTypes: ['friend', 'all'],
    tone: 'warm',
    designPrompt:
      '실로 연결된 두 컵(실 전화기). 컵 사이 실이 느슨하게 이어져있음. 소박하지만 연결 의지.',
  },
  {
    id: 'inside-out-umbrella',
    filename: '31-inside-out-umbrella.svg',
    label: '뒤집힌 우산',
    meaning: '믿었던 것이 배신당한 상태',
    group: 'tension',
    emotions: ['배신감', '충격', '뒤집힌 기대', '상처'],
    needs: ['신뢰', '안전', '공정함'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['friend', 'colleague'],
    tone: 'heavy',
    designPrompt:
      '바람에 뒤집혀버린 우산. 뒤집힌 캐노피, 뒤집힌 살대들. 보호가 깨진 순간의 형태.',
  },
  {
    id: 'one-seedling',
    filename: '32-one-seedling.svg',
    label: '한쪽만 자란 씨앗',
    meaning: '불균형한 투자',
    group: 'loneliness',
    emotions: ['불균형', '서운함', '혼자인 느낌', '소외'],
    needs: ['상호성', '균형', '투자'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['friend', 'couple'],
    tone: 'neutral',
    designPrompt:
      '두 개의 화분 중 하나만 싹이 돋아나고 하나는 흙만 있는 상태. 불균형한 돌봄을 표현.',
  },

  // ── 직장 (33–39) ─────────────────────────────────────────────────────────
  {
    id: 'tilted-scale',
    filename: '33-tilted-scale.svg',
    label: '기울어진 저울',
    meaning: '인정·보상의 불균형',
    group: 'tension',
    emotions: ['불공정함', '억울함', '좌절', '분노'],
    needs: ['공정함', '인정', '균형'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['colleague'],
    tone: 'heavy',
    designPrompt:
      '한쪽으로 심하게 기울어진 양팔 저울. 한쪽 크게 기울고 반대쪽 들려있는 구조.',
  },
  {
    id: 'overflowing-papers',
    filename: '34-overflowing-papers.svg',
    label: '넘치는 서류 더미',
    meaning: '과도한 요구·소진',
    group: 'tension',
    emotions: ['소진', '압박감', '벅참', '지침'],
    needs: ['여유', '공정함', '지원'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['colleague'],
    tone: 'heavy',
    designPrompt:
      '책상 위에 가득 쌓인 서류 더미에서 서류가 흘러내리는 모습. 가장자리에서 떨어지는 종이들.',
  },
  {
    id: 'empty-trophy',
    filename: '35-empty-trophy.svg',
    label: '빈 트로피',
    meaning: '인정받지 못한 노력',
    group: 'loneliness',
    emotions: ['인정받지 못한 느낌', '허탈함', '슬픔', '무력감'],
    needs: ['인정', '가치', '존중'],
    uiContexts: ['report-header', 'share-card', 'marketing-scene'],
    relationTypes: ['colleague', 'all'],
    tone: 'neutral',
    designPrompt:
      '광택 없이 홀로 서있는 빈 트로피. 트로피 형태는 완전하지만 텅 빈 느낌, 이름 새김 없음.',
  },
  {
    id: 'light-under-door',
    filename: '36-light-under-door.svg',
    label: '문틈으로 새는 불빛',
    meaning: '배제·소외',
    group: 'loneliness',
    emotions: ['소외', '배제', '외로움', '있어도 없는 느낌'],
    needs: ['소속감', '인정', '참여'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['colleague', 'family'],
    tone: 'heavy',
    designPrompt:
      '닫힌 문 아래 틈에서 빛이 새어나옴. 밖은 어둡고 문틈 빛만 따뜻하게. 배제된 시선.',
  },
  {
    id: 'chained-anchor',
    filename: '37-chained-anchor.svg',
    label: '체인에 묶인 닻',
    meaning: '벗어나고 싶지만 묶인 상태',
    group: 'avoidance',
    emotions: ['답답함', '갇힌 느낌', '떠나고 싶은 마음', '무력감'],
    needs: ['자유', '자율성', '변화'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['colleague', 'all'],
    tone: 'heavy',
    designPrompt:
      '체인에 감긴 닻. 닻에 묶인 굵은 체인, 움직이지 못하는 구조감. 무거움과 속박 표현.',
  },
  {
    id: 'too-many-keys',
    filename: '38-too-many-keys.svg',
    label: '너무 많은 열쇠',
    meaning: '과중한 책임·역할',
    group: 'tension',
    emotions: ['과부하', '부담감', '혼란', '선택 어려움'],
    needs: ['명확함', '지원', '우선순위'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['colleague', 'all'],
    tone: 'neutral',
    designPrompt:
      '열쇠고리에 너무 많은 열쇠들이 달린 모습. 제각각 모양의 열쇠들, 무겁게 처진 구조.',
  },
  {
    id: 'gears-not-meshing',
    filename: '39-gears-not-meshing.svg',
    label: '맞물리지 않는 톱니바퀴',
    meaning: '팀워크·협력 단절',
    group: 'tension',
    emotions: ['단절', '마찰', '불협화음', '답답함'],
    needs: ['협력', '소통', '팀워크'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['colleague'],
    tone: 'neutral',
    designPrompt:
      '두 개의 톱니바퀴가 서로 어긋나 맞물리지 않는 모습. 이빨이 부딪히거나 거리가 있는 상태.',
  },

  // ── 가족 (40–47) ─────────────────────────────────────────────────────────
  {
    id: 'small-birdcage',
    filename: '40-small-birdcage.svg',
    label: '너무 작은 새장',
    meaning: '통제·과잉보호로 숨막히는 상태',
    group: 'tension',
    emotions: ['답답함', '통제받는 느낌', '숨막힘', '자유 갈망'],
    needs: ['자율성', '공간', '독립'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['family', 'parent_child'],
    tone: 'heavy',
    designPrompt:
      '작은 새장 안 새. 새장이 새에 비해 지나치게 작아 숨막히는 구조. 통제 상징.',
  },
  {
    id: 'tall-fence',
    filename: '41-tall-fence.svg',
    label: '높아진 울타리',
    meaning: '보호가 감금으로 변한 상태',
    group: 'protection',
    emotions: ['갇힌 느낌', '답답함', '보호받지만 불편한', '두려움'],
    needs: ['자유', '자율성', '공간'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['family', 'parent_child'],
    tone: 'heavy',
    designPrompt:
      '매우 높이 자란 울타리. 높은 목재 판자들, 안쪽 작은 공간이 보이는 구도. 보호가 감금으로 전환.',
  },
  {
    id: 'trees-growing-apart',
    filename: '42-trees-growing-apart.svg',
    label: '반대 방향으로 자란 두 나무',
    meaning: '같은 뿌리에서 서로 다른 방향으로 자란',
    group: 'avoidance',
    emotions: ['멀어짐', '자연스러운 이별', '아쉬움', '각자의 길'],
    needs: ['연결', '소통', '이해'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover'],
    relationTypes: ['family', 'parent_child', 'friend'],
    tone: 'neutral',
    designPrompt:
      '같은 뿌리에서 출발해 반대 방향으로 자란 두 나무. 아래 뿌리 연결, 위 가지는 멀어지는 구조.',
  },
  {
    id: 'cracked-bowl',
    filename: '43-cracked-bowl.svg',
    label: '깨진 그릇',
    meaning: '상처난 관계',
    group: 'tension',
    emotions: ['상처', '손상된 신뢰', '아픔', '회복 욕구'],
    needs: ['치유', '신뢰', '안전'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['family', 'couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '금이 간 그릇. 금이 간 선들 선명하게, 조각나지는 않았지만 손상된 상태. 상처 입은 관계.',
  },
  {
    id: 'empty-dining-table',
    filename: '44-empty-dining-table.svg',
    label: '비어있는 식탁',
    meaning: '함께하지 못하는 시간',
    group: 'loneliness',
    emotions: ['고독', '그리움', '함께하지 못하는 슬픔', '소외'],
    needs: ['함께함', '연결', '가족'],
    uiContexts: ['report-header', 'share-card', 'marketing-cover', 'empty-state'],
    relationTypes: ['family', 'couple', 'marriage'],
    tone: 'heavy',
    designPrompt:
      '텅 빈 식탁. 두 개 이상의 의자가 놓여있지만 아무도 없음. 식탁 위 공허한 빈 공간.',
  },
  {
    id: 'wilting-plant',
    filename: '45-wilting-plant.svg',
    label: '시들어가는 화분',
    meaning: '방치된 관계',
    group: 'avoidance',
    emotions: ['방치된 느낌', '무관심', '지침', '포기 임박'],
    needs: ['돌봄', '관심', '연결'],
    uiContexts: ['report-header', 'marketing-scene'],
    relationTypes: ['family', 'friend', 'parent_child'],
    tone: 'neutral',
    designPrompt:
      '화분 속 시들어가는 식물. 늘어진 잎사귀들, 메마른 흙. 방치된 시간을 식물로 표현.',
  },
  {
    id: 'closed-diary',
    filename: '46-closed-diary.svg',
    label: '닫힌 일기장',
    meaning: '세대 간 말하지 못하는 속마음',
    group: 'hesitation',
    emotions: ['억누른 감정', '말하고 싶은 마음', '세대 차이', '혼자인 느낌'],
    needs: ['이해받음', '표현', '연결'],
    uiContexts: ['report-header', 'marketing-scene', 'marketing-quote-card'],
    relationTypes: ['family', 'parent_child'],
    tone: 'neutral',
    designPrompt:
      '꼭 닫힌 일기장. 자물쇠 또는 리본으로 묶인 다이어리. 안에 담긴 내용을 상상하게 하는 구성.',
  },
  {
    id: 'long-shadow',
    filename: '47-long-shadow.svg',
    label: '너무 긴 그림자',
    meaning: '부모의 영향력 아래',
    group: 'protection',
    emotions: ['무거움', '영향력 아래', '자신이 없는 느낌', '부담'],
    needs: ['독립', '자아', '인정'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['family', 'parent_child'],
    tone: 'heavy',
    designPrompt:
      '한 인물의 그림자가 다른 인물 전체를 덮는 구성. 그림자 크기 대비로 영향력 표현.',
  },

  // ── 지인 (48–52) ─────────────────────────────────────────────────────────
  {
    id: 'foggy-path',
    filename: '48-foggy-path.svg',
    label: '안개 속 길',
    meaning: '관계의 모호함',
    group: 'hesitation',
    emotions: ['불확실함', '모호함', '망설임', '조심스러움'],
    needs: ['명확함', '방향', '안전'],
    uiContexts: ['report-header', 'onboarding-intro', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '안개가 자욱한 길. 발밑 길은 보이지만 앞은 안개로 가려진 모습. 불확실한 미래 표현.',
  },
  {
    id: 'half-open-window',
    filename: '49-half-open-window.svg',
    label: '반쯤 열린 창문',
    meaning: '조심스럽게 열어두는 마음',
    group: 'hesitation',
    emotions: ['조심스러움', '기대', '작은 용기', '개방'],
    needs: ['안전', '연결', '시도'],
    uiContexts: ['report-header', 'session-end', 'share-card', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '반쯤 열린 창문. 창틀과 유리가 45도 정도 열린 상태, 바깥 빛이 조금 들어옴.',
  },
  {
    id: 'oil-on-water',
    filename: '50-oil-on-water.svg',
    label: '물 위의 기름',
    meaning: '섞이지 않는 관계',
    group: 'avoidance',
    emotions: ['거리감', '섞이지 않는 느낌', '어색함', '분리'],
    needs: ['연결', '공통점', '이해'],
    uiContexts: ['report-header', 'marketing-cover', 'marketing-scene'],
    relationTypes: ['friend', 'colleague', 'all'],
    tone: 'neutral',
    designPrompt:
      '물 위에 떠있는 기름. 물과 기름이 층층이 분리된 단면. 자연스럽게 섞이지 않는 두 존재.',
  },
  {
    id: 'crossing-paths',
    filename: '51-crossing-paths.svg',
    label: '교차하는 두 길',
    meaning: '스쳐 지나가는 관계',
    group: 'hesitation',
    emotions: ['스쳐지남', '아쉬움', '무관심', '놓친 느낌'],
    needs: ['연결', '소통', '만남'],
    uiContexts: ['report-header', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '두 개의 길이 X자로 교차하는 구성. 교차점을 지나쳐 각자의 방향으로 멀어지는 구조.',
  },
  {
    id: 'shallow-well',
    filename: '52-shallow-well.svg',
    label: '얕은 우물',
    meaning: '깊어지지 않는 관계',
    group: 'loneliness',
    emotions: ['피상적인 느낌', '닿지 못함', '아쉬움', '갈증'],
    needs: ['깊이', '이해', '연결'],
    uiContexts: ['report-header', 'marketing-scene'],
    relationTypes: ['friend', 'colleague', 'all'],
    tone: 'neutral',
    designPrompt:
      '깊이가 얕은 우물. 우물 측면에서 본 낮은 수위, 바닥이 보이는 상태. 깊이 없음을 표현.',
  },

  // ── 회복·전환 (53–60) ────────────────────────────────────────────────────
  {
    id: 'first-footstep',
    filename: '53-first-footstep.svg',
    label: '첫 발자국',
    meaning: '용기 내어 내딛은 시작',
    group: 'recovery',
    emotions: ['용기', '조심스러운 희망', '설렘', '시작'],
    needs: ['용기', '변화', '성장'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'onboarding-intro',
      'marketing-cover',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '눈이나 모래 위 첫 번째 발자국 하나. 작고 조심스러운 발자국, 주변 표면과 대비로 시작을 표현.',
  },
  {
    id: 'seed-in-palm',
    filename: '54-seed-in-palm.svg',
    label: '손바닥 위 씨앗',
    meaning: '새 관계의 가능성',
    group: 'recovery',
    emotions: ['가능성', '희망', '조심스러운 기대', '소중함'],
    needs: ['성장', '가능성', '시작'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'marketing-cover',
      'marketing-quote-card',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '손바닥 위에 놓인 작은 씨앗 하나. 손바닥 윤곽선 단순하게, 씨앗 강조. 새로운 가능성을 담은 손.',
  },
  {
    id: 'open-window',
    filename: '55-open-window.svg',
    label: '활짝 열린 창문',
    meaning: '마음을 열어두는 상태',
    group: 'recovery',
    emotions: ['해방감', '개방', '희망', '새로운 시작'],
    needs: ['자유', '변화', '연결'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'onboarding-intro',
      'marketing-cover',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '활짝 열린 창문. 양쪽으로 완전히 열린 창문, 바깥 빛 가득. 커튼이 바람에 살짝 나부끼는 모습.',
  },
  {
    id: 'cups-finally-touching',
    filename: '56-cups-finally-touching.svg',
    label: '마침내 닿는 두 컵',
    meaning: '화해의 첫 접촉',
    group: 'recovery',
    emotions: ['화해', '안도', '따뜻함', '첫 접촉'],
    needs: ['화해', '연결', '평화'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'marketing-cover',
      'marketing-quote-card',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '두 개의 컵이 가볍게 닿으며 건배하는 모습. 컵들이 막 닿는 순간, 약간의 물결이 생긴 상태.',
  },
  {
    id: 'melting-ice',
    filename: '57-melting-ice.svg',
    label: '녹아내리는 얼음',
    meaning: '차가움이 녹는 과정',
    group: 'recovery',
    emotions: ['풀어짐', '따뜻해짐', '해빙', '변화'],
    needs: ['화해', '따뜻함', '변화'],
    uiContexts: ['report-header', 'session-end', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '얼음 덩어리가 녹아내리는 모습. 얼음 상단에서 물방울이 떨어지며 녹는 과정. 차가움이 따뜻해지는 전환.',
  },
  {
    id: 'crack-with-light',
    filename: '58-crack-with-light.svg',
    label: '균열에 비치는 빛',
    meaning: '상처가 빛의 통로',
    group: 'recovery',
    emotions: ['희망', '치유', '새로운 시각', '변화'],
    needs: ['치유', '성장', '빛'],
    uiContexts: [
      'report-header',
      'session-end',
      'share-card',
      'marketing-cover',
      'marketing-quote-card',
    ],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '어두운 표면의 균열에서 빛이 새어나오는 모습. 균열이 결함이 아닌 빛의 통로로 표현.',
  },
  {
    id: 'two-compasses-aligned',
    filename: '59-two-compasses-aligned.svg',
    label: '같은 방향의 두 나침반',
    meaning: '같은 미래를 바라보는 마음',
    group: 'recovery',
    emotions: ['일치', '희망', '같은 방향', '안도'],
    needs: ['공동의 비전', '연결', '미래'],
    uiContexts: ['report-header', 'session-end', 'share-card', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'warm',
    designPrompt:
      '두 개의 나침반이 같은 방향을 가리키며 나란히 놓인 모습. 두 바늘 방향이 일치하는 것이 핵심.',
  },
  {
    id: 'raft-together',
    filename: '60-raft-together.svg',
    label: '함께 타는 뗏목',
    meaning: '어려움 속 동행',
    group: 'recovery',
    emotions: ['함께함', '의지', '협력', '안도'],
    needs: ['동행', '협력', '신뢰'],
    uiContexts: ['report-header', 'session-end', 'share-card', 'marketing-cover'],
    relationTypes: ['all'],
    tone: 'neutral',
    designPrompt:
      '작은 뗏목을 함께 타고 가는 두 사람 실루엣. 뗏목 구조, 물결. 함께하는 여정 표현.',
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
