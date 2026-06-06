'use client';

import { redirect } from 'next/navigation';

interface Props {
  params: { id: string };
  searchParams?: { highlight?: string };
}

export default function CommentsPage({ params, searchParams }: Props) {
  const q = searchParams?.highlight ? `?highlight=${searchParams.highlight}` : '';
  redirect(`/community/${params.id}${q}`);
}
