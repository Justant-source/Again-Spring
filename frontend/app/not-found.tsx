import Link from 'next/link';

export default function NotFound() {
  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center px-8 text-center gap-6"
      style={{ background: 'var(--L-bg)', color: 'var(--L-ink)' }}
    >
      <div
        className="serif"
        style={{ fontSize: 24, lineHeight: 1.5 }}
      >
        찾는 페이지가<br />여기엔 없네요.
      </div>
      <p
        style={{ color: 'var(--L-sub)', fontSize: 13, lineHeight: 1.7 }}
      >
        잠깐 길을 잃으셨나봐요.<br />
        다시봄의 첫 화면으로 돌아가시겠어요?
      </p>
      <Link href="/" className="btn-L">
        처음으로
      </Link>
    </div>
  );
}
