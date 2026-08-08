'use client';

import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import type { MarketingHoldingDraft } from '@/lib/api/admin/marketing';

/** Flattened skeleton fields for the holding draft editor. */
export interface HoldingDraftFormValues {
  title: string;
  promoTitle: string;
  /** Comma-separated tags, or JSON array string. */
  tags: string;
  /** JSON textarea for topComments[]. */
  topCommentsJson: string;
}

export interface HoldingDraftDialogProps {
  open: boolean;
  postId: string | null;
  /** Initial draft from holding row (or null for empty). */
  draft?: MarketingHoldingDraft | null;
  /** When true (committed / locked), inputs are read-only. */
  readOnly?: boolean;
  saving?: boolean;
  error?: string | null;
  onClose: () => void;
  /**
   * Called with flattened form values + parsed draft patch.
   * Parent / S6 wires `updateMarketingHoldingDraft`.
   * If omitted, Save is a no-op (skeleton).
   */
  onSave?: (payload: {
    postId: string;
    form: HoldingDraftFormValues;
    draft: MarketingHoldingDraft;
  }) => void | Promise<void>;
}

function tagsToForm(tags: string[] | null | undefined): string {
  if (!tags || tags.length === 0) return '';
  return tags.join(', ');
}

function parseTags(raw: string): string[] {
  const trimmed = raw.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith('[')) {
    const parsed = JSON.parse(trimmed) as unknown;
    if (!Array.isArray(parsed)) throw new Error('tags JSON must be an array');
    return parsed.map((t) => String(t));
  }
  return trimmed
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
}

function topCommentsToForm(
  comments: MarketingHoldingDraft['topComments']
): string {
  if (!comments || comments.length === 0) return '[]';
  return JSON.stringify(comments, null, 2);
}

function draftToForm(draft: MarketingHoldingDraft | null | undefined): HoldingDraftFormValues {
  return {
    title: draft?.title ?? '',
    promoTitle: draft?.promoTitle ?? '',
    tags: tagsToForm(draft?.tags),
    topCommentsJson: topCommentsToForm(draft?.topComments),
  };
}

function formToDraft(
  form: HoldingDraftFormValues,
  base?: MarketingHoldingDraft | null
): MarketingHoldingDraft {
  const tags = parseTags(form.tags);
  let topComments: MarketingHoldingDraft['topComments'] = [];
  const raw = form.topCommentsJson.trim() || '[]';
  const parsed = JSON.parse(raw) as unknown;
  if (!Array.isArray(parsed)) {
    throw new Error('topComments must be a JSON array');
  }
  topComments = parsed as NonNullable<MarketingHoldingDraft['topComments']>;

  return {
    ...(base ?? {}),
    title: form.title,
    promoTitle: form.promoTitle,
    tags,
    topComments,
  };
}

export function HoldingDraftDialog({
  open,
  postId,
  draft,
  readOnly = false,
  saving = false,
  error = null,
  onClose,
  onSave,
}: HoldingDraftDialogProps) {
  const [form, setForm] = useState<HoldingDraftFormValues>(() => draftToForm(draft));
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setForm(draftToForm(draft));
    setLocalError(null);
  }, [open, postId, draft]);

  const displayError = localError || error;
  const canSave = !!postId && !readOnly && typeof onSave === 'function';

  const handleSave = async () => {
    if (!postId || !onSave || readOnly) return;
    setLocalError(null);
    try {
      const nextDraft = formToDraft(form, draft);
      await onSave({ postId, form, draft: nextDraft });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setLocalError(msg.startsWith('tags') || msg.includes('JSON') || msg.includes('topComments')
        ? `입력 형식 오류: ${msg}`
        : msg);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        className="sm:max-w-lg max-h-[90vh] overflow-y-auto"
        data-testid="marketing-holding-draft-dialog"
      >
        <DialogHeader>
          <DialogTitle>마케팅 초안 편집</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-1">
          {postId && (
            <p className="text-xs font-mono text-gray-500">{postId}</p>
          )}
          {readOnly && (
            <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
              잠금·확정된 초안은 조회만 가능합니다. 광장 원본은 변경되지 않습니다.
            </div>
          )}
          <p className="text-xs text-gray-500">
            플랫폼 탭 없음 — 공통 파라미터만 편집합니다. 저장 시 draft만 갱신됩니다.
          </p>

          <div className="space-y-1">
            <Label htmlFor="holding-draft-title">title</Label>
            <Input
              id="holding-draft-title"
              value={form.title}
              disabled={readOnly || saving}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              data-testid="holding-draft-title"
            />
            <p className="text-xs text-gray-400">릴스·쇼츠·피드 공통</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="holding-draft-promoTitle">promoTitle</Label>
            <Input
              id="holding-draft-promoTitle"
              value={form.promoTitle}
              disabled={readOnly || saving}
              onChange={(e) => setForm((f) => ({ ...f, promoTitle: e.target.value }))}
              data-testid="holding-draft-promo-title"
            />
            <p className="text-xs text-gray-400">IG 훅 제목 · 릴스 미적용 가능</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="holding-draft-tags">tags</Label>
            <Input
              id="holding-draft-tags"
              value={form.tags}
              disabled={readOnly || saving}
              onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))}
              placeholder="comma,separated 또는 JSON 배열"
              data-testid="holding-draft-tags"
            />
            <p className="text-xs text-gray-400">쉼표 구분 또는 JSON 배열</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="holding-draft-topComments">topComments (JSON)</Label>
            <Textarea
              id="holding-draft-topComments"
              value={form.topCommentsJson}
              disabled={readOnly || saving}
              onChange={(e) =>
                setForm((f) => ({ ...f, topCommentsJson: e.target.value }))
              }
              rows={8}
              className="font-mono text-xs"
              data-testid="holding-draft-top-comments"
            />
            <p className="text-xs text-gray-400">최대 3 · author/body/side 객체 배열</p>
          </div>

          {displayError && (
            <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {displayError}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={saving}>
            닫기
          </Button>
          <Button
            type="button"
            onClick={handleSave}
            disabled={!canSave || saving}
            data-testid="holding-draft-save"
          >
            {saving ? '저장 중…' : '저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
