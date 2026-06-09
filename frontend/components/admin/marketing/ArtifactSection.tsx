'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Download, FileText, Image as ImageIcon, Video } from 'lucide-react';

interface Props {
  jobId: number;
  artifacts: Record<string, unknown>;
}

function artifactUrl(jobId: number, name: string) {
  return `/api/admin/marketing/jobs/${jobId}/artifacts/${name}`;
}

function artifactType(key: string): 'video' | 'image' | 'text' | 'other' {
  if (key === 'video_mp4' || key.endsWith('.mp4')) return 'video';
  if (key === 'thumbnail' || key.endsWith('.png') || key.endsWith('.jpg')) return 'image';
  if (key === 'blog_md' || key.endsWith('.md') || key.endsWith('.txt')) return 'text';
  if (key === 'images') return 'image';
  return 'other';
}

function ArtifactPreview({ jobId, name, kind }: { jobId: number; name: string; kind: string }) {
  const [text, setText] = useState<string | null>(null);
  const [loadingText, setLoadingText] = useState(false);
  const url = artifactUrl(jobId, name);

  if (kind === 'video') {
    return (
      <div className="mt-3">
        <video
          controls
          className="max-w-full rounded border"
          style={{ maxHeight: 360 }}
        >
          <source src={url} />
          브라우저가 video를 지원하지 않습니다.
        </video>
      </div>
    );
  }

  if (kind === 'image') {
    return (
      <div className="mt-3">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={url}
          alt={name}
          className="max-w-full rounded border"
          style={{ maxHeight: 400 }}
        />
      </div>
    );
  }

  if (kind === 'text') {
    const handleLoad = async () => {
      setLoadingText(true);
      try {
        const res = await fetch(url);
        const content = await res.text();
        setText(content);
      } catch {
        setText('내용을 불러오지 못했습니다.');
      } finally {
        setLoadingText(false);
      }
    };

    return (
      <div className="mt-3">
        {text === null ? (
          <Button variant="outline" size="sm" onClick={handleLoad} disabled={loadingText}>
            {loadingText ? '로드 중…' : '내용 미리보기'}
          </Button>
        ) : (
          <pre className="mt-2 max-h-80 overflow-auto rounded border bg-gray-50 p-3 text-xs whitespace-pre-wrap">
            {text}
          </pre>
        )}
      </div>
    );
  }

  return null;
}

function ArtifactRow({ jobId, name, value }: { jobId: number; name: string; value: unknown }) {
  const [open, setOpen] = useState(false);

  if (!value || (typeof value !== 'string' && !Array.isArray(value))) return null;

  if (Array.isArray(value)) {
    return (
      <>
        {value.map((v, i) => (
          <ArtifactRow key={i} jobId={jobId} name={`${name}[${i}]`} value={v} />
        ))}
      </>
    );
  }

  const kind = artifactType(name);
  const url = artifactUrl(jobId, name);

  const Icon =
    kind === 'video' ? Video :
    kind === 'image' ? ImageIcon :
    kind === 'text' ? FileText : FileText;

  return (
    <div className="rounded-lg border bg-white p-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Icon className="w-4 h-4 text-gray-500" />
          <span className="font-mono text-sm font-medium">{name}</span>
          <span className="text-xs text-gray-400 capitalize">{kind}</span>
        </div>
        <div className="flex gap-2">
          {(kind === 'video' || kind === 'image' || kind === 'text') && (
            <Button variant="outline" size="sm" onClick={() => setOpen(!open)}>
              {open ? '닫기' : '미리보기'}
            </Button>
          )}
          <a href={url} download={name}>
            <Button variant="outline" size="sm">
              <Download className="w-3 h-3 mr-1" />
              다운로드
            </Button>
          </a>
        </div>
      </div>

      {open && <ArtifactPreview jobId={jobId} name={name} kind={kind} />}
    </div>
  );
}

export function ArtifactSection({ jobId, artifacts }: Props) {
  const entries = Object.entries(artifacts).filter(([, v]) => v != null);
  if (entries.length === 0) return null;

  return (
    <Card className="p-6">
      <h3 className="text-lg font-semibold mb-4">생성된 아티팩트</h3>
      <div className="space-y-3">
        {entries.map(([key, value]) => (
          <ArtifactRow key={key} jobId={jobId} name={key} value={value} />
        ))}
      </div>
    </Card>
  );
}
