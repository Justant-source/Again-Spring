'use client';

import { useState } from 'react';
import { AuthImage } from '../AuthImage';
import { type ContentResponse } from '@/lib/api/marketing/contentApi';
import { parseImagePaths, imageUrl, normalizeHashtags } from './parseImagePaths';

// METAPHOR_COVER is slide 1 (hook card); remaining are the LLM-generated story cards
const CARD_ROLES = new Set(['METAPHOR_COVER', 'COVER', 'SCENE', 'FEELING', 'NVC', 'CTA', 'BONUS']);

interface Props {
  content: ContentResponse;
}

export function InstagramPreview({ content }: Props) {
  const [slideIdx, setSlideIdx] = useState(0);

  const images = parseImagePaths(content.imagePaths);
  const slides = images.filter((img) => CARD_ROLES.has(img.role));
  const tags = normalizeHashtags(content.hashtags);
  const caption = content.bodyText || '';

  const total = slides.length;
  const canPrev = slideIdx > 0;
  const canNext = slideIdx < total - 1;

  return (
    <div
      style={{
        width: 400,
        border: '1px solid #DBDBDB',
        borderRadius: 12,
        background: '#FFFFFF',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        overflow: 'hidden',
      }}
    >
      {/* profile header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 12px',
          borderBottom: '1px solid #DBDBDB',
        }}
      >
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: '50%',
            background: 'linear-gradient(135deg,#f09433,#e6683c,#dc2743,#cc2366,#bc1888)',
            padding: 2,
            flexShrink: 0,
          }}
        >
          <div
            style={{
              width: '100%',
              height: '100%',
              borderRadius: '50%',
              background: 'linear-gradient(135deg,#C76B4E,#8B5E3C)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#FFF',
              fontSize: 11,
              fontWeight: 700,
            }}
          >
            다
          </div>
        </div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#262626', lineHeight: 1.2 }}>
            dasibom_official
          </div>
          <div style={{ fontSize: 11, color: '#8E8E8E' }}>다시봄</div>
        </div>
        <span style={{ marginLeft: 'auto', fontSize: 20, color: '#262626', cursor: 'default' }}>···</span>
      </div>

      {/* carousel */}
      {total > 0 ? (
        <div style={{ position: 'relative', width: 400, height: 400, background: '#F5F5F5' }}>
          <AuthImage
            src={imageUrl(slides[slideIdx].filename)}
            alt={slides[slideIdx].alt || slides[slideIdx].role}
            style={{ display: 'block', width: 400, height: 400, objectFit: 'cover' }}
          />

          {/* prev button */}
          {canPrev && (
            <button
              onClick={() => setSlideIdx((i) => i - 1)}
              style={{
                position: 'absolute',
                left: 8,
                top: '50%',
                transform: 'translateY(-50%)',
                width: 28,
                height: 28,
                borderRadius: '50%',
                background: 'rgba(255,255,255,0.85)',
                border: 'none',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
                boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
              }}
            >
              ‹
            </button>
          )}

          {/* next button */}
          {canNext && (
            <button
              onClick={() => setSlideIdx((i) => i + 1)}
              style={{
                position: 'absolute',
                right: 8,
                top: '50%',
                transform: 'translateY(-50%)',
                width: 28,
                height: 28,
                borderRadius: '50%',
                background: 'rgba(255,255,255,0.85)',
                border: 'none',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
                boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
              }}
            >
              ›
            </button>
          )}

          {/* slide counter badge */}
          {total > 1 && (
            <div
              style={{
                position: 'absolute',
                top: 8,
                right: 8,
                background: 'rgba(0,0,0,0.6)',
                color: '#FFF',
                fontSize: 11,
                padding: '2px 7px',
                borderRadius: 10,
              }}
            >
              {slideIdx + 1}/{total}
            </div>
          )}
        </div>
      ) : (
        <div
          style={{
            width: 400,
            height: 400,
            background: '#F5F5F5',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#AAAAAA',
            fontSize: 13,
          }}
        >
          이미지 없음
        </div>
      )}

      {/* dot indicator */}
      {total > 1 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            gap: 4,
            padding: '8px 0 4px',
          }}
        >
          {slides.map((_, i) => (
            <div
              key={i}
              onClick={() => setSlideIdx(i)}
              style={{
                width: i === slideIdx ? 6 : 5,
                height: i === slideIdx ? 6 : 5,
                borderRadius: '50%',
                background: i === slideIdx ? '#0095F6' : '#A8A8A8',
                cursor: 'pointer',
                transition: 'background 0.15s',
              }}
            />
          ))}
        </div>
      )}

      {/* action row */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          padding: '8px 12px 4px',
          gap: 16,
          color: '#262626',
          fontSize: 22,
        }}
      >
        <span style={{ cursor: 'default' }}>♡</span>
        <span style={{ cursor: 'default' }}>💬</span>
        <span style={{ cursor: 'default' }}>✈</span>
        <span style={{ marginLeft: 'auto', cursor: 'default' }}>⊓</span>
      </div>

      {/* caption */}
      <div style={{ padding: '0 12px 12px' }}>
        {caption && (
          <p
            style={{
              margin: '4px 0 6px',
              fontSize: 14,
              color: '#262626',
              lineHeight: 1.6,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            <span style={{ fontWeight: 600 }}>dasibom_official</span> {caption}
          </p>
        )}

        {tags.length > 0 && (
          <p style={{ margin: 0, fontSize: 13, color: '#00376B', lineHeight: 1.6 }}>
            {tags.join(' ')}
          </p>
        )}
      </div>
    </div>
  );
}
