'use client';

import { AuthImage } from '../AuthImage';
import { type ContentResponse } from '@/lib/api/marketing/contentApi';
import { parseImagePaths, imageUrl, normalizeHashtags } from './parseImagePaths';

interface Props {
  content: ContentResponse;
}

export function XPreview({ content }: Props) {
  const images = parseImagePaths(content.imagePaths);
  const metaphorCover = images.find((img) => img.role === 'METAPHOR_COVER');
  const quoteCard = images.find((img) => img.role === 'QUOTE_CARD');
  const chatImg = images.find((img) => img.role === 'CHAT_PREVIEW');
  const tags = normalizeHashtags(content.hashtags);

  const rawTweets = (content.bodyText || '').split(/\n{2,}/).map((t) => t.trim()).filter(Boolean);
  const tweets = rawTweets.length > 0 ? rawTweets : ['(본문 없음)'];

  return (
    <div
      style={{
        maxWidth: 560,
        border: '1px solid #EFF3F4',
        borderRadius: 12,
        background: '#FFFFFF',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        overflow: 'hidden',
      }}
    >
      {tweets.map((tweet, idx) => {
        const isFirst = idx === 0;
        const isLast = idx === tweets.length - 1;
        const showMetaphorCover = isFirst && metaphorCover;
        const showQuote = isFirst && !metaphorCover && quoteCard;
        const showChat = isLast && chatImg;
        const showTags = isLast && tags.length > 0;

        return (
          <div
            key={idx}
            style={{
              padding: '12px 16px',
              borderBottom: isLast ? 'none' : '1px solid #EFF3F4',
              position: 'relative',
            }}
          >
            {/* thread line */}
            {tweets.length > 1 && !isLast && (
              <div
                style={{
                  position: 'absolute',
                  left: 32,
                  top: 52,
                  bottom: -1,
                  width: 2,
                  background: '#CFD9DE',
                }}
              />
            )}

            <div style={{ display: 'flex', gap: 10 }}>
              {/* avatar */}
              <div
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg,#C76B4E,#8B5E3C)',
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#FFF',
                  fontSize: 12,
                  fontWeight: 700,
                }}
              >
                다
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                {/* header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 4 }}>
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#0F1419' }}>다시봄</span>
                  <span style={{ fontSize: 13, color: '#536471' }}>@dasibom</span>
                  {tweets.length > 1 && (
                    <span style={{ marginLeft: 'auto', fontSize: 12, color: '#536471' }}>
                      {idx + 1}/{tweets.length}
                    </span>
                  )}
                </div>

                {/* tweet text */}
                <p
                  style={{
                    margin: '0 0 10px',
                    fontSize: 15,
                    color: '#0F1419',
                    lineHeight: 1.6,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {tweet}
                </p>

                {/* hashtags */}
                {showTags && (
                  <p style={{ margin: '0 0 10px', fontSize: 14, color: '#1D9BF0' }}>
                    {tags.join(' ')}
                  </p>
                )}

                {/* metaphor hook card — first tweet's main visual */}
                {showMetaphorCover && (
                  <div
                    style={{
                      marginBottom: 10,
                      borderRadius: 16,
                      overflow: 'hidden',
                      border: '1px solid #CFD9DE',
                      maxWidth: 400,
                    }}
                  >
                    <AuthImage
                      src={imageUrl(metaphorCover.filename)}
                      alt={metaphorCover.alt || '관계 메타포'}
                      style={{ display: 'block', width: '100%', aspectRatio: '1/1', objectFit: 'cover' }}
                    />
                  </div>
                )}

                {/* quote card (shown only when no metaphor cover) */}
                {showQuote && (
                  <div
                    style={{
                      marginBottom: 10,
                      borderRadius: 16,
                      overflow: 'hidden',
                      border: '1px solid #CFD9DE',
                      maxWidth: 400,
                    }}
                  >
                    <AuthImage
                      src={imageUrl(quoteCard.filename)}
                      alt={quoteCard.alt || '인용 카드'}
                      style={{ display: 'block', width: '100%', aspectRatio: '1/1', objectFit: 'cover' }}
                    />
                  </div>
                )}

                {/* chat screenshot */}
                {showChat && (
                  <div
                    style={{
                      marginBottom: 10,
                      borderRadius: 16,
                      overflow: 'hidden',
                      border: '1px solid #CFD9DE',
                      maxWidth: 400,
                    }}
                  >
                    <AuthImage
                      src={imageUrl(chatImg.filename)}
                      alt={chatImg.alt || '채팅 스크린샷'}
                      style={{ display: 'block', width: '100%', objectFit: 'cover' }}
                    />
                  </div>
                )}

                {/* action row */}
                <div style={{ display: 'flex', gap: 20, color: '#536471', fontSize: 13 }}>
                  {['답글', '리포스트', '좋아요', '공유'].map((label) => (
                    <span key={label} style={{ cursor: 'default' }}>
                      {label}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        );
      })}

    </div>
  );
}
