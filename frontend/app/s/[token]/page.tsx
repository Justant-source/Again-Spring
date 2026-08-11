'use client';

import { useState, useEffect, useCallback, type ReactNode } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import {
  postInviteApi,
  normalizeInvitePreview,
  saveInviteDraft,
  loadInviteDraft,
  clearInviteDraft,
  inviteApiErrorMessage,
} from '@/lib/api/community/postInviteApi';
import { SideStory } from '@/components/community/c3/SideStory';
import { UserChip } from '@/components/community/c3/UserChip';
import { InviteAnswerForm } from '@/components/community/InviteAnswerForm';
import { InviteManageActions } from '@/components/community/InviteManageActions';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';

type UiMode =
  | 'loading'
  | 'error'
  | 'deleted'
  | 'author_self'
  | 'write'
  | 'manage'
  | 'blocked'
  | 'rewrite'
  | 'edit';

function resolveMode(invite: ReturnType<typeof normalizeInvitePreview>): UiMode {
  if (invite.deleted) return 'deleted';
  if (invite.ownership === 'AUTHOR') return 'author_self';
  if (invite.ownership === 'OWNED_BY_OTHER' && invite.partnerState === 'ACTIVE') {
    return 'blocked';
  }
  if (invite.partnerState === 'TOMBSTONE' && invite.canWrite) return 'rewrite';
  if (invite.partnerState === 'ACTIVE') return 'manage';
  if (invite.partnerState === 'NONE' || invite.canWrite) return 'write';
  return 'blocked';
}

