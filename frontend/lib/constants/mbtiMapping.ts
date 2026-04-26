import type { CommunicationStyle } from '@/lib/types';

// 16 MBTI types → 6 communication styles
export const MBTI_TO_STYLE: Record<string, CommunicationStyle> = {
  ENFP: 'wave', ENFJ: 'wave', ESFP: 'wave',
  ESFJ: 'leaf', INFP: 'leaf', ISFJ: 'leaf',
  INFJ: 'moon', ISFP: 'moon',
  ENTJ: 'flame', ENTP: 'flame', ESTP: 'flame',
  ESTJ: 'star', INTP: 'star',
  INTJ: 'mountain', ISTJ: 'mountain', ISTP: 'mountain',
};

export interface MbtiQuestion {
  id: string;
  dimension: 'EI' | 'SN' | 'TF' | 'JP';
  text: string;
  optionA: { label: string; letter: string };
  optionB: { label: string; letter: string };
}

// 60 questions (15 per dimension), A/B choice
export const MBTI_TEST_QUESTIONS: MbtiQuestion[] = [
  // ── EI (외향 · 내향) ──────────────────────────────────────
  { id: 'ei1',  dimension: 'EI', text: '주말에 에너지를 충전하는 방식은?', optionA: { letter: 'E', label: '친구·지인들과 어울리며 활동적으로 보낸다' }, optionB: { letter: 'I', label: '혼자 조용히 쉬거나 개인 시간을 갖는다' } },
  { id: 'ei2',  dimension: 'EI', text: '처음 만나는 자리에서 나는?', optionA: { letter: 'E', label: '먼저 말을 걸거나 대화를 시작한다' }, optionB: { letter: 'I', label: '상대가 말을 걸어올 때까지 지켜본다' } },
  { id: 'ei3',  dimension: 'EI', text: '생각을 정리할 때 나는?', optionA: { letter: 'E', label: '대화하면서 말로 풀어내다 보면 정리된다' }, optionB: { letter: 'I', label: '혼자 충분히 생각한 후 말하는 편이다' } },
  { id: 'ei4',  dimension: 'EI', text: '사람이 많은 모임에서 나는?', optionA: { letter: 'E', label: '에너지가 올라가고 즐겁다' }, optionB: { letter: 'I', label: '어느 순간 지치고 혼자 있고 싶어진다' } },
  { id: 'ei5',  dimension: 'EI', text: '새로운 환경에 적응할 때?', optionA: { letter: 'E', label: '사람들과 빨리 어울리며 적응한다' }, optionB: { letter: 'I', label: '천천히 관찰하고 익숙해지는 편이다' } },
  { id: 'ei6',  dimension: 'EI', text: '오랜만에 쉬는 날이 생기면?', optionA: { letter: 'E', label: '친구를 만나거나 밖에서 무언가를 하고 싶다' }, optionB: { letter: 'I', label: '집에서 조용히 쉬는 게 먼저 떠오른다' } },
  { id: 'ei7',  dimension: 'EI', text: '낯선 사람들과 대화할 때?', optionA: { letter: 'E', label: '금방 친해지고 대화가 자연스럽다' }, optionB: { letter: 'I', label: '처음엔 어색하고 한참 지나야 편해진다' } },
  { id: 'ei8',  dimension: 'EI', text: '아이디어가 생기면?', optionA: { letter: 'E', label: '바로 누군가에게 말하거나 공유하고 싶다' }, optionB: { letter: 'I', label: '혼자 더 발전시킨 뒤 공유한다' } },
  { id: 'ei9',  dimension: 'EI', text: '스트레스를 받을 때?', optionA: { letter: 'E', label: '가까운 사람과 이야기하면서 푼다' }, optionB: { letter: 'I', label: '혼자만의 시간이 필요하다' } },
  { id: 'ei10', dimension: 'EI', text: '팀으로 일할 때?', optionA: { letter: 'E', label: '활기차고 협업이 잘 된다' }, optionB: { letter: 'I', label: '개인 역할이 명확하게 나뉘어야 편하다' } },
  { id: 'ei11', dimension: 'EI', text: '일하다가 막히는 문제가 생기면?', optionA: { letter: 'E', label: '동료에게 바로 묻거나 함께 논의한다' }, optionB: { letter: 'I', label: '먼저 혼자 해결해보려 한다' } },
  { id: 'ei12', dimension: 'EI', text: '관심 없는 주제의 대화가 이어지면?', optionA: { letter: 'E', label: '어느 정도 참여하며 맞장구를 쳐준다' }, optionB: { letter: 'I', label: '빨리 끝났으면 하고 소극적으로 반응한다' } },
  { id: 'ei13', dimension: 'EI', text: '오래된 친구와 다시 만나면?', optionA: { letter: 'E', label: '바로 편해지고 이야기가 넘친다' }, optionB: { letter: 'I', label: '오래 못 봤으면 워밍업이 필요하다' } },
  { id: 'ei14', dimension: 'EI', text: '주변에 아는 사람이 많은 편인가요?', optionA: { letter: 'E', label: '아는 사람이 많고 연락처도 많다' }, optionB: { letter: 'I', label: '진짜 친한 사람은 소수이고 깊게 사귄다' } },
  { id: 'ei15', dimension: 'EI', text: '발표나 무대에 서는 상황이 생기면?', optionA: { letter: 'E', label: '긴장보다 설레는 마음이 더 크다' }, optionB: { letter: 'I', label: '긴장이 크고 되도록 피하고 싶다' } },

  // ── SN (감각 · 직관) ──────────────────────────────────────
  { id: 'sn1',  dimension: 'SN', text: '새로운 정보를 접할 때?', optionA: { letter: 'S', label: '구체적인 사실과 데이터를 먼저 확인한다' }, optionB: { letter: 'N', label: '큰 흐름이나 패턴, 가능성이 먼저 보인다' } },
  { id: 'sn2',  dimension: 'SN', text: '문제를 해결할 때?', optionA: { letter: 'S', label: '이미 검증된 방법을 활용한다' }, optionB: { letter: 'N', label: '새로운 방식을 탐색하는 걸 즐긴다' } },
  { id: 'sn3',  dimension: 'SN', text: '계획을 세울 때?', optionA: { letter: 'S', label: '단계별로 세부 계획을 세운다' }, optionB: { letter: 'N', label: '큰 방향만 잡고 흐름에 따라간다' } },
  { id: 'sn4',  dimension: 'SN', text: '대화할 때 나는?', optionA: { letter: 'S', label: '구체적인 경험이나 사례를 들어 설명한다' }, optionB: { letter: 'N', label: '비유나 추상적인 개념으로 표현한다' } },
  { id: 'sn5',  dimension: 'SN', text: '미래에 대해 생각할 때?', optionA: { letter: 'S', label: '현재 상황을 바탕으로 현실적으로 본다' }, optionB: { letter: 'N', label: '아직 없는 가능성과 잠재력을 떠올린다' } },
  { id: 'sn6',  dimension: 'SN', text: '경험에서 배울 때?', optionA: { letter: 'S', label: '실제로 일어난 일을 토대로 배운다' }, optionB: { letter: 'N', label: '왜 그런 일이 생겼는지 의미를 찾는다' } },
  { id: 'sn7',  dimension: 'SN', text: '취미나 관심사를 고를 때?', optionA: { letter: 'S', label: '실용적이고 결과가 보이는 것에 끌린다' }, optionB: { letter: 'N', label: '창의적이고 상상력이 필요한 것에 끌린다' } },
  { id: 'sn8',  dimension: 'SN', text: '보고나 발표를 구성할 때?', optionA: { letter: 'S', label: '정확한 수치와 사실을 기반으로 구성한다' }, optionB: { letter: 'N', label: '인사이트와 방향성을 중심으로 전달한다' } },
  { id: 'sn9',  dimension: 'SN', text: '집중이 잘 되는 상황은?', optionA: { letter: 'S', label: '현재 하고 있는 일에 몰입할 때' }, optionB: { letter: 'N', label: '아이디어가 떠오르고 연결고리가 보일 때' } },
  { id: 'sn10', dimension: 'SN', text: '하나의 일을 할 때?', optionA: { letter: 'S', label: '처음부터 끝까지 완성하는 것을 중요시한다' }, optionB: { letter: 'N', label: '여러 가능성을 동시에 탐색하고 싶어진다' } },
  { id: 'sn11', dimension: 'SN', text: '설명을 들을 때?', optionA: { letter: 'S', label: '구체적이고 단계적인 설명이 잘 이해된다' }, optionB: { letter: 'N', label: '전체적인 맥락이 먼저 파악되어야 이해된다' } },
  { id: 'sn12', dimension: 'SN', text: '나의 강점은?', optionA: { letter: 'S', label: '세심하고 꼼꼼하게 처리하는 것' }, optionB: { letter: 'N', label: '아이디어를 내거나 새로운 시각을 제시하는 것' } },
  { id: 'sn13', dimension: 'SN', text: '지루한 반복 업무는?', optionA: { letter: 'S', label: '익숙해지면 안정감을 느낀다' }, optionB: { letter: 'N', label: '금방 지루해지고 변화를 원하게 된다' } },
  { id: 'sn14', dimension: 'SN', text: '기억에 잘 남는 것은?', optionA: { letter: 'S', label: '실제로 있었던 세부 내용' }, optionB: { letter: 'N', label: '느낌이나 인상, 분위기' } },
  { id: 'sn15', dimension: 'SN', text: '글을 쓸 때?', optionA: { letter: 'S', label: '사실 위주로 명확하게 쓴다' }, optionB: { letter: 'N', label: '감정이나 상상이 담긴 표현을 즐겨 쓴다' } },

  // ── TF (사고 · 감정) ──────────────────────────────────────
  { id: 'tf1',  dimension: 'TF', text: '결정을 내릴 때?', optionA: { letter: 'T', label: '논리와 객관적인 기준을 우선시한다' }, optionB: { letter: 'F', label: '관계에 미칠 영향과 감정을 함께 고려한다' } },
  { id: 'tf2',  dimension: 'TF', text: '친구가 고민을 털어놓을 때?', optionA: { letter: 'T', label: '현실적인 해결책을 먼저 제시한다' }, optionB: { letter: 'F', label: '먼저 공감하고 감정을 나눠준다' } },
  { id: 'tf3',  dimension: 'TF', text: '논쟁에서 중요한 것은?', optionA: { letter: 'T', label: '감정보다 논거와 사실' }, optionB: { letter: 'F', label: '서로의 감정이 상하지 않는 것' } },
  { id: 'tf4',  dimension: 'TF', text: '비판을 받을 때?', optionA: { letter: 'T', label: '내용이 맞다면 감정과 별개로 수용한다' }, optionB: { letter: 'F', label: '말하는 방식도 중요하고 기분이 영향을 받는다' } },
  { id: 'tf5',  dimension: 'TF', text: '팀 프로젝트에서?', optionA: { letter: 'T', label: '효율과 결과가 가장 중요하다' }, optionB: { letter: 'F', label: '구성원 모두가 불편하지 않게 진행되는 것도 중요하다' } },
  { id: 'tf6',  dimension: 'TF', text: '누군가의 잘못된 행동을 보면?', optionA: { letter: 'T', label: '직접적으로 지적하거나 바로잡으려 한다' }, optionB: { letter: 'F', label: '분위기를 고려해 부드럽게 전달하려 한다' } },
  { id: 'tf7',  dimension: 'TF', text: '상대방에게 불편한 진실을 말할 때?', optionA: { letter: 'T', label: '상처가 되더라도 사실을 말하는 게 옳다' }, optionB: { letter: 'F', label: '상대가 받아들일 수 있게 표현을 조절한다' } },
  { id: 'tf8',  dimension: 'TF', text: '칭찬을 받을 때?', optionA: { letter: 'T', label: '구체적으로 어떤 점이 좋았는지가 더 중요하다' }, optionB: { letter: 'F', label: '그 자체로 기분이 좋고 에너지가 오른다' } },
  { id: 'tf9',  dimension: 'TF', text: '내 관심이 더 쏠리는 것은?', optionA: { letter: 'T', label: '원리, 구조, 효율성, 논리' }, optionB: { letter: 'F', label: '사람, 관계, 감정, 조화' } },
  { id: 'tf10', dimension: 'TF', text: '규칙이나 원칙에 대해?', optionA: { letter: 'T', label: '일관되게 지켜야 한다' }, optionB: { letter: 'F', label: '상황에 따라 융통성이 있어야 한다' } },
  { id: 'tf11', dimension: 'TF', text: '합리적인 결정인데 누군가 불편해한다면?', optionA: { letter: 'T', label: '합리적인 결정이 우선이다' }, optionB: { letter: 'F', label: '불편한 사람의 감정도 충분히 고려한다' } },
  { id: 'tf12', dimension: 'TF', text: '내가 더 잘 듣는 말은?', optionA: { letter: 'T', label: '"논리적이네요", "분석력이 좋아요"' }, optionB: { letter: 'F', label: '"공감을 잘 해줘요", "따뜻한 사람이에요"' } },
  { id: 'tf13', dimension: 'TF', text: '의견 충돌이 있을 때?', optionA: { letter: 'T', label: '옳고 그름을 가리는 게 먼저다' }, optionB: { letter: 'F', label: '관계 회복이 먼저고 옳고 그름은 나중이다' } },
  { id: 'tf14', dimension: 'TF', text: '새로운 아이디어를 평가할 때?', optionA: { letter: 'T', label: '실현 가능성과 효율성을 따진다' }, optionB: { letter: 'F', label: '아이디어에 담긴 사람의 열의와 감정도 본다' } },
  { id: 'tf15', dimension: 'TF', text: '누군가 힘들어 보일 때?', optionA: { letter: 'T', label: '실질적으로 도울 방법을 찾는다' }, optionB: { letter: 'F', label: '옆에 있어주고 감정적으로 지지해준다' } },

  // ── JP (판단 · 인식) ──────────────────────────────────────
  { id: 'jp1',  dimension: 'JP', text: '일정 관리는?', optionA: { letter: 'J', label: '미리 계획하고 지키는 편' }, optionB: { letter: 'P', label: '그때그때 유연하게 대응하는 편' } },
  { id: 'jp2',  dimension: 'JP', text: '정해진 계획에 변경이 생기면?', optionA: { letter: 'J', label: '불편하고 다시 계획을 세우고 싶다' }, optionB: { letter: 'P', label: '새로운 흐름에 맞춰 자연스럽게 조정한다' } },
  { id: 'jp3',  dimension: 'JP', text: '마감 기한이 있을 때?', optionA: { letter: 'J', label: '미리 여유 있게 끝내놓아야 안심된다' }, optionB: { letter: 'P', label: '마감이 다가올수록 집중력이 오른다' } },
  { id: 'jp4',  dimension: 'JP', text: '방이나 작업 공간은?', optionA: { letter: 'J', label: '정리되어 있어야 편하다' }, optionB: { letter: 'P', label: '약간 어질러져 있어도 괜찮다' } },
  { id: 'jp5',  dimension: 'JP', text: '여행 계획은?', optionA: { letter: 'J', label: '일정, 숙소, 동선을 미리 다 정해둔다' }, optionB: { letter: 'P', label: '목적지만 정하고 즉흥으로 움직인다' } },
  { id: 'jp6',  dimension: 'JP', text: '선택의 기로에서?', optionA: { letter: 'J', label: '빨리 결정하고 다음 단계로 넘어가고 싶다' }, optionB: { letter: 'P', label: '더 많은 선택지를 탐색하고 싶다' } },
  { id: 'jp7',  dimension: 'JP', text: '책상이나 서랍 속은?', optionA: { letter: 'J', label: '물건마다 자리가 있고 정돈돼 있다' }, optionB: { letter: 'P', label: '뭐가 어디 있는지는 나만 안다' } },
  { id: 'jp8',  dimension: 'JP', text: '하루 일과가 있다면?', optionA: { letter: 'J', label: '정해진 루틴이 있는 게 더 편하다' }, optionB: { letter: 'P', label: '그날 기분대로 흘러가는 게 더 좋다' } },
  { id: 'jp9',  dimension: 'JP', text: '프로젝트를 시작할 때?', optionA: { letter: 'J', label: '전체 계획과 단계를 먼저 잡고 시작한다' }, optionB: { letter: 'P', label: '일단 시작하고 진행하면서 방향을 잡는다' } },
  { id: 'jp10', dimension: 'JP', text: '일이 끝난 상태는?', optionA: { letter: 'J', label: '완벽하게 마무리하고 다음으로 넘어간다' }, optionB: { letter: 'P', label: '여러 일이 동시에 진행 중인 상태가 자연스럽다' } },
  { id: 'jp11', dimension: 'JP', text: '약속을 잡을 때?', optionA: { letter: 'J', label: '미리 일정을 확정짓는 걸 선호한다' }, optionB: { letter: 'P', label: '되도록 열어두고 가까워지면 정하는 편이다' } },
  { id: 'jp12', dimension: 'JP', text: '결정 후에?', optionA: { letter: 'J', label: '결정한 것을 지키고 바꾸지 않는다' }, optionB: { letter: 'P', label: '새로운 정보가 생기면 바꿀 수 있다' } },
  { id: 'jp13', dimension: 'JP', text: '지출을 할 때?', optionA: { letter: 'J', label: '예산을 정하고 계획적으로 쓴다' }, optionB: { letter: 'P', label: '필요하면 쓰고 나중에 확인한다' } },
  { id: 'jp14', dimension: 'JP', text: '할 일 목록은?', optionA: { letter: 'J', label: '리스트를 만들고 하나씩 체크하는 걸 좋아한다' }, optionB: { letter: 'P', label: '머릿속으로 대충 파악하고 진행한다' } },
  { id: 'jp15', dimension: 'JP', text: '마음이 편한 상태는?', optionA: { letter: 'J', label: '모든 게 처리되고 정리되어 있을 때' }, optionB: { letter: 'P', label: '가능성이 열려 있고 자유롭게 움직일 수 있을 때' } },
];

export function deriveMbtiType(answers: Record<string, string>): string {
  let E=0,I=0,S=0,N=0,T=0,F=0,J=0,P=0;
  for (const q of MBTI_TEST_QUESTIONS) {
    const ans = answers[q.id];
    if (!ans) continue;
    if (q.dimension === 'EI') { if (ans === 'E') E++; else I++; }
    else if (q.dimension === 'SN') { if (ans === 'S') S++; else N++; }
    else if (q.dimension === 'TF') { if (ans === 'T') T++; else F++; }
    else if (q.dimension === 'JP') { if (ans === 'J') J++; else P++; }
  }
  return (E >= I ? 'E' : 'I') + (S >= N ? 'S' : 'N') + (T >= F ? 'T' : 'F') + (J >= P ? 'J' : 'P');
}
