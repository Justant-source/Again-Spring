import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ArtifactSection } from '@/components/admin/marketing/ArtifactSection';

vi.mock('@/lib/api/client', () => ({
  api: {
    get: vi.fn(),
  },
}));

const { api } = await import('@/lib/api/client');

const makeArtifacts = (platform: string, extras: Record<string, string> = {}) => ({
  [platform]: {
    upload: `/api/v1/jobs/1/artifacts/${platform}__upload.json`,
    ...extras,
  },
});

describe('ArtifactSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: upload.json returns empty object
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} });
  });

  it('renders nothing when artifacts is empty', () => {
    const { container } = render(<ArtifactSection jobId={1} artifacts={{}} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders platform badge for instagram_feed', async () => {
    const artifacts = makeArtifacts('instagram_feed', {
      card_01: '/api/v1/jobs/1/artifacts/instagram_feed__card_01.png',
      card_02: '/api/v1/jobs/1/artifacts/instagram_feed__card_02.png',
    });
    render(<ArtifactSection jobId={1} artifacts={artifacts} />);
    expect(screen.getByText('인스타그램 피드')).toBeInTheDocument();
  });

  it('renders carousel testid when instagram_feed has card_ keys', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { caption: '테스트 캡션', hashtags: ['#다시봄'] },
    });
    // Mock fetchArtifactBlobUrl
    global.URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    const artifacts = makeArtifacts('instagram_feed', {
      card_01: '/api/v1/jobs/1/artifacts/instagram_feed__card_01.png',
      card_02: '/api/v1/jobs/1/artifacts/instagram_feed__card_02.png',
    });
    render(<ArtifactSection jobId={1} artifacts={artifacts} />);
    await waitFor(() => {
      expect(screen.getByTestId('artifact-carousel')).toBeInTheDocument();
    });
  });

  it('renders x tweet mockup when x has card_ keys', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { text: '테스트 트윗', hashtags: ['#다시봄'] },
    });
    global.URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    const artifacts = makeArtifacts('x', {
      card_01: '/api/v1/jobs/1/artifacts/x__card_01.png',
      card_02: '/api/v1/jobs/1/artifacts/x__card_02.png',
    });
    render(<ArtifactSection jobId={1} artifacts={artifacts} />);
    await waitFor(() => {
      expect(screen.getByTestId('artifact-x-mockup')).toBeInTheDocument();
    });
  });

  it('renders blog preview for naver_blog with sections', async () => {
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        title: '블로그 제목',
        sections: [
          { type: 'heading', text: '소제목', position: 1 },
          { type: 'paragraph', text: '본문 내용', position: 2 },
          { type: 'image_marker', text: '[이미지 1]', position: 3 },
        ],
        body_markdown: '## 소제목\n\n본문 내용',
        tags: ['다시봄'],
      },
    });
    global.URL.createObjectURL = vi.fn().mockReturnValue('blob:test');
    const artifacts = makeArtifacts('naver_blog', {
      img_01: '/api/v1/jobs/1/artifacts/naver_blog__img_01.png',
    });
    render(<ArtifactSection jobId={1} artifacts={artifacts} />);
    await waitFor(() => {
      expect(screen.getByTestId('artifact-blog-preview')).toBeInTheDocument();
    });
    expect(screen.getByText('수동 첨부 필요')).toBeInTheDocument();
  });

  it('shows generic fallback for unknown platform', () => {
    const artifacts = makeArtifacts('tiktok');
    render(<ArtifactSection jobId={1} artifacts={artifacts} />);
    expect(screen.getByText('tiktok')).toBeInTheDocument();
  });
});
