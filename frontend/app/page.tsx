// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
'use client';

import Link from 'next/link';
import { PhoneFrame } from '@/components/shared/PhoneFrame';
import { Logo } from '@/components/shared/Logo';

const CHIPS = ['연인', '부부', '친구', '가족', '부모자식'];

export default function LandingPage() {
  return (
    <PhoneFrame tone="L">
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5">
        <div className="flex items-center justify-between">
          <Logo />
          <Link
            href="/login"
            className="text-[12px]"
            style={{ color: 'var(--L-sub)' }}
          >
            로그인
          </Link>
        </div>

        <div className="flex-1 mt-20">
          <div
            className="text-[13px] mb-3.5"
            style={{ color: 'var(--L-sub)' }}
          >
            관계 회복 AI 중재자
          </div>
          <h1
            className="serif"
            style={{
              fontSize: 32,
              lineHeight: 1.35,
              letterSpacing: '-0.01em',
            }}
          >
            지금, 누군가와<br />서운한 일이<br />있으신가요.
          </h1>
          <p
            className="mt-5 text-[14px] leading-[1.7]"
            style={{ color: 'var(--L-sub)' }}
          >
            판결이 아니라, 중재입니다.<br />
            두 사람의 마음을 차분히 정리해드려요.
          </p>

          <div className="mt-9 flex gap-2 flex-wrap">
            {CHIPS.map((c) => (
              <span key={c} className="chip-L">
                {c}
              </span>
            ))}
          </div>

          <div
            className="mt-12 letter-card"
            style={{ padding: 20 }}
          >
            <div
              className="quote-it"
              style={{ fontSize: 12, marginBottom: 10 }}
            >
              다시봄은 이런 마음입니다
            </div>
            <ul
              className="serif"
              style={{ fontSize: 13, lineHeight: 1.9 }}
            >
              <li>· 원문은 두 분 모두 적으신 후에 공개돼요</li>
              <li>· 옳고 그름이 아니라 서로의 욕구를 봐요</li>
              <li>· 이야기는 30일 후 자동으로 사라져요</li>
            </ul>
          </div>
        </div>

        <div className="flex flex-col gap-2 pb-2 pt-4">
          <Link href="/session/new" className="btn-L text-center">
            이야기 시작하기
          </Link>
          <Link
            href="/guest"
            className="text-center text-[12px] mt-1"
            style={{ color: 'var(--L-sub)' }}
          >
            게스트로 둘러보기
          </Link>
        </div>
      </div>
    </PhoneFrame>
  );
}
