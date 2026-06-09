'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Download, FileText, Image as ImageIcon, Video, ChevronDown, ChevronUp } from 'lucide-react';

interface PlatformPackage {
  upload?: string;
  card?: string;
  video?: string;
  thumbnail?: string;
  [key: string]: string | undefined;
}

interface Props {
  jobId: number;
  artifacts: Record<string, unknown>;
}

const PLATFORM_LABELS: Record<string, string> = {
  x: 'X (트위터)',
  naver_blog: '네이버 블로그',
  instagram_feed: '인스타그램 피드',
  instagram_reels: '인스타그램 릴스',
};

const PLATFORM_COLORS: Record<string, string> = {
  x: 'bg-black text-white',
  naver_blog: 'bg-green-600 text-white',
  instagram_feed: 'bg-pink-600 text-white',
  instagram_reels: 'bg-purple-600 text-white',
};

function fileKind(key: string): 'video' | 'image' | 'json' {
  if (key === 'video') return 'video';
  if (key === 'card' || key === 'thumbnail') return 'image';
  return 'json';
}

function fileLabel(key: string): string {
  const labels: Record<string, string> = {
    upload: '업로드 패키지 (JSON)',
    card: '카드 이미지',
    thumbnail: '썸네일',
    video: '영상',
  };
  return labels[key] ?? key;
}

function proxyUrl(jobId: number, artifactKey: string) {
  return `/api/admin/marketing/jobs/${jobId}/artifacts/${artifactKey}`;
}

function UploadJsonPreview({ url, filename }: { url: string; filename: string }) {
  const [content, setContent] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const handleLoad = async () => {
    if (content) { setOpen(!open); return; }
    setLoading(true);
    try {
      const res = await fetch(url);
      const text = await res.text();
      setContent(JSON.parse(text));
      setOpen(true);
    } catch {
      setContent({ error: '내용을 불러오지 못했습니다.' });
      setOpen(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="flex items-center gap-2 mt-2">
        <Button variant="outline" size="sm" onClick={handleLoad} disabled={loading}>
          {loading ? '로드 중…' : open ? <><ChevronUp className="w-3 h-3 mr-1" />닫기</> : <><ChevronDown className="w-3 h-3 mr-1" />내용 보기</>}
        </Button>
        <a href={url} download={filename}>
          <Button variant="outline" size="sm">
            <Download className="w-3 h-3 mr-1" />다운로드
          </Button>
        </a>
      </div>

      {open && content && (
        <div className="mt-3 space-y-3 rounded border bg-gray-50 p-4">
          {typeof content.text === 'string' && content.text && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">본문 텍스트</p>
              <pre className="whitespace-pre-wrap text-sm bg-white rounded border p-3">{content.text}</pre>
            </div>
          )}
          {typeof content.caption === 'string' && content.caption && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">캡션</p>
              <pre className="whitespace-pre-wrap text-sm bg-white rounded border p-3">{content.caption}</pre>
            </div>
          )}
          {typeof content.title === 'string' && content.title && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">제목</p>
              <p className="text-sm font-medium bg-white rounded border p-3">{content.title}</p>
            </div>
          )}
          {typeof content.body_markdown === 'string' && content.body_markdown && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">본문 (Markdown)</p>
              <pre className="whitespace-pre-wrap text-xs bg-white rounded border p-3 max-h-60 overflow-auto">{content.body_markdown}</pre>
            </div>
          )}
          {Array.isArray(content.hashtags) && content.hashtags.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">해시태그</p>
              <div className="flex flex-wrap gap-1">
                {(content.hashtags as string[]).map((tag) => (
                  <Badge key={tag} variant="secondary" className="text-xs">{tag}</Badge>
                ))}
              </div>
            </div>
          )}
          {Array.isArray(content.tags) && content.tags.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-gray-500 mb-1">태그</p>
              <div className="flex flex-wrap gap-1">
                {(content.tags as string[]).map((tag) => (
                  <Badge key={tag} variant="outline" className="text-xs">{tag}</Badge>
                ))}
              </div>
            </div>
          )}
          {typeof content.note === 'string' && content.note && (
            <p className="text-xs text-amber-600 italic">{content.note}</p>
          )}
        </div>
      )}
    </div>
  );
}

