'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ChatLayout } from '@/components/chat/ChatLayout';
import { api } from '@/lib/api/client';

export default function ChatPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [session, setSession] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get(`/api/sessions/${id}`)
      .then(r => {
        setSession(r.data);
        setLoading(false);
      })
      .catch(() => router.push('/'));
  }, [id]);

  if (loading || !session) return null;

  if (session.status === 'completed') {
    router.push(`/session/result/${id}`);
    return null;
  }

  return <ChatLayout sessionId={id} session={session} />;
}
