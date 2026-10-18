/**
 * 방문자 분류(visit_events.visitor_class) 한글 라벨·설명·요약 그룹 — 단일 출처.
 * 서버 enum 값이 SSOT이며, 모르는 값은 UNKNOWN_LEGACY 그룹으로 보수적으로 묶는다.
 */

export type VisitorGroupKey = 'realKr' | 'overseas' | 'bot' | 'suspect' | 'legacy';

export interface VisitorClassMeta {
  label: string;
  description: string;
  group: VisitorGroupKey;
}

export const VISITOR_CLASS_META: Record<string, VisitorClassMeta> = {
  HUMAN_KR: { label: '한국 방문자', description: '한국 IP의 일반 브라우저 방문 — 실제 사람으로 본다', group: 'realKr' },
  HUMAN_OVERSEAS: { label: '해외 방문자', description: '해외 IP의 일반 브라우저 방문', group: 'overseas' },
  HUMAN_UNKNOWN_GEO: { label: '위치 불명 방문자', description: '일반 브라우저지만 국가를 알 수 없는 방문', group: 'overseas' },
  BOT_SELF: { label: '자체 캡처 봇', description: '우리 영상 캡처(ASM) 트래픽', group: 'bot' },
  BOT_SCANNER: { label: '스캐너 봇', description: '취약점·경로 탐색 스캐너', group: 'bot' },
  BOT_CRAWLER: { label: '크롤러 봇', description: '검색엔진 등 크롤러', group: 'bot' },
  BOT_SOCIAL_PREVIEW: { label: '미리보기 봇', description: 'SNS·메신저 링크 미리보기 수집기', group: 'bot' },
  BOT_AUTOMATION: { label: '자동화 도구', description: '헤드리스 브라우저·스크립트 등 자동화', group: 'bot' },
  SUSPECT_SCANNER_PROXY: { label: '링크검사 의심', description: '구글 링크검사 직후 들어온 의심 방문', group: 'suspect' },
  UNKNOWN_LEGACY: { label: '구형 데이터', description: 'UA 없음 — 분류 도입 전 수집된 행', group: 'legacy' },
};

export const VISITOR_GROUPS: {
  key: VisitorGroupKey;
  label: string;
  color: string;
  testId: string;
}[] = [
  { key: 'realKr', label: '실제 한국 방문자', color: '#5F8F76', testId: 'acquisition-composition-realKr' },
  { key: 'overseas', label: '해외·위치불명 방문', color: '#4F7CAC', testId: 'acquisition-composition-overseas' },
  { key: 'bot', label: '봇·자동화', color: '#6B7280', testId: 'acquisition-composition-bot' },
  { key: 'suspect', label: '링크검사 의심', color: '#D97706', testId: 'acquisition-composition-suspect' },
  { key: 'legacy', label: '구형 데이터(UA 없음)', color: '#C4C9D1', testId: 'acquisition-composition-legacy' },
];

export function visitorClassMeta(cls: string): VisitorClassMeta {
  return (
    VISITOR_CLASS_META[cls] ?? {
      label: cls,
      description: '알 수 없는 분류',
      group: 'legacy',
    }
  );
}
