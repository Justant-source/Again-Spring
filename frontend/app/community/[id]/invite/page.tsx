import { redirect } from 'next/navigation';

interface InvitePageProps {
  params: { id: string };
}

export default function InvitePage({ params }: InvitePageProps) {
  redirect(`/community/${params.id}`);
}
