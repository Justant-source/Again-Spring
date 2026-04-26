export interface CrisisResource {
  label: string;
  phone: string;
  hours: string;
  description: string;
  category: 'immediate' | 'legal';
}

export const CRISIS_RESOURCES_IMMEDIATE: CrisisResource[] = [
  {
    label: '여성긴급전화 (가정폭력·성폭력)',
    phone: '1366',
    hours: '24시간 · 무료',
    description: '가정폭력·성폭력 긴급 상담 및 법률·의료 지원 연계',
    category: 'immediate',
  },
  {
    label: '자살예방상담전화',
    phone: '1393',
    hours: '24시간 · 무료',
    description: '자살 위기 상담 전용',
    category: 'immediate',
  },
  {
    label: '정신건강 위기상담',
    phone: '1577-0199',
    hours: '24시간 · 무료',
    description: '정신건강 위기 상황, 자살·자해 고위험 상담',
    category: 'immediate',
  },
  {
    label: '아동학대 신고',
    phone: '112',
    hours: '24시간',
    description: '또는 1391 (아동권리보장원)',
    category: 'immediate',
  },
];

export const CRISIS_RESOURCES_LEGAL: CrisisResource[] = [
  {
    label: '대한법률구조공단',
    phone: '132',
    hours: '평일 09–18',
    description: '이혼·소송 등 법적 사안 무료 안내',
    category: 'legal',
  },
  {
    label: '한국가정법률상담소',
    phone: '1644-7077',
    hours: '평일 09–18',
    description: '가족·이혼 관련 법률 상담 (무료)',
    category: 'legal',
  },
  {
    label: '건강가정지원센터',
    phone: '1577-9337',
    hours: '평일 09–18',
    description: '시·군·구별 무료 가족 상담',
    category: 'legal',
  },
];

export const CRISIS_RESOURCES: CrisisResource[] = [
  ...CRISIS_RESOURCES_IMMEDIATE,
  ...CRISIS_RESOURCES_LEGAL,
];
