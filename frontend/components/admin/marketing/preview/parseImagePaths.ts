export type ImageMeta = {
  filename: string;
  role: string;
  slot: string;
  alt: string;
  order: number;
};

export function parseImagePaths(raw?: string): ImageMeta[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item: unknown): ImageMeta =>
        typeof item === 'string'
          ? { filename: item, role: '', slot: '', alt: '이미지', order: 0 }
          : (item as ImageMeta)
      )
      .sort((a, b) => a.order - b.order);
  } catch {
    return [];
  }
}

export function imageUrl(filename: string, role?: string): string {
  if (role === 'METAPHOR') return `/illustrations/metaphors/${filename}`;
  return `/api/admin/marketing/images/${filename}`;
}

export function normalizeHashtags(raw?: string[]): string[] {
  return (raw ?? []).flatMap((s) => s.trim().split(/\s+/)).filter(Boolean);
}