export default function PartnerAnswerPage() {
  const router = useRouter();
  const params = useParams();
  const token = params?.token as string;
  const user = useUserStore((s) => s.user);
  useGuestInit();

  const isRegistered = Boolean(user && !user.isGuest);
  const authNext = token ? `/s/${token}` : '/';
  const loginHref = `/login?next=${encodeURIComponent(authNext)}`;

  const [invite, setInvite] = useState<ReturnType<typeof normalizeInvitePreview> | null>(null);
  const [mode, setMode] = useState<UiMode>('loading');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [bodyRaw, setBodyRaw] = useState('');

  const reload = useCallback(async () => {
    if (!token) return;
    try {
      const data = await postInviteApi.getByToken(token);
      const normalized = normalizeInvitePreview(data);
      setInvite(normalized);
      setMode(resolveMode(normalized));
      setLoadError(null);
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 410) {
        setInvite(null);
        setMode('deleted');
        return;
      }
      console.error('Failed to load invite preview:', err);
      setInvite(null);
      setMode('error');
      setLoadError(inviteApiErrorMessage(err, '초대 링크가 유효하지 않습니다'));
    }
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;

    (async () => {
      setMode('loading');
      try {
        const data = await postInviteApi.getByToken(token);
        if (cancelled) return;
        const normalized = normalizeInvitePreview(data);
        setInvite(normalized);
        const next = resolveMode(normalized);
        setMode(next);

        // 작성/재작성 폼일 때만 초안 복원
        if (next === 'write' || next === 'rewrite') {
          const draft = loadInviteDraft(token);
          if (draft) setBodyRaw(draft);
        }
      } catch (err) {
        if (cancelled) return;
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 410) {
          setMode('deleted');
          return;
        }
        console.error('Failed to load invite preview:', err);
        setMode('error');
        setLoadError(inviteApiErrorMessage(err, '초대 링크가 유효하지 않습니다'));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const persistDraftBeforeAuth = () => {
    if (token && bodyRaw.trim()) saveInviteDraft(token, bodyRaw);
  };

  const goToPost = (postId?: string) => {
    router.push(postId ? `/community/${postId}` : '/community');
  };

  const handleSubmitWrite = async () => {
    if (!bodyRaw.trim()) {
      setActionError('답변을 입력해주세요');
      return;
    }
    try {
      setSubmitting(true);
      setActionError(null);
      await postInviteApi.submitAnswer(token, {
        userTitle: invite?.userTitle || '상대방',
        bodyRaw: bodyRaw.trim(),
      });
      clearInviteDraft(token);
      goToPost(invite?.postId);
    } catch (err) {
      console.error('Failed to submit answer:', err);
      setActionError(
        inviteApiErrorMessage(err, '답변을 제출할 수 없습니다. 잠시 후 다시 시도해주세요.'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitEdit = async () => {
    if (!bodyRaw.trim()) {
      setActionError('답변을 입력해주세요');
      return;
    }
    try {
      setSubmitting(true);
      setActionError(null);
      await postInviteApi.patchAnswer(token, { bodyRaw: bodyRaw.trim() });
      clearInviteDraft(token);
      await reload();
      setBodyRaw('');
      setActionError(null);
    } catch (err) {
      console.error('Failed to patch answer:', err);
      setActionError(
        inviteApiErrorMessage(err, '수정을 저장할 수 없습니다. 잠시 후 다시 시도해주세요.'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleClaim = async () => {
    try {
      setSubmitting(true);
      setActionError(null);
      await postInviteApi.claim(token);
      await reload();
    } catch (err) {
      console.error('Failed to claim:', err);
      setActionError(
        inviteApiErrorMessage(err, '계정 연결에 실패했습니다. 잠시 후 다시 시도해주세요.'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('상대방 글을 삭제할까요? 다시 작성할 수 있습니다.')) return;
    try {
      setSubmitting(true);
      setActionError(null);
      await postInviteApi.deleteAnswer(token);
      await reload();
    } catch (err) {
      console.error('Failed to delete answer:', err);
      setActionError(
        inviteApiErrorMessage(err, '삭제할 수 없습니다. 잠시 후 다시 시도해주세요.'),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const startEdit = () => {
    setBodyRaw(invite?.partnerBodyPublished || '');
    setActionError(null);
    setMode('edit');
  };

  const pageShell = (children: ReactNode) => (
    <div style={{ minHeight: '100vh', background: 'var(--P-bg)', padding: '20px' }}>
      {children}
    </div>
  );

  const topBar = (title: string) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 24,
      }}
    >
      <button
        type="button"
        onClick={() => router.back()}
        aria-label="닫기"
        style={{
          background: 'none',
          border: 'none',
          fontSize: 16,
          color: 'var(--P-ink)',
          cursor: 'pointer',
          padding: 0,
        }}
      >
        닫기
      </button>
      <h1
        style={{
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--faction-partner)',
          margin: 0,
          flex: 1,
          textAlign: 'center',
        }}
      >
        {title}
      </h1>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {user && !user.isGuest ? (
          <UserChip user={user} />
        ) : (
          <Link
            href={loginHref}
            onClick={persistDraftBeforeAuth}
            data-testid="invite-login-link"
            style={{
              fontSize: 12,
              color: 'var(--P-sub)',
              textDecoration: 'none',
              padding: '4px 10px',
              border: '1px solid var(--P-border)',
              borderRadius: 999,
              whiteSpace: 'nowrap',
            }}
          >
            로그인 / 회원가입
          </Link>
        )}
      </div>
    </div>
  );

  if (mode === 'loading') {
    return (
      <div
        style={{
          minHeight: '100vh',
          background: 'var(--P-bg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div style={{ fontSize: 14, color: 'var(--P-sub)' }}>로드 중...</div>
      </div>
    );
  }

  if (mode === 'deleted') {
    return pageShell(
      <div
        data-testid="deleted-post-page"
        style={{ maxWidth: 420, margin: '80px auto', textAlign: 'center' }}
      >
        <p
          data-testid="deleted-post-message"
          style={{ fontSize: 16, color: 'var(--P-ink)', marginBottom: 24 }}
        >
          삭제된 게시글
        </p>
        <Link
          href="/community"
          data-testid="deleted-post-plaza-btn"
          style={{
            display: 'inline-block',
            padding: '12px 20px',
            background: 'var(--P-ink)',
            color: 'white',
            borderRadius: 8,
            fontSize: 14,
            textDecoration: 'none',
          }}
        >
          광장으로 가기
        </Link>
      </div>,
    );
  }

  if (mode === 'error' || !invite) {
    return pageShell(
      <div style={{ fontSize: 14, color: 'var(--faction-partner)' }}>
        {loadError || '초대 링크를 찾을 수 없습니다'}
      </div>,
    );
  }

  if (mode === 'author_self') {
    return pageShell(
      <>
        {topBar('초대 링크')}
        <div
          data-testid="invite-author-blocked"
          style={{ maxWidth: 420, margin: '40px auto', textAlign: 'center' }}
        >
          <p style={{ fontSize: 15, color: 'var(--P-ink)', lineHeight: 1.6, marginBottom: 20 }}>
            내가 올린 사연입니다. 상대방 글은 이 링크로 작성할 수 없어요.
          </p>
          <Link
            href={`/community/${invite.postId}`}
            style={{
              display: 'inline-block',
              padding: '12px 20px',
              background: 'var(--P-ink)',
              color: 'white',
              borderRadius: 8,
              fontSize: 14,
              textDecoration: 'none',
            }}
          >
            사연으로 이동
          </Link>
        </div>
      </>,
    );
  }

  if (mode === 'blocked') {
    return pageShell(
      <>
        {topBar('상대방으로 답하기')}
        <div style={{ maxWidth: 420, margin: '40px auto', textAlign: 'center' }}>
          <p style={{ fontSize: 15, color: 'var(--P-ink)', lineHeight: 1.6, marginBottom: 20 }}>
            다른 계정이 이미 이 상대방 글에 연결되어 있습니다.
          </p>
          {actionError && (
            <div
              role="alert"
              style={{
                padding: '12px 14px',
                background: '#FEE',
                border: '1px solid #F99',
                borderRadius: 8,
                fontSize: 12,
                color: '#C33',
                marginBottom: 20,
                textAlign: 'left',
              }}
            >
              {actionError}
            </div>
          )}
          <Link
            href={`/community/${invite.postId}`}
            style={{
              display: 'inline-block',
              padding: '12px 20px',
              background: 'var(--P-ink)',
              color: 'white',
              borderRadius: 8,
              fontSize: 14,
              textDecoration: 'none',
            }}
          >
            사연 보기
          </Link>
        </div>
      </>,
    );
  }

  const authorBody = invite.authorBodyPublished || '';

  // edit mode (from manage)
  if (mode === 'edit') {
    return pageShell(
      <>
        {topBar('상대방 글 수정')}
        {authorBody && (
          <div style={{ marginBottom: 28 }}>
            <SideStory
              side="g"
              label="상대방의 이야기"
              body={authorBody}
              clamp={false}
              selected={false}
            />
          </div>
        )}
        <InviteAnswerForm
          userTitle={invite.userTitle}
          bodyRaw={bodyRaw}
          onBodyChange={setBodyRaw}
          onSubmit={handleSubmitEdit}
          submitting={submitting}
          error={actionError}
          submitLabel="저장"
          bodyLabel="상대방 글 수정"
        />
        <button
          type="button"
          onClick={() => {
            setActionError(null);
            setMode('manage');
          }}
          style={{
            width: '100%',
            marginTop: 12,
            padding: '12px 14px',
            background: 'transparent',
            border: '1px solid var(--P-border)',
            borderRadius: 8,
            fontSize: 13,
            color: 'var(--P-sub)',
            cursor: 'pointer',
          }}
        >
          취소
        </button>
      </>,
    );
  }

  // rewrite after tombstone
  if (mode === 'rewrite') {
    return pageShell(
      <>
        {topBar('다시 작성')}
        <p
          style={{
            fontSize: 14,
            color: 'var(--P-ink)',
            lineHeight: 1.6,
            marginBottom: 20,
            textAlign: 'center',
          }}
        >
          상대방이 글을 삭제했습니다
        </p>
        {authorBody && (
          <div style={{ marginBottom: 28 }}>
            <SideStory
              side="g"
              label="상대방의 이야기"
              body={authorBody}
              clamp={false}
              selected={false}
            />
          </div>
        )}
        <InviteAnswerForm
          userTitle={invite.userTitle}
          bodyRaw={bodyRaw}
          onBodyChange={(v) => {
            setBodyRaw(v);
            if (token) saveInviteDraft(token, v);
          }}
          onSubmit={handleSubmitWrite}
          submitting={submitting}
          error={actionError}
          submitLabel="다시 작성"
          bodyLabel="상대방으로 다시 답하기"
          submitTestId="invite-rewrite-btn"
        />
      </>,
    );
  }

  // manage: ACTIVE unowned or owned-by-me
  if (mode === 'manage') {
    const manageTestId =
      invite.ownership === 'OWNED' ? 'invite-manage-owned' : 'invite-manage-unowned';
    return pageShell(
      <div data-testid={manageTestId}>
        {topBar('상대방 글')}
        {authorBody && (
          <div style={{ marginBottom: 20 }}>
            <SideStory
              side="g"
              label="상대방의 이야기"
              body={authorBody}
              clamp={false}
              selected={false}
            />
          </div>
        )}
        {invite.partnerBodyPublished && (
          <div style={{ marginBottom: 24 }}>
            <SideStory
              side="r"
              label="내 이야기"
              body={invite.partnerBodyPublished}
              clamp={false}
              selected={false}
            />
          </div>
        )}
        {actionError && (
          <div
            role="alert"
            style={{
              padding: '12px 14px',
              background: '#FEE',
              border: '1px solid #F99',
              borderRadius: 8,
              fontSize: 12,
              color: '#C33',
              marginBottom: 16,
            }}
          >
            {actionError}
          </div>
        )}
        <InviteManageActions
          canClaim={invite.canClaim}
          canEdit={invite.canEdit}
          canDelete={invite.canDelete}
          isRegistered={isRegistered}
          busy={submitting}
          onClaim={handleClaim}
          onEdit={startEdit}
          onDelete={handleDelete}
        />
        {invite.canClaim && !isRegistered && (
          <p style={{ fontSize: 12, color: 'var(--P-sub)', marginTop: 16, textAlign: 'center' }}>
            내 계정으로 연결하려면{' '}
            <Link
              href={loginHref}
              onClick={persistDraftBeforeAuth}
              style={{ color: 'var(--faction-partner)' }}
            >
              로그인
            </Link>
            해주세요.
          </p>
        )}
        <div style={{ marginTop: 20, textAlign: 'center' }}>
          <Link
            href={`/community/${invite.postId}`}
            style={{ fontSize: 13, color: 'var(--P-sub)' }}
          >
            사연 보기
          </Link>
        </div>
      </div>,
    );
  }

  // write: NONE
  return pageShell(
    <>
      {topBar('상대방으로 답하기')}
      {authorBody && (
        <div style={{ marginBottom: 28 }}>
          <SideStory
            side="g"
            label="상대방의 이야기"
            body={authorBody}
            clamp={false}
            selected={false}
          />
        </div>
      )}
      <InviteAnswerForm
        userTitle={invite.userTitle}
        bodyRaw={bodyRaw}
        onBodyChange={(v) => {
          setBodyRaw(v);
          if (token) saveInviteDraft(token, v);
        }}
        onSubmit={handleSubmitWrite}
        submitting={submitting}
        error={actionError}
      />
    </>,
  );
}
