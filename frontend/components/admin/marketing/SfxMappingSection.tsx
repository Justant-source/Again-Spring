'use client';

import { useEffect, useRef, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  getSfxMapping,
  putSfxMapping,
  fetchSfxSampleBlob,
  SfxMapping,
  SfxEvent,
  SfxLibraryFile,
} from '@/lib/api/admin/marketing';

function extractError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: { data?: { error?: { message?: string }; message?: string; detail?: string } };
      message?: string;
    };
    const msg = anyErr.response?.data?.error?.message ||
                anyErr.response?.data?.message ||
                anyErr.response?.data?.detail ||
                anyErr.message;
    if (msg) return msg;
  }
  return String(err);
}

/** 카테고리 이름을 한글 레이블로 변환 */
function getCategoryLabel(category: string): string {
  const labels: Record<string, string> = {
    current: '현재',
    click: '클릭',
    whoosh: '화면 전환',
    page: '페이지 넘김',
    card: '카드',
    text: '텍스트',
    transition: '전환',
    other: '기타',
  };
  return labels[category] || category;
}

type EventUIState = SfxEvent;

export function SfxMappingSection() {
  const [mapping, setMapping] = useState<SfxMapping | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // UI 상태
  const [events, setEvents] = useState<EventUIState[]>([]);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [playing, setPlaying] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  useEffect(() => {
    if (typeof Audio !== 'undefined') audioRef.current = new Audio();
    const audio = audioRef.current;
    if (audio) audio.onended = () => setPlaying(null);
    return () => {
      if (audio) { audio.pause(); audio.removeAttribute('src'); }
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    };
  }, []);

  /** 어드민 인증이 필요한 스트림이라 blob 으로 받아 재생한다(BGM 미리듣기와 동일). */
  const handlePreview = async (path: string) => {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing === path) {
      audio.pause();
      setPlaying(null);
      return;
    }
    try {
      const blob = await fetchSfxSampleBlob(path);
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
      objectUrlRef.current = URL.createObjectURL(blob);
      audio.src = objectUrlRef.current;
      audio.currentTime = 0;
      await audio.play();
      setPlaying(path);
    } catch (err: unknown) {
      setSaveError(`미리듣기에 실패했습니다: ${extractError(err)}`);
      setPlaying(null);
    }
  };

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getSfxMapping();
      setMapping(data);
      setEvents(data.events.map((e) => ({ ...e })));
      if (data.events.length > 0) {
        setSelectedKey(data.events[0].key);
      }
    } catch (err: unknown) {
      setError(`효과음 설정을 불러오지 못했습니다: ${extractError(err)}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleEventSelect = (key: string) => {
    setSelectedKey(key);
  };

  const handleFileSelect = (eventKey: string, file: SfxLibraryFile) => {
    setEvents((prevEvents) =>
      prevEvents.map((e) =>
        e.key === eventKey
          ? { ...e, file: file.name }
          : e
      )
    );
  };

  const handleVolumeChange = (eventKey: string, volume: number) => {
    const clamped = Math.min(1.5, Math.max(0, volume));
    setEvents((prevEvents) =>
      prevEvents.map((e) =>
        e.key === eventKey
          ? { ...e, volume: clamped }
          : e
      )
    );
  };

  const handleOffsetChange = (eventKey: string, offset: number) => {
    const clamped = Math.min(10, Math.max(-5, offset));
    setEvents((prevEvents) =>
      prevEvents.map((e) =>
        e.key === eventKey
          ? { ...e, offset: clamped }
          : e
      )
    );
  };

  const handleClearEvent = (eventKey: string) => {
    setEvents((prevEvents) =>
      prevEvents.map((e) =>
        e.key === eventKey
          ? { ...e, file: '' }
          : e
      )
    );
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      // library 제외하고 events와 maxPerVideo만 전송
      const { library, ...rest } = mapping || { events: [], maxPerVideo: 0 };
      await putSfxMapping({
        events: events.map((e) => ({
          key: e.key,
          file: e.file,
          volume: e.volume,
          offset: e.offset,
        })),
        maxPerVideo: rest.maxPerVideo,
      });
      setSaved(true);
      await load();
    } catch (err: unknown) {
      setSaveError(`저장에 실패했습니다: ${extractError(err)}`);
    } finally {
      setSaving(false);
    }
  };

  const selectedEvent = events.find((e) => e.key === selectedKey);
  const hasChanges = JSON.stringify(events) !== JSON.stringify(mapping?.events || []);

  return (
    <div data-testid="marketing-sfx-mapping-section">
      <div className="mb-4 flex items-center justify-between gap-4">
        <div>
          <h3 className="font-semibold text-gray-800">효과음 매핑</h3>
          <p className="mt-1 text-sm text-gray-500">
            숏폼 영상의 각 지점별로 효과음을 지정합니다. 음원은 오른쪽 라이브러리에서 선택하고,
            음량과 시간 오프셋을 조절할 수 있습니다.
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
          <div className="grid grid-cols-3 gap-4">
            {/* 왼쪽: 지점 목록 */}
            <div className="border-r pr-4">
              <h4 className="mb-3 text-sm font-semibold text-gray-700">삽입 지점</h4>
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {events.map((event) => (
                  <button
                    key={event.key}
                    type="button"
                    onClick={() => handleEventSelect(event.key)}
                    className={`w-full rounded border px-3 py-2 text-left text-sm transition ${
                      selectedKey === event.key
                        ? 'border-gray-800 bg-gray-100 font-medium text-gray-800'
                        : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                    }`}
                  >
                    <div className="font-medium">{event.key}</div>
                    {event.file && (
                      <div className="mt-0.5 text-xs text-gray-500">{event.file}</div>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* 중간: 선택된 지점 설정 */}
            <div className="border-r pr-4">
              <h4 className="mb-3 text-sm font-semibold text-gray-700">설정</h4>
              {selectedEvent ? (
                <div className="space-y-4">
                  {/* 현재 파일 표시 */}
                  <div>
                    <label className="block text-xs font-medium text-gray-600">현재 음원</label>
                    <div className="mt-1 rounded bg-gray-50 px-2 py-2">
                      <div className="text-xs text-gray-600">
                        {selectedEvent.file || <span className="italic text-gray-400">미지정</span>}
                      </div>
                    </div>
                  </div>

                  {/* 음량 */}
                  <div>
                    <label className="block text-xs font-medium text-gray-600">
                      음량: {selectedEvent.volume.toFixed(2)}
                    </label>
                    <input
                      type="range"
                      min="0"
                      max="1.5"
                      step="0.05"
                      value={selectedEvent.volume}
                      onChange={(e) => handleVolumeChange(selectedEvent.key, parseFloat(e.target.value))}
                      className="mt-1 w-full"
                    />
                    <div className="mt-1 text-xs text-gray-500">0 ~ 1.5</div>
                  </div>

                  {/* 오프셋 */}
                  <div>
                    <label className="block text-xs font-medium text-gray-600">
                      오프셋 (초): {selectedEvent.offset.toFixed(1)}
                    </label>
                    <input
                      type="range"
                      min="-5"
                      max="10"
                      step="0.1"
                      value={selectedEvent.offset}
                      onChange={(e) => handleOffsetChange(selectedEvent.key, parseFloat(e.target.value))}
                      className="mt-1 w-full"
                    />
                    <div className="mt-1 text-xs text-gray-500">-5 ~ 10초</div>
                  </div>

                  {/* 지우기 */}
                  {selectedEvent.file && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="w-full text-red-600 hover:bg-red-50"
                      onClick={() => handleClearEvent(selectedEvent.key)}
                    >
                      지우기
                    </Button>
                  )}
                </div>
              ) : (
                <div className="text-center text-sm text-gray-400">지점을 선택하세요</div>
              )}
            </div>

            {/* 오른쪽: 라이브러리 */}
            <div>
              <h4 className="mb-3 text-sm font-semibold text-gray-700">음원 라이브러리</h4>
              <div className="space-y-3 max-h-96 overflow-y-auto">
                {mapping?.library && mapping.library.length > 0 ? (
                  mapping.library.map((category) => (
                    <div key={category.category}>
                      <h5 className="text-xs font-semibold text-gray-600 mb-2">
                        {getCategoryLabel(category.category)}
                      </h5>
                      <div className="space-y-1">
                        {category.files.map((file) => (
                          <div key={file.path} className="flex items-center gap-1">
                            {/* 미리듣기 — 지점 선택과 무관하게 언제나 들어볼 수 있어야
                                비교가 된다. 듣지 않고 이름만 보고 고를 수는 없다. */}
                            <button
                              type="button"
                              title="미리듣기"
                              onClick={() => handlePreview(file.path)}
                              className={`shrink-0 rounded border px-2 py-1.5 text-xs transition ${
                                playing === file.path
                                  ? 'border-[#5F8F76] bg-[#E8EDE4] text-[#5F8F76]'
                                  : 'border-gray-200 bg-white text-gray-500 hover:bg-gray-50'
                              }`}
                            >
                              {playing === file.path ? '■' : '▶'}
                            </button>
                          <button
                            type="button"
                            onClick={() => {
                              if (selectedEvent) {
                                handleFileSelect(selectedEvent.key, file);
                              }
                            }}
                            disabled={!selectedEvent}
                            className={`block w-full rounded border px-2 py-1.5 text-left text-xs transition ${
                              !selectedEvent
                                ? 'border-gray-200 bg-gray-50 text-gray-400 cursor-not-allowed'
                                : selectedEvent.file === file.name
                                  ? 'border-green-400 bg-green-50 text-green-700 font-medium'
                                  : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                            }`}
                          >
                            {file.name}
                          </button>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="text-center text-xs text-gray-400">음원이 없습니다</div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 저장 버튼 및 상태 */}
        <div className="mt-6 border-t pt-4">
          {saveError && (
            <div className="mb-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {saveError}
            </div>
          )}

          <div className="flex items-center gap-3">
            <Button
              size="sm"
              onClick={handleSave}
              disabled={saving || !hasChanges}
            >
              {saving ? '저장 중…' : '저장'}
            </Button>
            {saved && !saving && (
              <span className="text-xs text-green-700">저장됨</span>
            )}
            {!hasChanges && !saved && (
              <span className="text-xs text-gray-400">변경사항 없음</span>
            )}
            {mapping && (
              <span className="text-xs text-gray-400">
                최대 {mapping.maxPerVideo}개 / 영상
              </span>
            )}
          </div>
        </div>
      </Card>
    </div>
  );
}
