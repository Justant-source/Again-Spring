'use client';

import { useState, useEffect } from 'react';
import { saveCorrection } from '@/lib/api/admin/corrections';
import { updatePost } from '@/lib/api/admin/content';
import type { AdminPost, AdminComment } from '@/lib/api/admin/content';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { AlertCircle, Sparkles, Save, Info } from 'lucide-react';
import { DiffPanel } from '@/components/admin/content/DiffPanel';

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

type TargetType = 'POST' | 'COMMENT';

interface Props {
  post?: AdminPost | null;
  comment?: AdminComment | null;
  onClose: () => void;
  onCommitted: () => void;
}

export function AiImproveDialog({ post, comment, onClose, onCommitted }: Props) {
  const targetType: TargetType = post ? 'POST' : 'COMMENT';
  const targetId = post ? post.id : String(comment?.id ?? '');

  const isOpen = !!(post || comment);

  // POST 필드 원본값
  const origTitle       = post?.title ?? '';
  const origBody        = post ? (post.bodyPublished ?? post.bodyRaw ?? '') : (comment?.body ?? '');
  const origPartnerBody = post ? (post.partnerBodyPublished ?? post.partnerBodyRaw ?? '') : '';

  const [corrTitle,       setCorrTitle]       = useState(origTitle);
  const [corrBody,        setCorrBody]        = useState(origBody);
  const [corrPartnerBody, setCorrPartnerBody] = useState(origPartnerBody);
  const [applyLive,       setApplyLive]       = useState(true);
  const [adminOpinion,    setAdminOpinion]    = useState('');
  const [saving,          setSaving]          = useState(false);
  const [error,           setError]           = useState('');

  useEffect(() => {
    if (isOpen) {
      setCorrTitle(origTitle);
      setCorrBody(origBody);
      setCorrPartnerBody(origPartnerBody);
      setAdminOpinion('');
      setError('');
    }
  }, [isOpen, origTitle, origBody, origPartnerBody]);

  const titleChanged       = corrTitle       !== origTitle;
  const bodyChanged        = corrBody        !== origBody;
  const partnerBodyChanged = corrPartnerBody !== origPartnerBody;
  const hasAnyChange       = titleChanged || bodyChanged || partnerBodyChanged;

  function handleOpenChange(open: boolean) {
    if (!open) handleClose();
  }

  function handleClose() {
    setCorrTitle(origTitle);
    setCorrBody(origBody);
    setCorrPartnerBody(origPartnerBody);
    setAdminOpinion('');
    setError('');
    setSaving(false);
    onClose();
  }

  async function handleSave() {
    if (!hasAnyChange) {
      setError('원본과 동일합니다. 수정 후 저장하세요.');
      return;
    }
    setError('');
    setSaving(true);
    try {
      if (targetType === 'POST') {
        // 본문 변경 → saveCorrection (학습 데이터 + bodyPublished 교체)
        if (bodyChanged) {
          await saveCorrection({
            targetType: 'POST',
            targetId,
            correctedText: corrBody,
            applyLive,
            adminOpinion: adminOpinion.trim() || null,
          });
        }
        // 제목·상대방 본문 변경 → updatePost
        if (titleChanged || partnerBodyChanged) {
          await updatePost(targetId, {
            ...(titleChanged       ? { title:          corrTitle       } : {}),
            ...(partnerBodyChanged ? { partnerBodyRaw: corrPartnerBody } : {}),
          });
        }
      } else {
        // 댓글: 단일 본문 saveCorrection
        if (!bodyChanged) {
          setError('원본과 동일합니다. 수정 후 저장하세요.');
          setSaving(false);
          return;
        }
        await saveCorrection({
          targetType: 'COMMENT',
          targetId,
          correctedText: corrBody,
          applyLive,
          adminOpinion: adminOpinion.trim() || null,
        });
      }
      onCommitted();
      handleClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  }

  const dialogTitle = targetType === 'POST' ? 'AI 게시글 개선' : 'AI 댓글 개선';

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[90vw] max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-purple-500" />
            {dialogTitle}
          </DialogTitle>
        </DialogHeader>

        {/* 안내 배너 */}
        <div className="p-3 bg-blue-50 border border-blue-200 rounded-md flex items-start gap-2 text-sm text-blue-800">
          <Info className="h-4 w-4 shrink-0 mt-0.5" />
          <span>
            저장하면 학습 데이터가 쌓입니다.{' '}
            <strong>AI 규칙 관리 → 첨삭 이력</strong>에서 일괄 분석을 요청할 수 있습니다.
            {targetType === 'POST' && (
              <> 제목·상대방 본문은 즉시 교체되며 학습 데이터에는 포함되지 않습니다.</>
            )}
          </span>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 shrink-0" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {/* 필드 패널들 */}
        <div className="space-y-5">
          {targetType === 'POST' && (
            <DiffPanel
              label="제목"
              original={origTitle}
              corrected={corrTitle}
              onChange={setCorrTitle}
              disabled={saving}
              height="h-14"
              placeholder="제목을 수정하세요."
            />
          )}

          <DiffPanel
            label={targetType === 'POST' ? '작성자 본문' : '댓글 내용'}
            original={origBody}
            corrected={corrBody}
            onChange={setCorrBody}
            disabled={saving}
            height="h-44"
            placeholder={targetType === 'POST' ? '작성자 본문을 수정하세요.' : 'AI가 작성한 원본을 이곳에서 수정하세요.'}
          />

          {targetType === 'POST' && (
            <DiffPanel
              label="상대방 본문"
              original={origPartnerBody}
              corrected={corrPartnerBody}
              onChange={setCorrPartnerBody}
              disabled={saving}
              height="h-44"
              placeholder="상대방 본문을 수정하세요."
            />
          )}
        </div>

        {/* 라이브 반영 체크박스 */}
        <div className="flex items-center gap-2 pt-1">
          <Checkbox
            id="applyLive"
            checked={applyLive}
            onCheckedChange={v => setApplyLive(!!v)}
            disabled={saving}
          />
          <Label htmlFor="applyLive" className="text-sm cursor-pointer">
            실제 게시글/댓글 본문도 수정본으로 즉시 교체
          </Label>
        </div>

        {/* 관리자 의견 (선택) — 본문/댓글 교정에만 귀속 */}
        <div className="space-y-1.5 pt-1">
          <Label className="text-sm font-medium">
            관리자 의견{' '}
            <span className="text-xs font-normal text-muted-foreground">
              (선택 · 일괄 분석 시 참고)
            </span>
          </Label>
          {targetType === 'POST' && !bodyChanged && (
            <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded px-2 py-1">
              의견은 <strong>본문(작성자)</strong> 수정 시에만 저장됩니다. 제목·상대방 본문만 바꾸는 경우 의견은 저장되지 않습니다.
            </p>
          )}
          <textarea
            value={adminOpinion}
            onChange={e => setAdminOpinion(e.target.value)}
            disabled={saving}
            rows={3}
            placeholder="왜 이렇게 고쳤는지, 다음에 어떤 방향으로 쓰길 원하는지 간단히 적어주세요. (비워도 됩니다)"
            className="w-full resize-y rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
          />
        </div>

        <DialogFooter className="gap-2 pt-2">
          <Button variant="outline" onClick={handleClose} disabled={saving}>
            취소
          </Button>
          <Button
            onClick={handleSave}
            disabled={saving || !hasAnyChange}
            className="bg-purple-600 hover:bg-purple-700 text-white"
          >
            {saving ? '저장 중...' : (
              <>
                <Save className="h-4 w-4 mr-1.5" />
                {bodyChanged ? '학습 데이터 저장' : '저장'}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
