'use client';

import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';

export default function TermsPage() {
  const router = useRouter();

  return (
    <PhoneFrame tone="Q">
      <PhoneHeader
        title="이용약관"
        tone="L"
        onBack={() => router.back()}
      />
      <div style={{ padding: '8px 28px 40px', fontSize: '14px', lineHeight: 1.7, color: 'var(--Q-ink)' }}>
        {/* Warning notice */}
        <div
          style={{
            background: 'var(--Q-card)',
            border: '1px solid var(--Q-border)',
            borderRadius: '3px',
            padding: '14px',
            marginBottom: '28px',
            fontSize: '12px',
            color: 'var(--Q-sub)',
          }}
        >
          ⚠️ 이 약관은 참고용이며, 실제 서비스 런칭 전 반드시 변호사 검토가 필요합니다.
        </div>

        {/* Section 1 */}
        <h1 style={{ fontSize: '18px', fontWeight: 600, marginTop: 24, marginBottom: 12 }}>
          제1조 (목적)
        </h1>
        <p>
          본 약관은 "다시봄(Again Spring)"(이하 "서비스")가 제공하는 AI 기반 관계 회복 지원 서비스의 이용과 관련하여, 서비스와 이용자 간의 권리, 의무 및 책임 사항을 규정함을 목적으로 합니다.
        </p>

        {/* Section 2 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제2조 (서비스의 성격)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 본 서비스는 대화형 AI를 활용하여 이용자의 대인 관계 갈등 상황에 대한 <b>정리·해석·소통 가이드</b>를 제공하는 도구입니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. 본 서비스는 다음 각 호의 행위를 제공하지 <b>않습니다</b>:
        </p>
        <ul style={{ marginBottom: 12, paddingLeft: '20px' }}>
          <li>심리 상담, 정신의학적 진단, 치료</li>
          <li>법률 자문, 법적 판단, 소송 대리</li>
          <li>의료적 진단 또는 치료 권고</li>
          <li>배우자·가족·지인에 대한 신원 조사</li>
          <li>법정 증거로 사용될 수 있는 공식 판정</li>
        </ul>
        <p>
          3. 이용자는 본 서비스의 결과물이 전문가의 상담·조언을 대체할 수 없음을 인지하고 이용해야 합니다.
        </p>

        {/* Section 3 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제3조 (이용자의 책임)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 이용자는 서비스에 입력한 모든 정보의 진실성에 대해 스스로 책임을 집니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. 이용자는 다음 상황에서 본 서비스를 이용하지 않고 <b>즉시 전문 기관</b>에 연락해야 합니다:
        </p>
        <ul style={{ marginBottom: 12, paddingLeft: '20px' }}>
          <li>가정폭력, 성폭력, 아동학대 상황</li>
          <li>자해 또는 자살 충동</li>
          <li>생명이나 신체에 대한 위협</li>
          <li>긴급한 법적 분쟁</li>
        </ul>
        <p>
          3. 이용자는 본 서비스의 결과물을 다음 용도로 사용할 수 없습니다:
        </p>
        <ul style={{ paddingLeft: '20px' }}>
          <li>상대방을 협박하거나 강요하는 수단</li>
          <li>법정·행정 기관에서의 증거 자료</li>
          <li>상대방에 대한 명예훼손, 모욕 행위</li>
          <li>제3자에게 특정 개인의 사생활을 노출</li>
        </ul>

        {/* Section 4 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제4조 (전문 기관 연락처)
        </h2>
        <p>
          본 서비스 이용 중 또는 이용 전후 다음과 같은 상황이 발생한 경우 아래 기관에 즉시 연락하시기 바랍니다:
        </p>
        <ul style={{ paddingLeft: '20px' }}>
          <li><b>가정폭력·성폭력</b>: 여성긴급전화 1366 (24시간)</li>
          <li><b>정신건강 위기</b>: 정신건강위기상담 1577-0199 (24시간)</li>
          <li><b>아동학대</b>: 112 또는 아동보호전문기관 1391</li>
          <li><b>청소년 상담</b>: 1388 (24시간)</li>
          <li><b>자살예방</b>: 자살예방상담 1393 (24시간)</li>
          <li><b>법률 상담</b>: 대한법률구조공단 132</li>
        </ul>

        {/* Section 5 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제5조 (AI 판단의 한계)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 본 서비스의 AI는 입력된 정보만을 기반으로 판단하며, 실제 상황의 전모를 파악할 수 없습니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. AI의 판단은 확률적 추정이며, 다음과 같은 한계가 있습니다:
        </p>
        <ul style={{ marginBottom: 12, paddingLeft: '20px' }}>
          <li>입력의 편향에 따른 결과 왜곡</li>
          <li>맥락과 비언어적 단서 누락</li>
          <li>관계의 장기적 역사 반영 한계</li>
        </ul>
        <p>
          3. 이용자는 AI의 결과를 참고 자료로만 활용하고, 중요한 관계·인생 결정은 전문가 상담과 본인의 판단을 거쳐야 합니다.
        </p>

        {/* Section 6 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제6조 (개인정보 보호)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 서비스는 이용자의 입력 원문을 <b>최대 30일</b> 보관한 후 자동 삭제합니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. 이용자가 삭제를 요청하는 경우 즉시 해당 데이터를 삭제합니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          3. 세션 이력 화면에서 보이는 내용은 AI가 생성한 요약본과 결과 리포트로 한정되며, 원문 대화는 포함되지 않습니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          4. 서비스는 이용자의 데이터를 AI 학습용으로 사용하지 않습니다.
        </p>
        <p>
          5. 자세한 내용은 "개인정보 처리방침"을 참조하시기 바랍니다.
        </p>

        {/* Section 7 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제7조 (콘텐츠 공유)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 이용자는 결과 리포트의 <b>추상화된 시각 자료</b>(욕구 차이 지도 등)를 제3자와 공유할 수 있습니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. 갈등의 구체적 내용이 포함된 정보는 이용자 본인의 판단과 책임 하에 공유하며, 서비스는 이로 인한 분쟁에 대해 책임지지 않습니다.
        </p>
        <p>
          3. 상대방의 동의 없이 상대방의 입력 내용을 제3자에게 공개하는 행위는 금지됩니다.
        </p>

        {/* Section 8 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제8조 (서비스 이용 제한)
        </h2>
        <p>
          다음 각 호에 해당하는 이용자는 서비스 이용이 제한될 수 있습니다:
        </p>
        <ul style={{ paddingLeft: '20px' }}>
          <li>타인을 지속적으로 협박·모욕하기 위한 용도로 서비스를 사용하는 자</li>
          <li>허위 정보를 반복적으로 입력하여 AI 판단을 왜곡시키려는 자</li>
          <li>서비스 운영을 방해하거나 타인의 이용을 방해하는 자</li>
          <li>본 약관 제3조의 책임을 위반하는 자</li>
        </ul>

        {/* Section 9 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제9조 (면책 조항)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 서비스는 AI 판단의 정확성을 보장하지 않습니다.
        </p>
        <p style={{ marginBottom: 12 }}>
          2. 서비스 이용으로 인해 발생한 다음 사항에 대해 서비스는 책임지지 않습니다:
        </p>
        <ul style={{ marginBottom: 12, paddingLeft: '20px' }}>
          <li>이용자 간 관계의 악화 또는 파국</li>
          <li>이용자의 정신적·경제적 손실</li>
          <li>이용자가 서비스 결과를 제3자에게 공개함으로 인한 분쟁</li>
          <li>서비스가 권고하지 않은 행동의 결과</li>
        </ul>
        <p>
          3. 다만 서비스의 고의 또는 중대한 과실로 인한 손해에 대해서는 관련 법률에 따라 책임집니다.
        </p>

        {/* Section 10 */}
        <h2 style={{ fontSize: '16px', fontWeight: 600, marginTop: 20, marginBottom: 12 }}>
          제10조 (분쟁 해결)
        </h2>
        <p style={{ marginBottom: 12 }}>
          1. 서비스와 이용자 간 분쟁은 상호 협의를 통해 해결함을 원칙으로 합니다.
        </p>
        <p>
          2. 협의가 이루어지지 않는 경우 대한민국 법률과 민사소송법에 따라 관할 법원에서 해결합니다.
        </p>

        {/* Closing */}
        <div
          style={{
            marginTop: 40,
            paddingTop: 20,
            borderTop: '1px solid var(--Q-border)',
            fontSize: '12px',
            color: 'var(--Q-sub)',
            textAlign: 'center',
          }}
        >
          <p>본 약관은 2026년부터 시행됩니다.</p>
          <p style={{ marginTop: 8 }}>마지막 업데이트: 2026년 4월</p>
        </div>
      </div>
    </PhoneFrame>
  );
}
