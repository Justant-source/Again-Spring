'use client';

import { useEffect, useState } from 'react';
import {
  getScheduledHolding,
  updateScheduledHolding,
  cancelScheduledHolding,
  type ScheduledHoldingDetail,
  type ScheduledHoldingItem,
} from '@/lib/api/admin/content';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { AlertCircle, Trash2 } from 'lucide-react';

const CATEGORY_OPTIONS = [
  { value: 'COUPLE', label: '연인' },
  { value: 'MARRIED', label: '부부' },
  { value: 'FRIEND', label: '친구' },
  { value: 'FAMILY', label: '가족' },
  { value: 'WORK', label: '직장' },
  { value: 'OTHER', label: '기타' },
];

/** ISO Instant → datetime-local value in Asia/Seoul. */
function toDatetimeLocalKst(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}

/** datetime-local (KST wall clock) → ISO Instant. */
function fromDatetimeLocalKst(local: string): string {
  if (!local) return '';
  const withSeconds = local.length === 16 ? `${local}:00` : local;
  return new Date(`${withSeconds}+09:00`).toISOString();
}

function formatKstLabel(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
}

interface EditableItem extends ScheduledHoldingItem {
  scheduledAtLocal: string;
}

interface Props {
  holdingId: string | null;
  onClose: () => void;
  onSaved: () => void;
  onCancelled: () => void;
}

export function EditScheduledPostDialog({ holdingId, onClose, onSaved, onCancelled }: Props) {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState('OTHER');
  const [slotLocal, setSlotLocal] = useState('');
  const [items, setItems] = useState<EditableItem[]>([]);
  const [detail, setDetail] = useState<ScheduledHoldingDetail | null>(null);

  useEffect(() => {
    if (!holdingId) {
      setDetail(null);
      return;
    }
    setLoading(true);
    setError('');
    getScheduledHolding(holdingId)
      .then((d) => {
        setDetail(d);
        setTitle(d.title || '');
        setBody(d.body || '');
        setCategory(d.category || 'OTHER');
        setSlotLocal(toDatetimeLocalKst(d.scheduledPublishAt));
        setItems(
          (d.items || []).map((it) => ({
            ...it,
            scheduledAtLocal: toDatetimeLocalKst(it.scheduledAt),
          }))
        );
      })
      .catch((err: any) => {
        setError(err?.response?.data?.message || '홀딩 글을 불러오지 못했어요.');
      })
      .finally(() => setLoading(false));
  }, [holdingId]);

  if (!holdingId) return null;

  const editable = detail?.status === 'SCHEDULED';

  function updateItem(index: number, patch: Partial<EditableItem>) {
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }

  function removeItem(index: number) {
    setItems((prev) => {
      const target = prev[index];
      return prev.filter((it, i) => {
        if (i === index) return false;
        if (it.parentRef && target && it.parentRef === target.ref) return false;
        return true;
      });
    });
  }

  async function handleSave() {
    if (!holdingId || !editable) return;
    setSubmitting(true);
    setError('');
    try {
      await updateScheduledHolding(holdingId, {
        title,
        body,
        category,
        scheduledPublishAt: fromDatetimeLocalKst(slotLocal),
        items: items.map((it) => ({
          ref: it.ref,
          parentRef: it.parentRef || null,
          personaId: it.personaId,
          body: it.body,
          scheduledAt: fromDatetimeLocalKst(it.scheduledAtLocal),
          stance: it.stance,
          priority: it.priority,
        })),
      });
      onSaved();
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel() {
    if (!holdingId || !editable) return;
    if (!window.confirm('이 예약 홀딩을 취소할까요? 발행되지 않습니다.')) return;
    setSubmitting(true);
    setError('');
    try {
      await cancelScheduledHolding(holdingId);
      onCancelled();
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '취소에 실패했어요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={!!holdingId} onOpenChange={onClose}>
      <DialogContent
        className="max-w-3xl max-h-[90vh] overflow-y-auto"
        data-testid="admin-scheduled-edit-dialog"
      >
        <DialogHeader>
          <DialogTitle>예약 홀딩 수정</DialogTitle>
        </DialogHeader>

        {loading ? (
          <p className="text-sm text-gray-500 py-8 text-center">불러오는 중…</p>
        ) : (
          <div className="space-y-4">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
                <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
                <p className="text-sm text-red-700">{error}</p>
              </div>
            )}

            {!editable && (
              <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md p-3">
                상태가 {detail?.status}이라 수정할 수 없습니다. 조회만 가능합니다.
              </p>
            )}

            <div className="space-y-2">
              <Label>제목</Label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                disabled={!editable || submitting}
                data-testid="admin-scheduled-title"
              />
            </div>

            <div className="space-y-2">
              <Label>본문</Label>
              <Textarea
                value={body}
                onChange={(e) => setBody(e.target.value)}
                rows={6}
                disabled={!editable || submitting}
                data-testid="admin-scheduled-body"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>카테고리</Label>
                <Select value={category} onValueChange={setCategory} disabled={!editable || submitting}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORY_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>글 발행 예정 (KST)</Label>
                <Input
                  type="datetime-local"
                  value={slotLocal}
                  onChange={(e) => setSlotLocal(e.target.value)}
                  disabled={!editable || submitting}
                  data-testid="admin-scheduled-slot"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>댓글 · 대댓글 릴리스 일정</Label>
              {items.length === 0 ? (
                <p className="text-sm text-gray-500">후보가 없습니다.</p>
              ) : (
                <ul className="space-y-3" data-testid="admin-scheduled-items">
                  {items.map((it, index) => (
                    <li
                      key={it.ref}
                      className="border rounded-md p-3 space-y-2 bg-gray-50/50"
                      data-testid={`admin-scheduled-item-${it.ref}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2 text-xs text-gray-600">
                          <Badge variant={it.type === 'REPLY' ? 'secondary' : 'default'}>
                            {it.type === 'REPLY' ? '대댓글' : '댓글'}
                          </Badge>
                          <span className="font-mono">{it.personaId}</span>
                          <span className="text-gray-400">{formatKstLabel(fromDatetimeLocalKst(it.scheduledAtLocal) || it.scheduledAt)}</span>
                        </div>
                        {editable && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeItem(index)}
                            disabled={submitting}
                            aria-label="후보 삭제"
                          >
                            <Trash2 className="h-4 w-4 text-red-600" />
                          </Button>
                        )}
                      </div>
                      <Input
                        type="datetime-local"
                        value={it.scheduledAtLocal}
                        onChange={(e) => updateItem(index, { scheduledAtLocal: e.target.value })}
                        disabled={!editable || submitting}
                      />
                      <Input
                        value={it.personaId}
                        onChange={(e) => updateItem(index, { personaId: e.target.value })}
                        disabled={!editable || submitting}
                        placeholder="personaId"
                      />
                      <Textarea
                        value={it.body}
                        onChange={(e) => updateItem(index, { body: e.target.value })}
                        rows={3}
                        disabled={!editable || submitting}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}

        <DialogFooter className="gap-2 sm:gap-0">
          {editable && (
            <Button
              type="button"
              variant="destructive"
              onClick={handleCancel}
              disabled={submitting || loading}
              data-testid="admin-scheduled-cancel"
            >
              홀딩 취소
            </Button>
          )}
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            닫기
          </Button>
          {editable && (
            <Button
              type="button"
              onClick={handleSave}
              disabled={submitting || loading}
              data-testid="admin-scheduled-save"
            >
              {submitting ? '저장 중…' : '저장'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
