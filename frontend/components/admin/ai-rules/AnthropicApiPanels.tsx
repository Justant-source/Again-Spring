'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  getAnthropicApiKey,
  upsertAnthropicApiKey,
  deleteAnthropicApiKey,
  getAnthropicBaseUrl,
  upsertAnthropicBaseUrl,
  deleteAnthropicBaseUrl,
  type ApiKeyStatus,
  type ApiBaseUrlStatus,
} from '@/lib/api/admin/corrections';
import { KeyRound, Loader2, Save, Trash2, Zap } from 'lucide-react';

export function AnthropicApiKeyPanel() {
  const [status, setStatus] = useState<ApiKeyStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState('');
  const [showInput, setShowInput] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    loadStatus();
  }, []);

  const loadStatus = async () => {
    setLoading(true);
    try {
      const data = await getAnthropicApiKey();
      setStatus(data);
    } catch {
      setError('API 키 상태를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!inputValue.trim()) { setError('API 키를 입력해주세요.'); return; }
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const data = await upsertAnthropicApiKey(inputValue.trim());
      setStatus(data);
      setInputValue('');
      setSuccess('API 키가 저장됐습니다.');
    } catch {
      setError('저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('API 키를 삭제하시겠습니까?')) return;
    setDeleting(true);
    setError('');
    setSuccess('');
    try {
      await deleteAnthropicApiKey();
      setStatus({ isSet: false, maskedValue: '', updatedAt: null, updatedBy: null });
      setSuccess('API 키가 삭제됐습니다.');
    } catch {
      setError('삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
    }
  };

  if (loading) return <p className="text-sm text-muted-foreground text-center py-8">로딩 중…</p>;

  return (
    <div className="space-y-4 max-w-xl">
      <div className="rounded-lg border bg-white p-5 space-y-3">
        <div className="flex items-center gap-2">
          <KeyRound className="w-4 h-4 text-amber-600" />
          <span className="font-medium text-sm">Anthropic API 키</span>
          {status?.isSet ? (
            <span className="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">설정됨</span>
          ) : (
            <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">미설정</span>
          )}
        </div>

        {status?.isSet && (
          <div className="space-y-1 text-sm">
            <div className="font-mono bg-gray-50 rounded px-3 py-2 text-gray-600 text-sm">
              {status.maskedValue}
            </div>
            {status.updatedAt && (
              <p className="text-xs text-gray-400">
                마지막 수정: {new Date(status.updatedAt).toLocaleString('ko-KR')}
                {status.updatedBy ? ` (${status.updatedBy})` : ''}
              </p>
            )}
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <Button variant="outline" size="sm" onClick={() => { setShowInput(!showInput); setError(''); setSuccess(''); }}>
            <KeyRound className="w-3 h-3 mr-1" />
            {status?.isSet ? '키 변경' : '키 등록'}
          </Button>
          {status?.isSet && (
            <Button variant="outline" size="sm" onClick={handleDelete} disabled={deleting}
              className="text-red-600 hover:text-red-700 hover:border-red-300">
              {deleting ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Trash2 className="w-3 h-3 mr-1" />}
              삭제
            </Button>
          )}
        </div>
      </div>

      {showInput && (
        <div className="rounded-lg border bg-white p-5 space-y-3">
          <p className="text-sm font-medium">새 API 키 입력</p>
          <p className="text-xs text-gray-500">
            <a href="https://console.anthropic.com/settings/keys" target="_blank" rel="noopener noreferrer"
              className="text-blue-600 hover:underline">Anthropic Console</a>에서 발급한 <code className="bg-gray-100 px-1 rounded text-xs">sk-ant-...</code> 형식의 키를 입력하세요.
          </p>
          <div className="relative">
            <Input
              type="password"
              placeholder="sk-ant-api03-..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              className="font-mono text-sm pr-10"
              onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); }}
            />
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={handleSave} disabled={saving || !inputValue.trim()}>
              {saving ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Save className="w-3 h-3 mr-1" />}
              저장
            </Button>
            <Button variant="outline" size="sm" onClick={() => { setShowInput(false); setInputValue(''); setError(''); }}>
              취소
            </Button>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      {success && <p className="text-sm text-green-600">{success}</p>}
    </div>
  );
}

export function AnthropicBaseUrlPanel() {
  const DEFAULT_URL = 'https://api.anthropic.com/v1';
  const [status, setStatus] = useState<ApiBaseUrlStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState('');
  const [showInput, setShowInput] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => { loadStatus(); }, []);

  const loadStatus = async () => {
    setLoading(true);
    try {
      const data = await getAnthropicBaseUrl();
      setStatus(data);
    } catch {
      setError('Base URL 상태를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const validateUrl = (url: string): string => {
    if (!url.trim()) return '';
    try {
      const u = new URL(url.trim());
      if (!['http:', 'https:'].includes(u.protocol)) return '올바른 URL을 입력하세요 (.../v1)';
      return '';
    } catch {
      return '올바른 URL을 입력하세요 (.../v1)';
    }
  };

  const urlError = validateUrl(inputValue);

  const handleSave = async () => {
    if (urlError) return;
    if (!inputValue.trim()) { setError('URL을 입력해주세요.'); return; }
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const data = await upsertAnthropicBaseUrl(inputValue.trim());
      setStatus(data);
      setInputValue('');
      setShowInput(false);
      setSuccess('API Base URL이 저장됩니다.');
    } catch {
      setError('저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!confirm(`기본값(${DEFAULT_URL})으로 초기화하시겠습니까?`)) return;
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await deleteAnthropicBaseUrl();
      setStatus({ isSet: false, value: DEFAULT_URL, updatedAt: null, updatedBy: null });
      setSuccess('기본값으로 초기화됩니다.');
    } catch {
      setError('초기화에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className="text-sm text-muted-foreground text-center py-4">로딩 중…</p>;

  const currentUrl = status?.value || DEFAULT_URL;

  return (
    <div className="space-y-4 max-w-xl">
      <div className="rounded-lg border bg-white p-5 space-y-3">
        <div className="flex items-center gap-2">
          <Zap className="w-4 h-4 text-amber-600" />
          <span className="font-medium text-sm">API Base URL</span>
          {status?.isSet ? (
            <span className="px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-medium">커스텀</span>
          ) : (
            <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">기본값</span>
          )}
        </div>

        <div className="font-mono bg-gray-50 rounded px-3 py-2 text-gray-600 text-sm break-all">
          {currentUrl}
        </div>

        {status?.updatedAt && (
          <p className="text-xs text-gray-400">
            마지막 수정: {new Date(status.updatedAt).toLocaleString('ko-KR')}
            {status.updatedBy ? ` (${status.updatedBy})` : ''}
          </p>
        )}

        <div className="flex gap-2 pt-1">
          <Button variant="outline" size="sm" onClick={() => {
            setInputValue(currentUrl);
            setShowInput(!showInput);
            setError('');
            setSuccess('');
          }}>
            <Zap className="w-3 h-3 mr-1" />
            URL 변경
          </Button>
          {status?.isSet && (
            <Button variant="outline" size="sm" onClick={handleReset} disabled={saving}
              className="text-gray-600 hover:text-gray-700">
              {saving ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : null}
              기본값으로 초기화
            </Button>
          )}
        </div>
      </div>

      {showInput && (
        <div className="rounded-lg border bg-white p-5 space-y-3">
          <p className="text-sm font-medium">API Base URL 변경</p>
          <p className="text-xs text-gray-500">
            <code className="bg-gray-100 px-1 rounded">.../v1</code> 형태로 입력하세요.{' '}
            <code className="bg-gray-100 px-1 rounded">/messages</code>는 자동으로 붙습니다.
            공식: <code className="bg-gray-100 px-1 rounded">https://api.anthropic.com/v1</code>
          </p>
          <div>
            <Input
              type="text"
              placeholder="https://api.anthropic.com/v1"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              className={`font-mono text-sm ${urlError ? 'border-red-400' : ''}`}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); }}
            />
            {urlError && <p className="text-xs text-red-500 mt-1">{urlError}</p>}
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={handleSave} disabled={saving || !!urlError || !inputValue.trim()}>
              {saving ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Save className="w-3 h-3 mr-1" />}
              저장
            </Button>
            <Button variant="outline" size="sm" onClick={() => { setShowInput(false); setInputValue(''); setError(''); }}>
              취소
            </Button>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      {success && <p className="text-sm text-green-600">{success}</p>}
    </div>
  );
}