function MediaFile({ url, kind, label, filename }: { url: string; kind: 'image' | 'video'; label: string; filename: string }) {
  const [open, setOpen] = useState(false);
  const Icon = kind === 'video' ? Video : ImageIcon;

  return (
    <div>
      <div className="flex items-center gap-2 mt-2">
        <Icon className="w-4 h-4 text-gray-400" />
        <span className="text-xs text-gray-600">{label}</span>
        <Button variant="outline" size="sm" onClick={() => setOpen(!open)}>
          {open ? '닫기' : '미리보기'}
        </Button>
        <a href={url} download={filename}>
          <Button variant="outline" size="sm">
            <Download className="w-3 h-3 mr-1" />다운로드
          </Button>
        </a>
      </div>
      {open && kind === 'image' && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={label} className="mt-2 max-w-xs rounded border" />
      )}
      {open && kind === 'video' && (
        <video controls className="mt-2 max-w-xs rounded border">
          <source src={url} />
        </video>
      )}
    </div>
  );
}

function PlatformCard({ platform, pkg, jobId }: { platform: string; pkg: PlatformPackage; jobId: number }) {
  const label = PLATFORM_LABELS[platform] ?? platform;
  const colorClass = PLATFORM_COLORS[platform] ?? 'bg-gray-600 text-white';

  return (
    <div className="rounded-lg border bg-white p-5">
      <div className="flex items-center gap-2 mb-4">
        <span className={`px-3 py-1 rounded-full text-sm font-semibold ${colorClass}`}>{label}</span>
      </div>

      <div className="space-y-3">
        {Object.entries(pkg).map(([key, artifactKey]) => {
          if (!artifactKey) return null;
          const url = proxyUrl(jobId, artifactKey);
          const kind = fileKind(key);

          if (kind === 'json') {
            return (
              <div key={key}>
                <div className="flex items-center gap-2">
                  <FileText className="w-4 h-4 text-gray-400" />
                  <span className="text-sm font-medium text-gray-700">{fileLabel(key)}</span>
                </div>
                <UploadJsonPreview url={url} filename={`${platform}_upload.json`} />
              </div>
            );
          }

          return (
            <MediaFile
              key={key}
              url={url}
              kind={kind}
              label={fileLabel(key)}
              filename={`${platform}_${key}.${key === 'video' ? 'mp4' : 'png'}`}
            />
          );
        })}
      </div>
    </div>
  );
}

export function ArtifactSection({ jobId, artifacts }: Props) {
  // Detect per-platform structure (values are objects) vs old flat structure
  const entries = Object.entries(artifacts).filter(([, v]) => v != null && typeof v === 'object');

  if (entries.length === 0) {
    // Old flat structure fallback — just show raw download links
    const flat = Object.entries(artifacts).filter(([, v]) => typeof v === 'string');
    if (flat.length === 0) return null;
    return (
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4">생성된 아티팩트</h3>
        <div className="space-y-2">
          {flat.map(([key, val]) => (
            <div key={key} className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-gray-400" />
              <span className="font-mono text-sm">{key}</span>
              <a href={proxyUrl(jobId, String(val))} download={key}>
                <Button variant="outline" size="sm"><Download className="w-3 h-3 mr-1" />다운로드</Button>
              </a>
            </div>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <Card className="p-6">
      <h3 className="text-lg font-semibold mb-4">플랫폼별 업로드 패키지</h3>
      <div className="space-y-4">
        {entries.map(([platform, pkg]) => (
          <PlatformCard
            key={platform}
            platform={platform}
            pkg={pkg as PlatformPackage}
            jobId={jobId}
          />
        ))}
      </div>
    </Card>
  );
}
