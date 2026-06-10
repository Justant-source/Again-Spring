'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  listPlatformCredentials,
  upsertPlatformCredential,
  deletePlatformCredential,
  PlatformCredentialStatus,
} from '@/lib/api/admin/marketing';

// Korean display labels — presentation lives in the FE; field *structure* comes from ASM.
const PLATFORM_LABELS: Record<string, string> = {
  x: 'X (트위터)',
  instagram_feed: 'Instagram 피드',
  instagram_reels: 'Instagram 릴스',
  naver_blog: '네이버 블로그',
  naver_clip: '네이버 클립',
  youtube_shorts: 'YouTube Shorts',
  threads: 'Threads',
};

const FIELD_LABELS: Record<string, string> = {
  handle: '핸들 / 이메일',
  username: '사용자명',
  password: '비밀번호',
  totp_secret: '2FA TOTP 시크릿',
  naver_id: '네이버 아이디',
  blog_id: '블로그 ID',
  client_id: 'OAuth Client ID',
  client_secret: 'OAuth Client Secret',
  refresh_token: 'Refresh Token',
  channel_id: '채널 ID',
  access_token: 'Access Token',
  user_id: '사용자 ID',
};

const platformLabel = (p: string) => PLATFORM_LABELS[p] ?? p;
const fieldLabel = (k: string) => FIELD_LABELS[k] ?? k;

/** First non-secret field value — shown on the card as "which account". */
function primaryAccount(cred: PlatformCredentialStatus): string | null {
  for (const f of cred.fields) {
    if (!f.secret && cred.values[f.key]) return cred.values[f.key];
  }
  return null;
}

export function PlatformCredentialsSection() {
  const [creds, setCreds] = useState<PlatformCredentialStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<PlatformCredentialStatus | null>(null);
  const [formValues, setFormValues] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setCreds(await listPlatformCredentials());
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`계정 정보를 불러오지 못했습니다: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  const openEdit = (cred: PlatformCredentialStatus) => {
    // Prefill public values; secret fields stay blank (blank = keep existing).
    const init: Record<string, string> = {};
    for (const f of cred.fields) {
      init[f.key] = f.secret ? '' : cred.values[f.key] ?? '';
    }
    setFormValues(init);
    setSaveError(null);
    setEditing(cred);
  };

  const handleSave = async () => {
    if (!editing) return;
    const missing = editing.fields.filter((f) => {
      if (!f.required) return false;
      if ((formValues[f.key] ?? '').trim()) return false;
      // a required secret that is already stored may be left blank
      if (f.secret && editing.secret_set[f.key]) return false;
      return true;
    });
    if (missing.length > 0) {
      setSaveError(`필수 항목을 입력해주세요: ${missing.map((m) => fieldLabel(m.key)).join(', ')}`);
      return;
    }

    // Build payload: send public values (allow clearing); omit blank secrets (keep existing).
    const payload: Record<string, string> = {};
    for (const f of editing.fields) {
      const v = (formValues[f.key] ?? '').trim();
      if (f.secret && v === '') continue;
      payload[f.key] = v;
    }

    setSaving(true);
    setSaveError(null);
    try {
      await upsertPlatformCredential(editing.platform, payload);
      setEditing(null);
      await load();
    } catch (err: unknown) {
      setSaveError(`저장에 실패했습니다: ${extractError(err)}`);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (platform: string) => {
    if (!window.confirm(`${platformLabel(platform)} 계정 정보를 삭제할까요?`)) return;
    setDeleting(platform);
    setError(null);
    try {
      await deletePlatformCredential(platform);
      await load();
    } catch (err: unknown) {
      setError(`삭제에 실패했습니다: ${extractError(err)}`);
    } finally {
      setDeleting(null);
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-gray-500">
          플랫폼별 게시 계정 정보를 입력합니다. 비밀·토큰 값은 ASM 서버에서 암호화되어 저장되며,
          저장 후에는 다시 표시되지 않습니다.
        </p>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          {loading ? '로드 중…' : '새로고침'}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-8 text-center text-gray-400">로드 중…</div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {creds.map((cred) => {
            const account = primaryAccount(cred);
            return (
              <Card key={cred.platform} className="flex flex-col p-4">
                <div className="mb-2 flex items-center justify-between">
                  <span className="font-medium">{platformLabel(cred.platform)}</span>
                  <Badge
                    className={
                      cred.configured
                        ? 'bg-green-200 text-green-800'
                        : 'bg-gray-200 text-gray-600'
                    }
                  >
                    {cred.configured ? '설정됨' : '미설정'}
                  </Badge>
                </div>

                <div className="mb-3 min-h-[2.5rem] text-sm text-gray-600">
                  {cred.configured ? (
                    <>
                      {account && <div className="truncate font-mono text-xs">{account}</div>}
                      {cred.updated_at && (
                        <div className="text-xs text-gray-400">
                          수정: {new Date(cred.updated_at).toLocaleString('ko-KR')}
                        </div>
                      )}
                    </>
                  ) : (
                    <span className="text-xs text-gray-400">계정 정보가 없습니다.</span>
                  )}
                </div>

                <div className="mt-auto flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => openEdit(cred)}>
                    {cred.configured ? '편집' : '계정 연결'}
                  </Button>
                  {cred.configured && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-red-600 hover:text-red-700"
                      onClick={() => handleDelete(cred.platform)}
                      disabled={deleting === cred.platform}
                    >
                      {deleting === cred.platform ? '삭제 중…' : '삭제'}
                    </Button>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* 편집 다이얼로그 — 필드는 ASM 스키마로 동적 렌더 */}
      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editing ? `${platformLabel(editing.platform)} 계정 정보` : ''}
            </DialogTitle>
          </DialogHeader>

          {editing && (
            <div className="space-y-4 py-2">
              {editing.fields.map((f) => {
                const storedSecret = f.secret && editing.secret_set[f.key];
                return (
                  <div key={f.key} className="space-y-1">
                    <Label htmlFor={`cred-${f.key}`}>
                      {fieldLabel(f.key)}
                      {f.required && <span className="ml-1 text-red-500">*</span>}
                      {f.secret && <span className="ml-1 text-xs text-gray-400">(암호화)</span>}
                    </Label>
                    <Input
                      id={`cred-${f.key}`}
                      type={f.secret ? 'password' : 'text'}
                      autoComplete={f.secret ? 'new-password' : 'off'}
                      value={formValues[f.key] ?? ''}
                      onChange={(e) =>
                        setFormValues((prev) => ({ ...prev, [f.key]: e.target.value }))
                      }
                      placeholder={storedSecret ? '설정됨 — 변경하려면 입력' : ''}
                      className="text-sm"
                    />
                  </div>
                );
              })}

              {saveError && (
                <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {saveError}
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)} disabled={saving}>
              취소
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? '저장 중…' : '저장'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function extractError(err: unknown): string {
  // axios error: prefer the server's message (BE passes through ASM's {"detail": ...})
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as { response?: { data?: { message?: string; detail?: string } }; message?: string };
    const data = anyErr.response?.data;
    if (data?.message) return data.message;
    if (data?.detail) return data.detail;
    if (anyErr.message) return anyErr.message;
  }
  return String(err);
}
