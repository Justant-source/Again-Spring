'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api/client';
import { uploadJobThumbnail } from '@/lib/api/admin/marketing';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Download, FileText, Image as ImageIcon, Video, ChevronDown, ChevronUp, Upload } from 'lucide-react';

interface PlatformPackage {
  upload?: string;
  card?: string;
  video?: string;
  thumbnail?: string;
  customcover?: string;
  [key: string]: string | undefined;
}

interface Props {
  jobId: number;
  artifacts: Record<string, unknown>;
  /** Called after a custom thumbnail upload succeeds, so the parent can refetch the job. */
  onArtifactsChanged?: () => void;
}

const THUMBNAIL_PLATFORMS = new Set(['youtube_shorts', 'instagram_reels']);
const MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024; // matches YouTube thumbnails.set hard cap

const PLATFORM_LABELS: Record<string, string> = {
  x_thread: 'X 4단 스레드',
  naver_blog: '네이버 블로그',
  instagram_feed: '인스타그램 피드',
  instagram_reels: '인스타그램 릴스',
  threads: '스레드',
  youtube_shorts: '유튜브 쇼츠',
  naver_clip: '네이버 클립',
};

const PLATFORM_COLORS: Record<string, string> = {
  x_thread: 'bg-black text-white',
  naver_blog: 'bg-green-600 text-white',
  instagram_feed: 'bg-pink-600 text-white',
  instagram_reels: 'bg-purple-600 text-white',
  threads: 'bg-gray-800 text-white',
  youtube_shorts: 'bg-red-600 text-white',
  naver_clip: 'bg-green-700 text-white',
};

function fileKind(key: string): 'video' | 'image' | 'json' {
  if (key === 'video' || key.startsWith('video')) return 'video';
  if (
    key === 'card' || key === 'thumbnail' || key === 'customcover' ||
    key.startsWith('card_') || key.startsWith('img_')
  ) return 'image';
  return 'json';
}

function fileLabel(key: string): string {
  const labels: Record<string, string> = {
    upload: '업로드 패키지 (JSON)',
    card: '카드 이미지',
    thumbnail: '썸네일 (자동)',
    customcover: '커스텀 썸네일',
    video: '영상',
  };
  return labels[key] ?? key;
}

function proxyUrl(jobId: number, artifactKey: string) {
  // artifactKey may be a full ASM path like /api/v1/jobs/{id}/artifacts/platform__file.ext
  const name = artifactKey.split('/').pop() ?? artifactKey;
  return `/api/admin/marketing/jobs/${jobId}/artifacts/${name}`;
}

// 아티팩트 프록시는 ADMIN JWT 필수 — <img src>/<a href>는 Authorization 헤더를 못 붙이므로
// 인증된 axios 클라이언트로 blob을 받아 object URL로 표시/다운로드한다.
async function fetchArtifactBlobUrl(url: string): Promise<string> {
  const res = await api.get<Blob>(url, { responseType: 'blob' });
  return URL.createObjectURL(res.data);
}

async function downloadArtifact(url: string, filename: string) {
  const objectUrl = await fetchArtifactBlobUrl(url);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}

