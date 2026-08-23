'use client';

import { useEffect, useRef, useState } from 'react';
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
  startYoutubeOauth,
  listTtsVoices,
  fetchTtsVoiceSampleBlob,
  listBgmTracks,
  fetchBgmSampleBlob,
  PlatformCredentialStatus,
  TtsVoice,
  BgmTrack,
} from '@/lib/api/admin/marketing';

// Korean display labels — presentation lives in the FE; field *structure* comes from ASM.
const PLATFORM_LABELS: Record<string, string> = {
  // ASM credential PK remains `x` (login session); display as the only X product.
  x: 'X 4단 스레드',
  x_thread: 'X 4단 스레드',
  instagram_feed: 'Instagram 피드',
  instagram_reels: 'Instagram 릴스',
  naver_blog: '네이버 블로그',
  naver_clip: '네이버 클립',
  youtube_shorts: 'YouTube Shorts',
  threads: 'Threads',
  shortform_video: '숏폼영상 (릴스·쇼츠 공용)',
};

const FIELD_LABELS: Record<string, string> = {
  handle: '핸들 / 이메일',
  username: '사용자명',
  email: '이메일',
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
  app_id: 'Meta App ID',
  app_secret: 'Meta App Secret',
  ig_user_id: 'Instagram 계정 ID (ig-user-id)',
  graph_host: 'Graph Host (선택)',
  tts_voice: '본문 TTS 음성',
  comment_tts_voices: '댓글 TTS 음성',
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

  // YouTube OAuth
  const [oauthLoading, setOauthLoading] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);

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

  /** YouTube OAuth 팝업 실행 */
  const handleYoutubeOauth = async () => {
    if (!editing) return;
    setOauthLoading(true);
    setOauthError(null);
    try {
      const redirectUri = `${window.location.origin}/admin/marketing/youtube-oauth/callback`;
      const { auth_url } = await startYoutubeOauth(redirectUri);

      // 팝업 오픈
      const popup = window.open(auth_url, 'youtube-oauth', 'width=600,height=700,noopener');
      if (!popup) {
        setOauthError('팝업이 차단되었습니다. 팝업 허용 후 다시 시도해주세요.');
        return;
      }

      // 콜백에서 postMessage 수신
      const onMessage = async (e: MessageEvent) => {
        if (e.origin !== window.location.origin) return;
        if (e.data === 'youtube-oauth-success') {
          window.removeEventListener('message', onMessage);
          setEditing(null);
          await load();
        } else if (typeof e.data === 'string' && e.data.startsWith('youtube-oauth-error:')) {
          window.removeEventListener('message', onMessage);
          setOauthError(`Google 연결 실패: ${e.data.replace('youtube-oauth-error:', '')}`);
        }
      };
      window.addEventListener('message', onMessage);
    } catch (err: unknown) {
      setOauthError(`Google 연결 시작 실패: ${extractError(err)}`);
    } finally {
      setOauthLoading(false);
    }
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
          {creds
            .filter((cred) => cred.platform !== 'shortform_video')
            .map((cred) => {
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
        <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editing ? `${platformLabel(editing.platform)} 계정 정보` : ''}
            </DialogTitle>
          </DialogHeader>

          {editing && (
            <div className="space-y-4 py-2">
              {/* Threads: 인스타 계정 자동 상속 — 입력 불필요 */}
              {editing.platform === 'threads' ? (
                <ThreadsCredentialInfo creds={creds} />
              ) : (
                <>
                  {editing.fields
                    .filter((f) => {
                      // YouTube Shorts: refresh_token은 OAuth로 자동 획득 — 입력 숨김
                      if (editing.platform === 'youtube_shorts' && f.key === 'refresh_token') return false;
                      return true;
                    })
                    .map((f) => {
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

                  {/* YouTube Shorts: Google 계정 연결 섹션 */}
                  {editing.platform === 'youtube_shorts' && (
                    <YoutubeOAuthSection
                      editing={editing}
                      formValues={formValues}
                      oauthLoading={oauthLoading}
                      oauthError={oauthError}
                      onConnect={handleYoutubeOauth}
                    />
                  )}
                </>
              )}

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

export function extractError(err: unknown): string {
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

// ---------------------------------------------------------------------------
// WaggleBot TTS voice picker + preview.
// Exported for reuse by ShortformVideoSection (숏폼영상 설정 박스) — 릴스·쇼츠가
// WaggleBot에서 같은 영상을 공유하므로 나레이션은 여기서만 렌더, 개별 플랫폼
// 편집 다이얼로그에는 더 이상 노출하지 않는다.
// ---------------------------------------------------------------------------
export interface TtsVoicePickerProps {
  value: string;
  onChange: (key: string) => void;
}

export function TtsVoicePicker({ value, onChange }: TtsVoicePickerProps) {
  const [voices, setVoices] = useState<TtsVoice[]>([]);
  const [defaultVoice, setDefaultVoice] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [playing, setPlaying] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  useEffect(() => {
    if (typeof Audio !== 'undefined') {
      audioRef.current = new Audio();
    }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const catalog = await listTtsVoices();
        if (cancelled) return;
        setVoices(catalog.voices ?? []);
        const def = catalog.defaultVoice || '';
        setDefaultVoice(def);
        // Persist default into the form when nothing is stored yet
        if (!value && def) onChange(def);
      } catch (err: unknown) {
        if (!cancelled) setError(`음성 목록을 불러오지 못했습니다: ${extractError(err)}`);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.removeAttribute('src');
      }
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
    };
    // Intentionally once on mount — parent value/onChange identity is stable for the dialog session.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selected = value || defaultVoice;

  const handlePreview = async (voice: TtsVoice) => {
    const audio = audioRef.current;
    const samplePath = voice.sampleUrl;
    if (!audio || !samplePath) {
      setPreviewError('이 음성은 미리듣기 샘플이 없습니다.');
      return;
    }
    setPreviewError(null);
    try {
      if (playing === voice.key) {
        audio.pause();
        setPlaying(null);
        return;
      }
      audio.pause();
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
      const blob = await fetchTtsVoiceSampleBlob(samplePath);
      const url = URL.createObjectURL(blob);
      objectUrlRef.current = url;
      audio.onended = () => {
        setPlaying(null);
        if (objectUrlRef.current) {
          URL.revokeObjectURL(objectUrlRef.current);
          objectUrlRef.current = null;
        }
      };
      audio.src = url;
      await audio.play();
      setPlaying(voice.key);
    } catch (err: unknown) {
      setPlaying(null);
      setPreviewError(`미리듣기 실패: ${extractError(err)}`);
    }
  };

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">본문 TTS 음성</span>
        <span className="text-xs text-gray-400">기본: {defaultVoice}</span>
      </div>
      <p className="mb-3 text-xs text-gray-500">
        사연 본문·클로징 낭독에 사용할 음성을 고릅니다. 미리듣기로 확인한 뒤 선택하세요.
      </p>
      {loading ? (
        <div className="py-3 text-center text-xs text-gray-400">음성 목록 로드 중…</div>
      ) : error ? (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {error}
        </div>
      ) : (
        <div className="max-h-56 space-y-1 overflow-y-auto pr-1">
          {voices.map((v) => {
            const isSelected = selected === v.key;
            return (
              <div
                key={v.key}
                className={`flex items-center gap-2 rounded border px-2 py-1.5 ${
                  isSelected ? 'border-peach bg-white' : 'border-transparent hover:bg-white/70'
                }`}
              >
                <input
                  type="radio"
                  id={`tts-${v.key}`}
                  name="tts_voice"
                  className="accent-[#C9785A]"
                  checked={isSelected}
                  onChange={() => onChange(v.key)}
                />
                <label htmlFor={`tts-${v.key}`} className="min-w-0 flex-1 cursor-pointer">
                  <div className="truncate text-sm text-gray-800">{v.label || v.key}</div>
                  <div className="truncate font-mono text-[10px] text-gray-400">
                    {v.key}
                    {v.gender ? ` · ${v.gender}` : ''}
                  </div>
                </label>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="h-7 shrink-0 px-2 text-xs"
                  disabled={!v.sampleUrl && !v.hasSample}
                  onClick={() => handlePreview(v)}
                >
                  {playing === v.key ? '정지' : '미리듣기'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
      {previewError && (
        <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          {previewError}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Comment TTS pool — max 5 voices, stored as comma-separated keys
// ---------------------------------------------------------------------------
const COMMENT_TTS_MAX = 5;

function parseCommentVoiceCsv(csv: string): string[] {
  return csv
    .split(/[,;]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, COMMENT_TTS_MAX);
}

export interface CommentTtsVoicePickerProps {
  value: string;
  narratorVoice: string;
  onChange: (csv: string) => void;
}

export function CommentTtsVoicePicker({ value, narratorVoice, onChange }: CommentTtsVoicePickerProps) {
  const [voices, setVoices] = useState<TtsVoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [playing, setPlaying] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  const selected = parseCommentVoiceCsv(value);

  useEffect(() => {
    if (typeof Audio !== 'undefined') {
      audioRef.current = new Audio();
    }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const catalog = await listTtsVoices();
        if (cancelled) return;
        setVoices(catalog.voices ?? []);
      } catch (err: unknown) {
        if (!cancelled) setError(`음성 목록을 불러오지 못했습니다: ${extractError(err)}`);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.removeAttribute('src');
      }
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
    };
  }, []);

  const toggle = (key: string) => {
    const set = new Set(selected);
    if (set.has(key)) {
      set.delete(key);
    } else if (set.size >= COMMENT_TTS_MAX) {
      return;
    } else {
      set.add(key);
    }
    onChange([...set].join(','));
  };

  const handlePreview = async (voice: TtsVoice) => {
    const audio = audioRef.current;
    const samplePath = voice.sampleUrl;
    if (!audio || !samplePath) {
      setPreviewError('이 음성은 미리듣기 샘플이 없습니다.');
      return;
    }
    setPreviewError(null);
    try {
      if (playing === voice.key) {
        audio.pause();
        setPlaying(null);
        return;
      }
      audio.pause();
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
      const blob = await fetchTtsVoiceSampleBlob(samplePath);
      const url = URL.createObjectURL(blob);
      objectUrlRef.current = url;
      audio.onended = () => {
        setPlaying(null);
        if (objectUrlRef.current) {
          URL.revokeObjectURL(objectUrlRef.current);
          objectUrlRef.current = null;
        }
      };
      audio.src = url;
      await audio.play();
      setPlaying(voice.key);
    } catch (err: unknown) {
      setPlaying(null);
      setPreviewError(`미리듣기 실패: ${extractError(err)}`);
    }
  };

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">댓글 TTS 음성 (최대 {COMMENT_TTS_MAX})</span>
        <span className="text-xs text-gray-400">
          {selected.length}/{COMMENT_TTS_MAX} 선택
        </span>
      </div>
      <p className="mb-3 text-xs text-gray-500">
        댓글마다 아래 풀에서 랜덤으로 배정합니다. 본문 음성({narratorVoice || '미선택'})과 겹치지
        않는 목소리를 고르면 더 자연스럽습니다.
      </p>
      {loading ? (
        <div className="py-3 text-center text-xs text-gray-400">음성 목록 로드 중…</div>
      ) : error ? (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {error}
        </div>
      ) : (
        <div className="max-h-56 space-y-1 overflow-y-auto pr-1">
          {voices.map((v) => {
            const isSelected = selected.includes(v.key);
            const atCap = !isSelected && selected.length >= COMMENT_TTS_MAX;
            const isNarrator = narratorVoice !== '' && v.key === narratorVoice;
            return (
              <div
                key={v.key}
                className={`flex items-center gap-2 rounded border px-2 py-1.5 ${
                  isSelected ? 'border-[#5F8F76] bg-white' : 'border-transparent hover:bg-white/70'
                } ${atCap ? 'opacity-50' : ''}`}
              >
                <input
                  type="checkbox"
                  id={`comment-tts-${v.key}`}
                  className="accent-[#5F8F76]"
                  checked={isSelected}
                  disabled={atCap}
                  onChange={() => toggle(v.key)}
                />
                <label htmlFor={`comment-tts-${v.key}`} className="min-w-0 flex-1 cursor-pointer">
                  <div className="truncate text-sm text-gray-800">
                    {v.label || v.key}
                    {isNarrator ? (
                      <span className="ml-1 text-[10px] text-amber-700">(본문과 동일)</span>
                    ) : null}
                  </div>
                  <div className="truncate font-mono text-[10px] text-gray-400">
                    {v.key}
                    {v.gender ? ` · ${v.gender}` : ''}
                  </div>
                </label>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="h-7 shrink-0 px-2 text-xs"
                  disabled={!v.sampleUrl && !v.hasSample}
                  onClick={() => handlePreview(v)}
                >
                  {playing === v.key ? '정지' : '미리듣기'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
      {previewError && (
        <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          {previewError}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// YouTube Shorts — OAuth 연결 섹션 서브컴포넌트
// ---------------------------------------------------------------------------
interface YoutubeOAuthSectionProps {
  editing: PlatformCredentialStatus;
  formValues: Record<string, string>;
  oauthLoading: boolean;
  oauthError: string | null;
  onConnect: () => void;
}

function YoutubeOAuthSection({
  editing,
  formValues,
  oauthLoading,
  oauthError,
  onConnect,
}: YoutubeOAuthSectionProps) {
  const refreshConnected = editing.secret_set['refresh_token'] ?? false;

  // 연결 버튼 활성 조건: client_id/client_secret이 이미 저장되어 있거나 폼에 입력됨
  const hasClientId =
    (formValues['client_id'] ?? '').trim() !== '' || (editing.values['client_id'] ?? '') !== '';
  const hasClientSecret =
    (formValues['client_secret'] ?? '').trim() !== '' || (editing.secret_set['client_secret'] ?? false);
  const canConnect = hasClientId && hasClientSecret;

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">YouTube 계정 연결 상태</span>
        <Badge
          className={
            refreshConnected ? 'bg-green-200 text-green-800' : 'bg-yellow-100 text-yellow-800'
          }
        >
          {refreshConnected ? '연결됨' : '미연결'}
        </Badge>
      </div>
      <p className="mb-3 text-xs text-gray-500">
        {refreshConnected
          ? 'Google 계정이 연결되어 있습니다. 재연결하려면 아래 버튼을 클릭하세요.'
          : 'Client ID와 Client Secret 저장 후 아래 버튼으로 Google 계정을 연결해주세요.'}
      </p>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={!canConnect || oauthLoading}
        onClick={onConnect}
        className="w-full"
      >
        {oauthLoading ? '연결 중…' : refreshConnected ? 'Google 계정 재연결' : 'Google 계정 연결'}
      </Button>
      {oauthError && (
        <div className="mt-2 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {oauthError}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Threads — 인스타그램 계정 상속 안내 서브컴포넌트
// ---------------------------------------------------------------------------
interface ThreadsCredentialInfoProps {
  creds: PlatformCredentialStatus[];
}

function ThreadsCredentialInfo({ creds }: ThreadsCredentialInfoProps) {
  const igFeed = creds.find((c) => c.platform === 'instagram_feed');
  const igEmail = igFeed?.values['email'] ?? null;
  const igConfigured = igFeed?.configured ?? false;

  return (
    <div className="rounded border border-blue-100 bg-blue-50 p-3 text-sm">
      <p className="mb-1 font-medium text-blue-800">Instagram 계정 정보를 사용합니다</p>
      <p className="text-xs text-blue-700">
        Threads는 별도 로그인 없이 Instagram 피드 계정으로 자동 로그인합니다.
        threads.net 세션은 첫 게시 시 자동으로 생성됩니다.
      </p>
      <div className="mt-2 flex items-center gap-2">
        <Badge
          className={igConfigured ? 'bg-green-200 text-green-800' : 'bg-red-100 text-red-800'}
        >
          {igConfigured ? '인스타 설정됨' : '인스타 미설정'}
        </Badge>
        {igEmail && <span className="truncate font-mono text-xs text-gray-600">{igEmail}</span>}
      </div>
      {!igConfigured && (
        <p className="mt-2 text-xs text-red-600">
          먼저 Instagram 피드 계정 정보를 설정해주세요.
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// WaggleBot BGM track picker + preview.
// Exported for reuse by video rendering options.
// ---------------------------------------------------------------------------
export interface BgmTrackPickerProps {
  value: string;
  onChange: (path: string) => void;
}

export function BgmTrackPicker({ value, onChange }: BgmTrackPickerProps) {
  const [tracks, setTracks] = useState<BgmTrack[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [playing, setPlaying] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  // Group tracks by emotion
  const emotionGroups: Record<string, BgmTrack[]> = {};
  tracks.forEach((track) => {
    if (!emotionGroups[track.emotion]) {
      emotionGroups[track.emotion] = [];
    }
    emotionGroups[track.emotion].push(track);
  });

  useEffect(() => {
    if (typeof Audio !== 'undefined') {
      audioRef.current = new Audio();
    }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const catalog = await listBgmTracks();
        if (cancelled) return;
        setTracks(catalog.tracks ?? []);
      } catch (err: unknown) {
        if (!cancelled) setError(`배경음악 목록을 불러오지 못했습니다: ${extractError(err)}`);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.removeAttribute('src');
      }
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePreview = async (track: BgmTrack) => {
    const audio = audioRef.current;
    const samplePath = track.path;
    if (!audio || !samplePath) {
      setPreviewError('이 배경음악은 미리듣기 샘플이 없습니다.');
      return;
    }
    setPreviewError(null);
    try {
      if (playing === track.path) {
        audio.pause();
        setPlaying(null);
        return;
      }
      audio.pause();
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
        objectUrlRef.current = null;
      }
      const blob = await fetchBgmSampleBlob(samplePath);
      const url = URL.createObjectURL(blob);
      objectUrlRef.current = url;
      audio.onended = () => {
        setPlaying(null);
        if (objectUrlRef.current) {
          URL.revokeObjectURL(objectUrlRef.current);
          objectUrlRef.current = null;
        }
      };
      audio.src = url;
      await audio.play();
      setPlaying(track.path);
    } catch (err: unknown) {
      setPlaying(null);
      setPreviewError(`미리듣기 실패: ${extractError(err)}`);
    }
  };

  const EMOTION_LABELS: Record<string, string> = {
    shock: '충격',
    anger: '분노',
    tension: '긴장',
    sad: '슬픔',
    hype: '하이프',
  };

  const emotionOrder = ['shock', 'anger', 'tension', 'sad', 'hype'];

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">배경음악 (BGM)</span>
      </div>
      <p className="mb-3 text-xs text-gray-500">
        영상에 사용할 배경음악을 감정별로 선택합니다. 미리듣기로 확인한 뒤 선택하세요.
        고르지 않으면 사연의 후킹 감정에 맞춰 자동으로 골라집니다.
      </p>
      {loading ? (
        <div className="py-3 text-center text-xs text-gray-400">배경음악 목록 로드 중…</div>
      ) : error ? (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {error}
        </div>
      ) : (
        <div className="max-h-96 space-y-3 overflow-y-auto pr-1">
          {/* 자동 선택으로 되돌리는 길 — 없으면 한번 고른 뒤 감정 기반 선택으로 복귀할 수 없다 */}
          <div
            className={`flex items-center gap-2 rounded border px-2 py-1.5 ${
              !value ? 'border-sage bg-white' : 'border-transparent hover:bg-white/70'
            }`}
          >
            <input
              type="radio"
              id="bgm-auto"
              name="bgm_track"
              className="accent-[#5F8F76]"
              checked={!value}
              onChange={() => onChange('')}
            />
            <label htmlFor="bgm-auto" className="min-w-0 flex-1 cursor-pointer">
              <div className="text-sm text-gray-800">자동 선택</div>
              <div className="text-[10px] text-gray-400">사연의 후킹 감정에 맞는 곡을 매번 골라 씁니다</div>
            </label>
          </div>
          {emotionOrder.map((emotion) => {
            const emotionTracks = emotionGroups[emotion] ?? [];
            if (emotionTracks.length === 0) return null;
            return (
              <div key={emotion} className="space-y-1">
                <div className="text-xs font-semibold text-gray-700">
                  {EMOTION_LABELS[emotion] || emotion}
                </div>
                {emotionTracks.map((track) => {
                  const isSelected = value === track.path;
                  return (
                    <div
                      key={track.path}
                      className={`flex items-center gap-2 rounded border px-2 py-1.5 ${
                        isSelected ? 'border-sage bg-white' : 'border-transparent hover:bg-white/70'
                      }`}
                    >
                      <input
                        type="radio"
                        id={`bgm-${track.file}`}
                        name="bgm_track"
                        className="accent-[#5F8F76]"
                        checked={isSelected}
                        onChange={() => onChange(track.path)}
                      />
                      <label htmlFor={`bgm-${track.file}`} className="min-w-0 flex-1 cursor-pointer">
                        <div className="text-sm text-gray-800">{track.file}</div>
                        <div className="font-mono text-[10px] text-gray-400">
                          {track.durationSec
                            ? `${Math.floor(track.durationSec / 60)}:${String(track.durationSec % 60).padStart(2, '0')}`
                            : '길이 미측정'}
                        </div>
                      </label>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="h-7 shrink-0 px-2 text-xs"
                        onClick={() => handlePreview(track)}
                      >
                        {playing === track.path ? '정지' : '미리듣기'}
                      </Button>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      )}
      {previewError && (
        <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          {previewError}
        </div>
      )}
    </div>
  );
}
