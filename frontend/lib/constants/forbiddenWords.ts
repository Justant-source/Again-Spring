export const CRISIS_KEYWORDS: Record<string, string[]> = {
  domestic_violence: [
    '때리', '때렸', '맞았', '맞고', '폭행', '폭력',
    '학대', '때릴', '때려', '구타', '상해',
  ],
  sexual_violence: [
    '강간', '성폭행', '성폭력', '강제로',
  ],
  self_harm: [
    '죽고 싶', '죽고싶', '자살', '자해',
    '뛰어내리', '목 매', '목매', '약 먹고 죽',
  ],
  child_abuse: [
    '아이를 때', '애를 때', '아동학대',
  ],
};

export const WARNING_KEYWORDS: Record<string, string[]> = {
  legal: ['이혼', '절연', '고소', '신고', '소송', '변호사'],
  extreme_emotion: ['미치겠', '참을 수 없', '죽여버리'],
};

export const FORBIDDEN_UI_WORDS = [
  '과실비율', '판결', '판사', '유죄', '무죄',
  '가해자', '피해자', '승자', '패자',
  '나르시시스트', '소시오패스', '가스라이팅',
  '손절', '절교', '절연',
];
