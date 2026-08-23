'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  listPlatformCredentials,
  upsertPlatformCredential,
  PlatformCredentialStatus,
} from '@/lib/api/admin/marketing';
import {
  TtsVoicePicker,
  CommentTtsVoicePicker,
  BgmTrackPicker,
  extractError,
} from '@/components/admin/marketing/PlatformCredentialsSection';

const SHORTFORM_PLATFORM = 'shortform_video';

/**
 * 숏폼영상 설정 — Instagram 릴스와 YouTube Shorts는 WaggleBot에서 같은 영상을
 * 한 번 렌더링해 재사용하므로(paired render), 나레이션(본문·댓글 TTS)은 두 플랫폼이
 * 아니라 여기 한 곳에서만 설정한다. 플랫폼별 계정 정보(로그인·API 키 등)는
 * 아래 "게시 계정" 섹션에서 계속 개별 관리한다.
 */
export function ShortformVideoSection() {
  const [cred, setCred] = useState<PlatformCredentialStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [ttsVoice, setTtsVoice] = useState('');
  const [commentVoices, setCommentVoices] = useState('');
  const [bgmTrack, setBgmTrack] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listPlatformCredentials();
      const found = list.find((c) => c.platform === SHORTFORM_PLATFORM) ?? null;
      setCred(found);
      setTtsVoice(found?.values['tts_voice'] ?? '');
      setCommentVoices(found?.values['comment_tts_voices'] ?? '');
      setBgmTrack(found?.values['bgm_track'] ?? '');
    } catch (err: unknown) {
      setError(`숏폼영상 설정을 불러오지 못했습니다: ${extractError(err)}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      await upsertPlatformCredential(SHORTFORM_PLATFORM, {
        tts_voice: ttsVoice,
        comment_tts_voices: commentVoices,
        bgm_track: bgmTrack,
      });
      setSaved(true);
      await load();
    } catch (err: unknown) {
      setSaveError(`저장에 실패했습니다: ${extractError(err)}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div data-testid="marketing-shortform-video-section">
      <div className="mb-4 flex items-center justify-between gap-4">
        <div>
          <h3 className="font-semibold text-gray-800">숏폼영상</h3>
          <p className="mt-1 text-sm text-gray-500">
            Instagram 릴스와 YouTube Shorts는 같은 영상을 한 번만 만들어 두 플랫폼에
            그대로 올립니다. 나레이션은 플랫폼별로 따로 설정하지 않고 여기서 한 번만
            정합니다.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          {loading ? '로드 중…' : '새로고침'}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <Card className="p-4">
        {loading ? (
          <div className="py-8 text-center text-gray-400">로드 중…</div>
        ) : (
          <div className="space-y-4">
            <TtsVoicePicker value={ttsVoice} onChange={setTtsVoice} />
            <CommentTtsVoicePicker
              value={commentVoices}
              narratorVoice={ttsVoice}
              onChange={setCommentVoices}
            />
            <BgmTrackPicker value={bgmTrack} onChange={setBgmTrack} />

            {saveError && (
              <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {saveError}
              </div>
            )}

            <div className="flex items-center gap-3">
              <Button size="sm" onClick={handleSave} disabled={saving}>
                {saving ? '저장 중…' : '저장'}
              </Button>
              {saved && !saving && (
                <span className="text-xs text-green-700">저장됨</span>
              )}
              {cred?.updated_at && (
                <span className="text-xs text-gray-400">
                  마지막 수정: {new Date(cred.updated_at).toLocaleString('ko-KR')}
                </span>
              )}
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
