'use client';

interface LegalNoticeBoxProps {
  message?: string;
  testId?: string;
}

export default function LegalNoticeBox({ message, testId = 'ratio-legal-notice' }: LegalNoticeBoxProps) {
  const defaultMsg = '이 결과는 공감 분포일 뿐 법적 책임이나 과실 비율과 무관합니다. AI 분석에는 한계가 있으며, 전문 상담을 권장합니다.';
  return (
    <div
      data-testid={testId}
      style={{
        marginTop: 14,
        background: 'color-mix(in srgb, var(--P-sub) 6%, transparent)',
        border: '1px solid color-mix(in srgb, var(--P-sub) 15%, transparent)',
        borderRadius: 10,
        padding: '12px 14px',
        fontSize: 12,
        color: 'var(--P-sub)',
        lineHeight: 1.7,
      }}
    >
      {message ?? defaultMsg}
    </div>
  );
}
