'use client';

import { type ContentResponse } from '@/lib/api/marketing/contentApi';
import { XPreview } from './XPreview';
import { InstagramPreview } from './InstagramPreview';
import { NaverBlogPreview } from './NaverBlogPreview';

interface Props {
  content: ContentResponse;
}

export function PlatformPreview({ content }: Props) {
  const platform = content.platform.toLowerCase();

  let preview: React.ReactNode;
  if (platform === 'x') {
    preview = <XPreview content={content} />;
  } else if (platform === 'instagram') {
    preview = <InstagramPreview content={content} />;
  } else if (platform === 'naver_blog') {
    preview = <NaverBlogPreview content={content} />;
  } else {
    return null;
  }

  return (
    <div style={{ marginBottom: 24 }}>
      <label
        style={{
          fontSize: 11,
          color: '#666',
          fontWeight: 600,
          display: 'block',
          marginBottom: 8,
          textTransform: 'uppercase',
          letterSpacing: '0.5px',
        }}
      >
        미리보기
      </label>
      <div
        style={{
          padding: 16,
          border: '1px dashed #CCCCCC',
          borderRadius: 8,
          background: '#FAFAFA',
          overflowX: 'auto',
        }}
      >
        {preview}
      </div>
    </div>
  );
}
