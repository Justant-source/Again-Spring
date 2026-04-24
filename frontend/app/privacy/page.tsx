'use client';

import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';

export default function PrivacyPage() {
  const router = useRouter();

  return (
    <PhoneFrame tone="Q">
      <PhoneHeader
        title="개인정보 처리방침"
        tone="L"
        onBack={() => router.back()}
      />
      <div
        style={{
          padding: '8px 28px 40px',
          fontSize: '14px',
          lineHeight: 1.7,
          color: 'var(--Q-ink)',
        }}
      >
        {/* Section 1 */}
        <h1 style={{ fontSize: '18px', fontWeight: 600, marginTop: 24, marginBottom: 12 }}>
          개인정보 처리방침
        </h1>
        <p style={{ fontSize: '12px', color: 'var(--Q-sub)', marginBottom: 24 }}>
          마지막 업데이트: 2026년 4월
        </p>

        {/* Section: 수집 항목 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          1. 수집하는 개인정보
        </h2>
        <p style={{ marginBottom: 12 }}>
          다시봄(Again Spring) 서비스는 다음과 같은 개인정보를 수집합니다:
        </p>
        <ul style={{ paddingLeft: '20px', marginBottom: 12 }}>
          <li>
            <b>필수 정보</b>: 이메일, 닉네임, 회원가입 시 선택한 대화 성향 설문 응답
          </li>
          <li>
            <b>세션 데이터</b>: 갈등 상황 설명, 상대방 입력 내용 (원문 및 요약), 생성된 보고서
          </li>
          <li>
            <b>이용 정보</b>: 접속 날짜/시간, 기기 정보, 로그인 기록
          </li>
        </ul>

        {/* Section: 보유 기간 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          2. 보유 기간
        </h2>
        <ul style={{ paddingLeft: '20px' }}>
          <li>
            <b>세션 원문</b>: 최대 30일 자동 삭제
          </li>
          <li>
            <b>생성된 보고서 및 요약</b>: 이용자 삭제 시까지 또는 계정 삭제 시 함께 삭제
          </li>
          <li>
            <b>회원 정보</b>: 계정 삭제 요청 시 즉시 삭제 (법정 의무 보존 기간 제외)
          </li>
        </ul>

        {/* Section: 제3자 제공 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          3. 제3자 제공
        </h2>
        <p>
          다시봄은 이용자의 개인정보를 제3자에게 제공하지 않습니다.
          단, 다음의 경우는 예외입니다:
        </p>
        <ul style={{ paddingLeft: '20px' }}>
          <li>법원 또는 수사기관의 법정 요청</li>
          <li>이용자의 명시적 동의</li>
        </ul>

        {/* Section: 미성년자 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          4. 미성년자 이용 제한
        </h2>
        <p>
          본 서비스는 만 18세 이상의 이용자를 대상으로 합니다.
          미성년자는 회원가입이 불가능하며,
          미성년자로부터 수집된 개인정보는 즉시 삭제합니다.
        </p>

        {/* Section: 보안 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          5. 정보 보안
        </h2>
        <p>
          다시봄은 이용자의 개인정보를 보호하기 위해 다음과 같은 조치를
          취합니다:
        </p>
        <ul style={{ paddingLeft: '20px' }}>
          <li>전송 시 SSL 암호화</li>
          <li>데이터베이스 접근 제한</li>
          <li>정기적인 보안 감사</li>
        </ul>

        {/* Section: 이용자 권리 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          6. 이용자의 권리
        </h2>
        <p style={{ marginBottom: 12 }}>
          이용자는 다음의 권리를 행사할 수 있습니다:
        </p>
        <ul style={{ paddingLeft: '20px', marginBottom: 12 }}>
          <li>개인정보 열람 요청</li>
          <li>오류 수정 요청</li>
          <li>삭제 요청</li>
          <li>이용 정지 요청</li>
        </ul>
        <p>
          위 권리 행사는 문의 이메일을 통해 언제든 요청할 수 있습니다.
        </p>

        {/* Section: AI 학습 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          7. AI 학습에 대한 정보
        </h2>
        <p>
          다시봄은 이용자의 입력 데이터(갈등 상황, 감정 표현 등)를 AI 모델
          학습에 사용하지 않습니다. 모든 데이터는 이용자의 개인용 세션 분석에만
          사용되며, 익명화되어 저장되지 않습니다.
        </p>

        {/* Section: 정책 변경 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          8. 정책 변경
        </h2>
        <p>
          본 개인정보 처리방침은 법률 변경이나 서비스 개선에 따라 변경될 수
          있습니다. 변경 시 서비스 화면에 공지합니다.
        </p>

        {/* Section: 문의 */}
        <h2
          style={{
            fontSize: '16px',
            fontWeight: 600,
            marginTop: 20,
            marginBottom: 12,
          }}
        >
          9. 문의
        </h2>
        <p>
          개인정보 처리에 관한 문의는 다음으로 연락주시기 바랍니다:
        </p>
        <p style={{ marginTop: 12, paddingLeft: '20px' }}>
          <b>이메일</b>: support@again-spring.com
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
          <p>본 정책은 서비스 이용 시점에 적용됩니다.</p>
        </div>
      </div>
    </PhoneFrame>
  );
}
