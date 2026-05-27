'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { AuthImage } from '../AuthImage';
import { type ContentResponse } from '@/lib/api/marketing/contentApi';
import { parseImagePaths, imageUrl, normalizeHashtags } from './parseImagePaths';

interface Props {
  content: ContentResponse;
}

const SLOT_PATTERN = /(<!-- IMG:[^>]+-->)/g;

export function NaverBlogPreview({ content }: Props) {
  const images = parseImagePaths(content.imagePaths);
  const tags = normalizeHashtags(content.hashtags);
  const markdown = content.bodyText || '';

  // split into alternating [text, marker, text, marker, ...]
  const parts = markdown.split(SLOT_PATTERN);

  return (
    <div
      style={{
        maxWidth: 720,
        background: '#FFFFFF',
        border: '1px solid #E5E5E5',
        borderRadius: 8,
        fontFamily: '"Noto Sans KR", "Apple SD Gothic Neo", sans-serif',
        overflow: 'hidden',
      }}
    >
      {/* Naver blog chrome */}
      <div
        style={{
          background: '#03C75A',
          padding: '10px 16px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span style={{ color: '#FFF', fontWeight: 700, fontSize: 15, letterSpacing: '-0.5px' }}>
          N 블로그
        </span>
        <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: 12 }}>— 미리보기</span>
      </div>

      {/* post header bar */}
      <div
        style={{
          padding: '16px 24px',
          borderBottom: '1px solid #EEEEEE',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}
      >
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: 'linear-gradient(135deg,#C76B4E,#8B5E3C)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#FFF',
            fontWeight: 700,
            fontSize: 14,
            flexShrink: 0,
          }}
        >
          다
        </div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#222' }}>dasibom</div>
          <div style={{ fontSize: 11, color: '#999' }}>다시봄 공식 블로그</div>
        </div>
      </div>

      {/* post body */}
      <div style={{ padding: '24px 32px' }}>
        {parts.map((part, idx) => {
          const isMarker = SLOT_PATTERN.test(part);
          // reset lastIndex since we're reusing the regex
          SLOT_PATTERN.lastIndex = 0;

          if (isMarker) {
            const matched = images.find((img) => img.slot === part.trim());
            if (matched) {
              return (
                <div
                  key={idx}
                  style={{ margin: '20px 0', textAlign: 'center' }}
                >
                  <AuthImage
                    src={imageUrl(matched.filename)}
                    alt={matched.alt || matched.role}
                    style={{
                      display: 'inline-block',
                      maxWidth: '100%',
                      borderRadius: 8,
                      border: '1px solid #EEEEEE',
                    }}
                  />
                  <div style={{ fontSize: 11, color: '#AAA', marginTop: 6 }}>
                    [{matched.role}] {matched.alt}
                  </div>
                </div>
              );
            }
            // fallback: show marker as code
            return (
              <div
                key={idx}
                style={{
                  margin: '12px 0',
                  padding: '8px 12px',
                  background: '#F7F7F7',
                  borderRadius: 4,
                  fontSize: 12,
                  color: '#999',
                  fontFamily: 'monospace',
                }}
              >
                {part} (이미지 없음)
              </div>
            );
          }

          if (!part.trim()) return null;

          return (
            <div
              key={idx}
              style={{
                fontSize: 16,
                color: '#333',
                lineHeight: 1.8,
                wordBreak: 'keep-all',
              }}
            >
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  h1: ({ children }) => (
                    <h1 style={{ fontSize: 24, fontWeight: 700, margin: '0 0 16px', color: '#111', lineHeight: 1.4 }}>
                      {children}
                    </h1>
                  ),
                  h2: ({ children }) => (
                    <h2 style={{ fontSize: 20, fontWeight: 700, margin: '20px 0 12px', color: '#222' }}>
                      {children}
                    </h2>
                  ),
                  h3: ({ children }) => (
                    <h3 style={{ fontSize: 17, fontWeight: 600, margin: '16px 0 8px', color: '#333' }}>
                      {children}
                    </h3>
                  ),
                  p: ({ children }) => (
                    <p style={{ margin: '0 0 14px', lineHeight: 1.8 }}>{children}</p>
                  ),
                  blockquote: ({ children }) => (
                    <blockquote
                      style={{
                        margin: '16px 0',
                        padding: '12px 16px',
                        background: '#F7F7F7',
                        borderLeft: '4px solid #999',
                        color: '#555',
                        fontSize: 15,
                      }}
                    >
                      {children}
                    </blockquote>
                  ),
                  strong: ({ children }) => (
                    <strong style={{ fontWeight: 700, color: '#111' }}>{children}</strong>
                  ),
                }}
              >
                {part}
              </ReactMarkdown>
            </div>
          );
        })}

        {/* hashtags */}
        {tags.length > 0 && (
          <div style={{ marginTop: 24, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {tags.map((tag) => (
              <span
                key={tag}
                style={{
                  background: '#F4F4F4',
                  color: '#666',
                  borderRadius: 12,
                  padding: '4px 10px',
                  fontSize: 12,
                }}
              >
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
