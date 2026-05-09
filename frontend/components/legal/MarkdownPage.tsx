'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';

export default function MarkdownPage({ src, title }: { src: string; title: string }) {
  const router = useRouter();
  const [content, setContent] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(src)
      .then((r) => r.text())
      .then((text) => { setContent(text); setLoading(false); })
      .catch(() => { setContent('문서를 불러올 수 없습니다.'); setLoading(false); });
  }, [src]);

  return (
    <PhoneFrame tone="Q">
      <PhoneHeader title={title} tone="L" onBack={() => router.back()} />
      <div style={{ padding: '8px 28px 60px', fontSize: 14, lineHeight: 1.75, color: 'var(--Q-ink)', overflowWrap: 'break-word' }}>
        {loading ? (
          <div style={{ paddingTop: 40, textAlign: 'center', color: 'var(--Q-sub)', fontSize: 13 }}>불러오는 중…</div>
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              h1: ({ children }) => <h1 style={{ fontSize: 18, fontWeight: 600, marginTop: 28, marginBottom: 10 }}>{children}</h1>,
              h2: ({ children }) => <h2 style={{ fontSize: 16, fontWeight: 600, marginTop: 22, marginBottom: 8 }}>{children}</h2>,
              h3: ({ children }) => <h3 style={{ fontSize: 14, fontWeight: 600, marginTop: 16, marginBottom: 6 }}>{children}</h3>,
              p: ({ children }) => <p style={{ marginBottom: 10 }}>{children}</p>,
              ul: ({ children }) => <ul style={{ paddingLeft: 20, marginBottom: 10 }}>{children}</ul>,
              ol: ({ children }) => <ol style={{ paddingLeft: 20, marginBottom: 10 }}>{children}</ol>,
              li: ({ children }) => <li style={{ marginBottom: 4 }}>{children}</li>,
              hr: () => <hr style={{ margin: '20px 0', border: 'none', borderTop: '1px solid var(--Q-border)' }} />,
              strong: ({ children }) => <strong style={{ fontWeight: 600 }}>{children}</strong>,
              blockquote: ({ children }) => (
                <blockquote style={{ margin: '16px 0', padding: '12px 14px', background: 'var(--Q-card)', borderLeft: '3px solid var(--Q-border)', borderRadius: 4, fontSize: 13, color: 'var(--Q-sub)' }}>
                  {children}
                </blockquote>
              ),
              table: ({ children }) => (
                <div style={{ overflowX: 'auto', marginBottom: 14 }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>{children}</table>
                </div>
              ),
              th: ({ children }) => <th style={{ textAlign: 'left', padding: '6px 10px', background: 'var(--Q-card)', borderBottom: '1px solid var(--Q-border)', fontWeight: 600 }}>{children}</th>,
              td: ({ children }) => <td style={{ padding: '6px 10px', borderBottom: '1px solid var(--Q-border)' }}>{children}</td>,
            }}
          >
            {content}
          </ReactMarkdown>
        )}
      </div>
    </PhoneFrame>
  );
}
