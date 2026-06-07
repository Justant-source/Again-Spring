/**
 * /community/[id] — 서버 컴포넌트 래퍼
 *
 * - generateMetadata: 서버에서 글 데이터를 fetch해 og:title / og:description / og:url 동적 생성
 * - opengraph-image.tsx 가 og:image / twitter:image 를 자동 주입 (여기서 중복 지정 X)
 * - 실제 인터랙티브 UI 는 PostDetailClient.tsx ('use client') 에 위임
 *
 * Next 14.2: params 는 sync 객체 (Promise 형은 15+). 서버 page가 'use client' 자식에
 * plain serializable props 전달은 App Router 정석 패턴.
 */
import type { Metadata } from 'next';
import PostDetailClient from './PostDetailClient';
import { fetchPostForOg } from '@/lib/og/fetchPostForOg';

interface PageProps {
  params: { id: string };
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const p = await fetchPostForOg(params.id);

  // metadataBase (layout.tsx) 기준으로 상대 URL → 절대 https URL 로 해소
  const url = `/community/${params.id}`;

  // 비공개 / DRAFT / BLOCKED / 없는 글 → 제너릭 메타 (제목·본문 누설 금지)
  if (!p.ok || !p.crawlable) {
    return {
      title: '다시봄 · 사연',
      description: '관계 회복을 돕는 AI 중재자, 다시봄.',
      openGraph: { title: '다시봄', url, type: 'article' },
    };
  }

  // ── 공개·투표 가능 글 ────────────────────────────────────────────
  // 카톡 제목: 브랜드 훅 (전 글 공통 — 호기심 유발)
  const hook = '이 갈등, 당신은 누구에게 공감하나요?';

  // 카톡 설명: 실시간 비율 + 참여자 수
  const desc =
    p.totalVotes > 0
      ? `작성자 ${p.authorPct}% · 상대방 ${p.partnerPct}% — ${p.totalVotes}명이 함께 봤어요`
      : '아직 공감 전 — 당신의 생각을 들려주세요';

  return {
    title: hook,
    description: desc,
    openGraph: {
      title: hook,
      description: desc,
      url,
      type: 'article',
    },
    twitter: {
      card: 'summary_large_image',
      title: hook,
      description: desc,
    },
    // og:image / twitter:image 는 opengraph-image.tsx 가 자동 주입 — 여기서 중복 지정 X
  };
}

export default function Page({ params }: PageProps) {
  return <PostDetailClient params={params} />;
}