function DownloadButton({ url, filename }: { url: string; filename: string }) {
  const [busy, setBusy] = useState(false);
  const handleClick = async () => {
    setBusy(true);
    try {
      await downloadArtifact(url, filename);
    } catch {
      alert('다운로드에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };
  return (
    <Button variant="outline" size="sm" onClick={handleClick} disabled={busy}>
      <Download className="w-3 h-3 mr-1" />{busy ? '다운로드 중…' : '다운로드'}
    </Button>
  );
}

function UploadJsonPreview({ url, filename }: { url: string; filename: string }) {
  const [content, setContent] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const handleLoad = async () => {
    if (content) { setOpen(!open); return; }
    setLoading(true);
    try {
      const res = await api.get(url);
      setContent(res.data);
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
        <DownloadButton url={url} filename={filename} />
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

function MediaFile({ url, kind, label, filename, tallVideo = false }: {
  url: string; kind: 'image' | 'video'; label: string; filename: string; tallVideo?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const Icon = kind === 'video' ? Video : ImageIcon;

  useEffect(() => {
    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [blobUrl]);

  const handleToggle = async () => {
    if (open) { setOpen(false); return; }
    if (blobUrl) { setOpen(true); return; }
    setLoading(true);
    setLoadError(false);
    try {
      setBlobUrl(await fetchArtifactBlobUrl(url));
      setOpen(true);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="flex items-center gap-2 mt-2">
        <Icon className="w-4 h-4 text-gray-400" />
        <span className="text-xs text-gray-600">{label}</span>
        <Button variant="outline" size="sm" onClick={handleToggle} disabled={loading}>
          {loading ? '로드 중…' : open ? '닫기' : '미리보기'}
        </Button>
        <DownloadButton url={url} filename={filename} />
      </div>
      {loadError && (
        <p className="mt-1 text-xs text-red-600">미리보기를 불러오지 못했습니다.</p>
      )}
      {open && blobUrl && kind === 'image' && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={blobUrl} alt={label} className="mt-2 max-w-xs rounded border" />
      )}
      {open && blobUrl && kind === 'video' && (
        <video
          controls
          className={
            tallVideo
              ? 'mt-2 w-full max-w-[240px] aspect-[9/16] rounded border object-cover'
              : 'mt-2 max-w-xs rounded border'
          }
        >
          <source src={blobUrl} />
        </video>
      )}
    </div>
  );
}

function CarouselPreview({ pkg, jobId, uploadData }: {
  pkg: PlatformPackage; jobId: number; uploadData?: Record<string, unknown>;
}) {
  const cardKeys = Object.keys(pkg).filter(k => k.startsWith('card_')).sort();
  const [activeIdx, setActiveIdx] = useState(0);
  const [blobUrls, setBlobUrls] = useState<Record<string, string>>({});

  useEffect(() => {
    cardKeys.forEach(key => {
      const url = proxyUrl(jobId, pkg[key]!);
      fetchArtifactBlobUrl(url)
        .then(blobUrl => setBlobUrls(prev => ({ ...prev, [key]: blobUrl })))
        .catch(() => {});
    });
    return () => {
      Object.values(blobUrls).forEach(u => URL.revokeObjectURL(u));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (cardKeys.length === 0) return null;
  const caption = typeof uploadData?.caption === 'string' ? uploadData.caption : '';
  const hashtags = Array.isArray(uploadData?.hashtags) ? uploadData.hashtags as string[] : [];

  return (
    <div data-testid="artifact-carousel">
      <div className="flex gap-2 overflow-x-auto pb-2 mt-2">
        {cardKeys.map((key, i) => (
          <button
            key={key}
            onClick={() => setActiveIdx(i)}
            className={`relative flex-shrink-0 w-28 h-36 rounded border-2 overflow-hidden bg-gray-100 transition-all ${
              activeIdx === i ? 'border-pink-500 ring-2 ring-pink-200' : 'border-gray-200'
            }`}
          >
            {blobUrls[key] ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={blobUrls[key]} alt={`card ${i + 1}`} className="w-full h-full object-cover" />
            ) : (
              <div className="flex items-center justify-center h-full text-xs text-gray-400">
                {i + 1}/{cardKeys.length}
              </div>
            )}
            <div className="absolute bottom-1 right-1 bg-black/60 text-white text-xs px-1 rounded">
              {i + 1}/{cardKeys.length}
            </div>
          </button>
        ))}
      </div>

      {blobUrls[cardKeys[activeIdx]] && (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={blobUrls[cardKeys[activeIdx]]}
          alt={`card ${activeIdx + 1}`}
          className="mt-3 max-w-xs rounded border shadow"
        />
      )}

      {caption && (
        <div className="mt-3">
          <p className="text-xs font-semibold text-gray-500 mb-1">캡션 (첫 125자)</p>
          <pre className="whitespace-pre-wrap text-sm bg-gray-50 rounded border p-2 max-h-20 overflow-auto">
            {caption.slice(0, 125)}
          </pre>
        </div>
      )}

      {hashtags.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {hashtags.map(tag => (
            <Badge key={tag} variant="secondary" className="text-xs">{tag}</Badge>
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-2 mt-3">
        {cardKeys.map((key, i) => (
          <DownloadButton
            key={key}
            url={proxyUrl(jobId, pkg[key]!)}
            filename={`instagram_card_${String(i + 1).padStart(2, '0')}.png`}
          />
        ))}
      </div>
    </div>
  );
}

function XTweetMockup({ pkg, jobId, uploadData }: {
  pkg: PlatformPackage; jobId: number; uploadData?: Record<string, unknown>;
}) {
  const cardKeys = Object.keys(pkg).filter(k => k.startsWith('card_')).sort().slice(0, 2);
  const [blobUrls, setBlobUrls] = useState<Record<string, string>>({});

  useEffect(() => {
    cardKeys.forEach(key => {
      const url = proxyUrl(jobId, pkg[key]!);
      fetchArtifactBlobUrl(url)
        .then(blobUrl => setBlobUrls(prev => ({ ...prev, [key]: blobUrl })))
        .catch(() => {});
    });
    return () => {
      Object.values(blobUrls).forEach(u => URL.revokeObjectURL(u));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const text = typeof uploadData?.text === 'string' ? uploadData.text : '';
  const hashtags = Array.isArray(uploadData?.hashtags) ? uploadData.hashtags as string[] : [];
  const charCount = text.length + hashtags.join(' ').length + (hashtags.length > 0 ? 1 : 0);

  return (
    <div data-testid="artifact-x-mockup" className="mt-2 rounded-xl border border-gray-200 p-4 bg-white max-w-md shadow-sm">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-9 h-9 rounded-full bg-gray-200 flex items-center justify-center text-sm font-bold">다</div>
        <div>
          <div className="font-bold text-sm">다시봄</div>
          <div className="text-xs text-gray-500">@againspring</div>
        </div>
      </div>
      {text && (
        <p className="text-sm whitespace-pre-wrap mb-3">
          {text}
          {hashtags.length > 0 && (
            <span className="text-blue-500"> {hashtags.join(' ')}</span>
          )}
        </p>
      )}
      {cardKeys.length > 0 && (
        <div className={`grid gap-1 mb-2 ${cardKeys.length >= 2 ? 'grid-cols-2' : 'grid-cols-1'}`}>
          {cardKeys.map(key => (
            <div key={key} className="aspect-video bg-gray-100 rounded-lg overflow-hidden">
              {blobUrls[key] ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={blobUrls[key]} alt="card" className="w-full h-full object-cover" />
              ) : (
                <div className="flex items-center justify-center h-full text-xs text-gray-400">로딩 중…</div>
              )}
            </div>
          ))}
        </div>
      )}
      <div className="text-xs text-gray-400 mt-2">{charCount}/280자</div>
      <div className="flex gap-2 mt-2">
        {cardKeys.map((key, i) => (
          <DownloadButton key={key} url={proxyUrl(jobId, pkg[key]!)} filename={`x_card_${i + 1}.png`} />
        ))}
      </div>
    </div>
  );
}

type BlogSection = { type: string; text: string; position: number };

function BlogPreview({ pkg, jobId, uploadData }: {
  pkg: PlatformPackage; jobId: number; uploadData?: Record<string, unknown>;
}) {
  const imgKeys = Object.keys(pkg).filter(k => k.startsWith('img_')).sort();
  const [imgBlobUrls, setImgBlobUrls] = useState<Record<string, string>>({});

  useEffect(() => {
    imgKeys.forEach(key => {
      const url = proxyUrl(jobId, pkg[key]!);
      fetchArtifactBlobUrl(url)
        .then(blobUrl => setImgBlobUrls(prev => ({ ...prev, [key]: blobUrl })))
        .catch(() => {});
    });
    return () => {
      Object.values(imgBlobUrls).forEach(u => URL.revokeObjectURL(u));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sections = Array.isArray(uploadData?.sections)
    ? uploadData.sections as BlogSection[]
    : [];
  const bodyMarkdown = typeof uploadData?.body_markdown === 'string' ? uploadData.body_markdown : '';
  const title = typeof uploadData?.title === 'string' ? uploadData.title : '';
  const tags = Array.isArray(uploadData?.tags) ? uploadData.tags as string[] : [];
  const charCount = bodyMarkdown.length;
  const imgKeyList = imgKeys;

  return (
    <div data-testid="artifact-blog-preview" className="mt-2 max-w-lg">
      {title && (
        <h2 className="text-lg font-bold mb-3 text-gray-800">{title}</h2>
      )}

      {sections.length > 0 ? (
        <div className="space-y-3 text-sm">
          {sections.map((section, i) => {
            if (section.type === 'heading') {
              return <h3 key={i} className="text-base font-bold mt-4 text-gray-700">{section.text}</h3>;
            }
            if (section.type === 'paragraph') {
              return <p key={i} className="text-gray-700 leading-relaxed whitespace-pre-wrap">{section.text}</p>;
            }
            if (section.type === 'image_marker') {
              const markerNum = sections.slice(0, i).filter(s => s.type === 'image_marker').length;
              const imgKey = imgKeyList[markerNum];
              return (
                <div key={i} className="my-3 rounded-lg border border-dashed border-amber-300 bg-amber-50 p-3">
                  <div className="flex items-center gap-2 mb-2">
                    <Badge variant="outline" className="text-amber-600 border-amber-400 text-xs">수동 첨부 필요</Badge>
                    <span className="text-xs text-amber-700">{section.text}</span>
                  </div>
                  {imgKey && imgBlobUrls[imgKey] ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={imgBlobUrls[imgKey]} alt={`image ${markerNum + 1}`} className="max-w-xs rounded border" />
                  ) : imgKey ? (
                    <div className="text-xs text-gray-400">이미지 로딩 중…</div>
                  ) : null}
                  {imgKey && (
                    <div className="mt-2">
                      <DownloadButton
                        url={proxyUrl(jobId, pkg[imgKey]!)}
                        filename={`${imgKey}.png`}
                      />
                    </div>
                  )}
                </div>
              );
            }
            return null;
          })}
        </div>
      ) : bodyMarkdown ? (
        <pre className="whitespace-pre-wrap text-sm bg-gray-50 rounded border p-3 max-h-80 overflow-auto">
          {bodyMarkdown}
        </pre>
      ) : null}

      <div className="mt-3 text-xs text-gray-400">{charCount}자</div>

      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {tags.map(tag => (
            <Badge key={tag} variant="outline" className="text-xs">{tag}</Badge>
          ))}
        </div>
      )}
    </div>
  );
}

function ThumbnailUploader({
  jobId, platform, onUploaded,
}: { jobId: number; platform: string; onUploaded?: () => void }) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      setError('PNG 또는 JPEG 파일만 업로드할 수 있습니다.');
      return;
    }
    if (file.size > MAX_THUMBNAIL_BYTES) {
      setError('파일 크기는 2MB 이하여야 합니다.');
      return;
    }
    setUploading(true);
    try {
      await uploadJobThumbnail(jobId, platform, file);
      onUploaded?.();
    } catch {
      setError('썸네일 업로드에 실패했습니다.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="mt-1">
      <label className="inline-flex items-center gap-2 text-xs text-gray-600 cursor-pointer">
        <Upload className="w-4 h-4 text-gray-400" />
        <span>{uploading ? '업로드 중…' : '커스텀 썸네일 업로드 (PNG/JPEG, ≤2MB)'}</span>
        <input
          type="file"
          accept="image/png,image/jpeg"
          className="hidden"
          disabled={uploading}
          onChange={(e) => {
            void handleFile(e.target.files?.[0]);
            e.target.value = '';
          }}
        />
      </label>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}

function PlatformCard({
  platform, pkg, jobId, onArtifactsChanged,
}: { platform: string; pkg: PlatformPackage; jobId: number; onArtifactsChanged?: () => void }) {
  const label = PLATFORM_LABELS[platform] ?? platform;
  const colorClass = PLATFORM_COLORS[platform] ?? 'bg-gray-600 text-white';
  const [uploadData, setUploadData] = useState<Record<string, unknown> | undefined>(undefined);

  useEffect(() => {
    const uploadKey = Object.keys(pkg).find(k => k === 'upload' || k.endsWith('upload'));
    if (!uploadKey || !pkg[uploadKey]) return;
    const url = proxyUrl(jobId, pkg[uploadKey]!);
    api.get(url).then(res => setUploadData(res.data as Record<string, unknown>)).catch(() => {});
  }, [pkg, jobId]);

  const hasCards = Object.keys(pkg).some(k => k.startsWith('card_'));
  const hasImgs = Object.keys(pkg).some(k => k.startsWith('img_'));

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
          // Individual card images and img_ images: skip in generic list (shown in platform preview)
          if (key.startsWith('card_') || key.startsWith('img_')) return null;

          return (
            <MediaFile
              key={key}
              url={url}
              kind={kind}
              label={fileLabel(key)}
              filename={`${platform}_${key}.${kind === 'video' ? 'mp4' : 'png'}`}
              tallVideo={platform === 'youtube_shorts' && kind === 'video'}
            />
          );
        })}
        {THUMBNAIL_PLATFORMS.has(platform) && (
          <ThumbnailUploader jobId={jobId} platform={platform} onUploaded={onArtifactsChanged} />
        )}
      </div>

      {/* Platform-specific rich preview below generic items */}
      {platform === 'instagram_feed' && hasCards && (
        <CarouselPreview pkg={pkg} jobId={jobId} uploadData={uploadData} />
      )}
      {(platform === 'x_thread' || platform === 'x') && hasCards && (
        <XTweetMockup pkg={pkg} jobId={jobId} uploadData={uploadData} />
      )}
      {platform === 'naver_blog' && (hasImgs || (uploadData?.sections !== undefined)) && (
        <BlogPreview pkg={pkg} jobId={jobId} uploadData={uploadData} />
      )}
    </div>
  );
}

export function ArtifactSection({ jobId, artifacts, onArtifactsChanged }: Props) {
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
              <DownloadButton url={proxyUrl(jobId, String(val))} filename={key} />
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
            onArtifactsChanged={onArtifactsChanged}
          />
        ))}
      </div>
    </Card>
  );
}
