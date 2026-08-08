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
import { Badge } from '@/components/ui/badge';
import type { MarketingHoldingDraft } from '@/lib/api/admin/marketing';
import { getAdminPost, type AdminPost } from '@/lib/api/admin/content';
import { AUTHOR, PARTNER } from '@/lib/constants/factionColors';

/** Flattened editable fields for the holding draft editor. title/promoTitle/body are read-only, sourced live. */
export interface HoldingDraftFormValues {
  tags: string[];
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

/** Accepts comma-separated text or a JSON array string; always returns a flat string[]. */
function parseTags(raw: string): string[] {
  const trimmed = raw.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith('[')) {
    const parsed = JSON.parse(trimmed) as unknown;
    if (!Array.isArray(parsed)) throw new Error('tags JSON must be an array');
    return parsed.map((t) => String(t).trim()).filter(Boolean);
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
    tags: draft?.tags ? [...draft.tags] : [],
    topCommentsJson: topCommentsToForm(draft?.topComments),
  };
}

function formToDraft(
  form: HoldingDraftFormValues,
  base?: MarketingHoldingDraft | null
): MarketingHoldingDraft {
  const raw = form.topCommentsJson.trim() || '[]';
  const parsed = JSON.parse(raw) as unknown;
  if (!Array.isArray(parsed)) {
    throw new Error('topComments must be a JSON array');
  }
  const topComments = parsed as NonNullable<MarketingHoldingDraft['topComments']>;

  // Spread base first so title/promoTitle/authorBody/partnerBody (read-only in this
  // dialog) are preserved untouched — only tags/topComments are ever overwritten here.
  return {
    ...(base ?? {}),
    tags: form.tags,
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
  const [tagInput, setTagInput] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const [livePost, setLivePost] = useState<AdminPost | null>(null);
  const [postLoading, setPostLoading] = useState(false);
  const [postError, setPostError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setForm(draftToForm(draft));
    setTagInput('');
    setLocalError(null);
  }, [open, postId, draft]);

  useEffect(() => {
    if (!open || !postId) {
      setLivePost(null);
      setPostError(null);
      setPostLoading(false);
      return;
    }
    let cancelled = false;
    setPostLoading(true);
    setPostError(null);
    getAdminPost(postId)
      .then((post) => {
        if (!cancelled) setLivePost(post);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLivePost(null);
          setPostError(err instanceof Error ? err.message : String(err));
        }
      })
      .finally(() => {
        if (!cancelled) setPostLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, postId]);

  const displayError = localError || error;
  const canSave = !!postId && !readOnly && typeof onSave === 'function';

  const addTagsFromInput = () => {
    if (!tagInput.trim()) return;
    try {
      const parsed = parseTags(tagInput);
      if (parsed.length === 0) return;
      setForm((f) => ({ ...f, tags: Array.from(new Set([...f.tags, ...parsed])) }));
      setTagInput('');
      setLocalError(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setLocalError(`태그 입력 형식 오류: ${msg}`);
    }
  };

  const removeTag = (tag: string) => {
    setForm((f) => ({ ...f, tags: f.tags.filter((t) => t !== tag) }));
  };

  const handleSave = async () => {
    if (!postId || !onSave || readOnly) return;
    setLocalError(null);
    try {
      const nextDraft = formToDraft(form, draft);
      await onSave({ postId, form, draft: nextDraft });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setLocalError(
        msg.includes('JSON') || msg.includes('topComments')
          ? `입력 형식 오류: ${msg}`
          : msg
      );
    }
  };

  const displayTitle = livePost?.title || livePost?.userTitle || '';
  const authorBody = livePost?.bodyPublished || livePost?.bodyRaw || '';
  const partnerBody = livePost?.partnerBodyPublished || livePost?.partnerBodyRaw || '';

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

          <div
            className="space-y-2 rounded border border-gray-200 bg-gray-50 px-3 py-2"
            data-testid="holding-draft-live-post"
          >
            <p className="text-xs font-medium text-gray-500">
              원본 사연 (광장 실시간 · 읽기 전용)
            </p>
            {postLoading && (
              <p className="text-sm text-gray-400" data-testid="holding-draft-live-post-loading">
                불러오는 중…
              </p>
            )}
            {postError && (
              <p className="text-sm text-red-600" data-testid="holding-draft-live-post-error">
                원본을 불러오지 못했습니다: {postError}
              </p>
            )}
            {!postLoading && !postError && livePost && (
              <>
                <p className="text-sm font-semibold" data-testid="holding-draft-live-title">
                  {displayTitle || '(제목 없음)'}
                </p>
                <div className="space-y-1">
                  <span
                    className="text-xs font-semibold"
                    style={{ color: AUTHOR }}
                  >
                    작성자
                  </span>
                  <p
                    className="whitespace-pre-wrap text-sm text-gray-700"
                    data-testid="holding-draft-live-author-body"
                  >
                    {authorBody || '(본문 없음)'}
                  </p>
                </div>
                {partnerBody && (
                  <div className="space-y-1">
                    <span
                      className="text-xs font-semibold"
                      style={{ color: PARTNER }}
                    >
                      상대방
                    </span>
                    <p
                      className="whitespace-pre-wrap text-sm text-gray-700"
                      data-testid="holding-draft-live-partner-body"
                    >
                      {partnerBody}
                    </p>
                  </div>
                )}
              </>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="holding-draft-tags-input">tags</Label>
            <div
              className="flex flex-wrap gap-1.5"
              data-testid="holding-draft-tags-list"
            >
              {form.tags.length === 0 && (
                <span className="text-xs text-gray-400">태그 없음</span>
              )}
              {form.tags.map((tag, idx) => (
                <Badge
                  key={`${tag}-${idx}`}
                  variant="secondary"
                  className="gap-1 py-1 pl-2.5 pr-1.5"
                  data-testid={`holding-draft-tag-${idx}`}
                >
                  {tag}
                  {!readOnly && (
                    <button
                      type="button"
                      aria-label={`${tag} 태그 삭제`}
                      className="ml-0.5 rounded-full px-1 leading-none hover:bg-black/10"
                      disabled={saving}
                      onClick={() => removeTag(tag)}
                      data-testid={`holding-draft-tag-remove-${idx}`}
                    >
                      ×
                    </button>
                  )}
                </Badge>
              ))}
            </div>
            {!readOnly && (
              <div className="flex gap-2">
                <Input
                  id="holding-draft-tags-input"
                  value={tagInput}
                  disabled={saving}
                  onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ',') {
                      e.preventDefault();
                      addTagsFromInput();
                    }
                  }}
                  placeholder="태그 입력 후 Enter · 쉼표/JSON 배열 붙여넣기 가능"
                  data-testid="holding-draft-tags-input"
                />
                <Button
                  type="button"
                  variant="outline"
                  disabled={saving || !tagInput.trim()}
                  onClick={addTagsFromInput}
                  data-testid="holding-draft-tags-add"
                >
                  추가
                </Button>
              </div>
            )}
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
