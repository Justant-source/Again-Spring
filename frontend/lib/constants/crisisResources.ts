export interface CrisisResource {
  label: string;
  phone: string;
  hours: string;
  description: string;
}

export const CRISIS_RESOURCES: CrisisResource[] = [
  {
    label: '여성긴급전화 (가정폭력·성폭력)',
    phone: '1366',
    hours: '24시간 · 무료',
    description: '가정폭력·성폭력 긴급 상담 및 법률·의료 지원 연계',
  },
  {
    label: '정신건강 위기상담',
    phone: '1577-0199',
    hours: '24시간 · 무료',
    description: '정신건강 위기 상황, 자살·자해 고위험 상담',
  },
  {
    label: '자살예방상담',
    phone: '1393',
    hours: '24시간 · 무료',
    description: '자살 위기 상담 전용',
  },
  {
    label: '아동학대 신고',
    phone: '112',
    hours: '24시간',
    description: '또는 1391 (아동권리보장원)',
  },
  {
    label: '청소년상담',
    phone: '1388',
    hours: '24시간',
    description: '청소년 고민·상담 전화',
  },
  {
    label: '대한법률구조공단',
    phone: '132',
    hours: '평일 09–18',
    description: '이혼·소송 등 법적 사안 안내',
  },
];
