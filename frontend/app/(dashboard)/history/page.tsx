'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { api } from '@/lib/api/client';
import type { RelationType } from '@/lib/types';
// V47~: CATEGORIES import 제거 (중·소분류 칩 렌더링 삭제됨)

interface HistoryItem {
  id: string;
  status: string;
  partnerNickname: string | null;
  relationType: RelationType | null;
  soloMode: boolean;
  completedAt: string | null;
  createdAt: string;
  /** V47 신규: 자동 생성 제목 (사용자 수정 가능). */
  title?: string | null;
  /** V47 신규: 추론 핵심 키워드 최대 2개. */
  keywords?: string[] | null;
  /** V47 신규: 한국 특화 태그. */
  koreanTag?: string | null;
  reportId: string | null;
  testRun?: boolean;
}

const RELATION_TYPE_LABEL: Record<RelationType, string> = {
  couple: '연인',
  marriage: '부부',
  friend: '친구·지인',
  family: '가족',
  parent_child: '부모·자식',
  korean_specific: '한국 특화',
  work: '직장',
};

// V47~: 중·소분류 제거 — 키워드 칩은 item.keywords 배열에서 직접 렌더링

const ACTIVE_STATUSES = new Set(['chatting_solo', 'chatting_duo', 'awaiting_finalization', 'waiting_b']);
const isActive = (status: string) => ACTIVE_STATUSES.has(status);

export default function HistoryPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(true);

  // 멀티셀렉트
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  // 삭제 확인
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!hasHydrated) return;
    if (!user) {
      router.push('/login');
      return;
    }
    if (user.isGuest) {
      setLoading(false);
      return;
    }
    const fetchHistory = async () => {
      try {
        const res = await api.get('/api/users/me/history');
        setHistory(res.data || []);
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, [user, router]);

  const toggleSelect = (id: string) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const exitSelectMode = () => {
    setSelectMode(false);
    setSelected(new Set());
  };

  const handleDeleteSelected = async () => {
    if (selected.size === 0) return;
    setDeleting(true);
    const ids = Array.from(selected);
    await Promise.allSettled(ids.map(id => api.delete(`/api/sessions/${id}`)));
    setHistory(prev => prev.filter(item => !selected.has(item.id)));
    setDeleting(false);
    setShowDeleteConfirm(false);
    exitSelectMode();
  };

  if (!user) return null;

  if (loading) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="대화기록" tone="L" onBack={() => router.push('/')} />
        <div style={{ padding: '28px', textAlign: 'center', color: 'var(--L-sub)' }}>
          로딩 중...
        </div>
      </PhoneFrame>
    );
  }

  if (user.isGuest) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="대화기록" tone="L" onBack={() => router.push('/')} />
        <div style={{ padding: '28px 28px 40px', display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ textAlign: 'center', marginTop: 40 }}>
            <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginBottom: 16 }}>
              게스트 모드에서는<br />이력이 저장되지 않아요.
            </div>
            <div style={{ fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              회원가입 후 모든 대화를<br />저장하고 언제든 다시 볼 수 있어요.
            </div>
          </div>
          <button onClick={() => router.push('/signup')} className="btn-L" style={{ width: '100%', marginTop: 24 }}>
            회원가입 하기
          </button>
        </div>
      </PhoneFrame>
    );
  }

  if (history.length === 0) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="대화기록" tone="L" onBack={() => router.push('/')} />
        <div style={{ padding: '28px 28px 40px', display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ textAlign: 'center', marginTop: 40 }}>
            <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginBottom: 16 }}>
              아직 기록된<br />대화가 없어요.
            </div>
            <div style={{ fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              첫 이야기를 시작해보세요.
            </div>
          </div>
          <button onClick={() => router.push('/session/new')} className="btn-L" style={{ width: '100%', marginTop: 24 }}>
            이야기 시작하기
          </button>
        </div>
      </PhoneFrame>
    );
  }

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title={selectMode ? `${selected.size}개 선택됨` : '대화기록'}
        tone="L"
        back={!selectMode}
        onBack={selectMode ? undefined : () => router.push('/')}
        right={
          selectMode ? (
            <button
              onClick={exitSelectMode}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--L-ink)', fontSize: 14, padding: '4px 8px' }}
            >
              취소
            </button>
          ) : (
            <button
              onClick={() => setSelectMode(true)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--L-sub)', fontSize: 13, padding: '4px 8px' }}
            >
              삭제
            </button>
          )
        }
      />

      <div style={{ flex: 1, overflowY: 'auto', padding: '8px 28px', paddingBottom: selectMode ? 80 : 40, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {history.map((item, idx) => {
          const active = isActive(item.status);
          const dateStr = (item.completedAt ?? item.createdAt)
            ? new Date(item.completedAt ?? item.createdAt).toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
              })
            : '';
          const isChecked = selected.has(item.id);

          const handleClick = () => {
            if (selectMode) {
              if (!item.testRun) toggleSelect(item.id);
              return;
            }
            if (active) {
              router.push(`/session/chat/${item.id}`);
            } else {
              router.push(`/session/history/${item.id}`);
            }
          };

          return (
            <div
              key={idx}
              className="letter-card"
              onClick={handleClick}
              style={{
                cursor: 'pointer',
                padding: '14px 16px',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                outline: selectMode && isChecked ? '2px solid var(--L-point)' : 'none',
                borderRadius: 12,
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>{dateStr}</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {item.testRun && (
                      <span style={{
                        fontSize: 10,
                        background: '#7C6A5A',
                        color: '#fff',
                        borderRadius: 4,
                        padding: '2px 7px',
                        fontWeight: 500,
                      }}>
                        시뮬레이션
                      </span>
                    )}
                    {!item.testRun && active ? (
                      <span style={{
                        fontSize: 10,
                        background: 'var(--L-point)',
                        color: '#fff',
                        borderRadius: 4,
                        padding: '2px 7px',
                      }}>
                        진행 중
                      </span>
                    ) : !item.testRun && item.completedAt ? (
                      <span style={{
                        fontSize: 10,
                        background: 'var(--L-sub)',
                        color: '#fff',
                        borderRadius: 4,
                        padding: '2px 7px',
                      }}>
                        완료
                      </span>
                    ) : null}
                    {!item.testRun && !active && item.status === 'completed' && !item.reportId && (
                      <span style={{
                        fontSize: 10,
                        padding: '2px 7px',
                        background: 'color-mix(in srgb, var(--L-sub) 30%, transparent)',
                        color: 'var(--L-sub)',
                        borderRadius: 4,
                        fontWeight: 500,
                      }}>
                        결과 생성중
                      </span>
                    )}
                    {!item.testRun && !active && item.reportId && (
                      <button
                        onClick={e => {
                          e.stopPropagation();
                          router.push(`/session/result/${item.id}`);
                        }}
                        style={{
                          fontSize: 10,
                          padding: '2px 7px',
                          background: 'var(--L-point)',
                          color: '#fff',
                          border: 'none',
                          borderRadius: 4,
                          cursor: 'pointer',
                          fontWeight: 500,
                        }}
                      >
                        결과 보기
                      </button>
                    )}
                  </div>
                </div>
                {/* V47~: 제목 — item.title 우선, 없으면 관계 기반 fallback */}
                <div className="serif" style={{ fontSize: 15, color: 'var(--L-ink)', fontWeight: 500, marginBottom: 8 }}>
                  {item.testRun
                    ? 'AI 시뮬레이션 대화'
                    : item.title
                      ? item.title
                      : item.soloMode
                        ? '혼자 정리한 이야기'
                        : item.partnerNickname
                          ? `${item.partnerNickname}분과의 대화`
                          : '상대방과의 대화'}
                </div>
                {/* V47~: [대분류] · 키워드1 · 키워드2 */}
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                  {item.relationType && (
                    <span style={{
                      fontSize: '11px',
                      background: 'var(--L-card)',
                      border: '1px solid var(--L-border)',
                      borderRadius: '3px',
                      padding: '4px 8px',
                      color: 'var(--L-sub)',
                    }}>
                      {RELATION_TYPE_LABEL[item.relationType] ?? item.relationType}
                    </span>
                  )}
                  {item.keywords && item.keywords.length > 0
                    ? item.keywords.map((kw, idx) => (
                        <span key={idx} style={{
                          fontSize: '11px',
                          background: 'var(--L-card)',
                          border: '1px solid var(--L-border)',
                          borderRadius: '3px',
                          padding: '4px 8px',
                          color: 'var(--L-sub)',
                        }}>
                          {kw}
                        </span>
                      ))
                    : null}
                </div>
              </div>

              {/* 체크박스 — 시뮬레이션 세션은 삭제 불가 */}
              {selectMode && !item.testRun && (
                <div style={{
                  flexShrink: 0,
                  width: 22,
                  height: 22,
                  borderRadius: '50%',
                  border: `2px solid ${isChecked ? 'var(--L-point)' : 'var(--L-border)'}`,
                  background: isChecked ? 'var(--L-point)' : 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  transition: 'all 0.15s',
                }}>
                  {isChecked && (
                    <svg width="12" height="10" viewBox="0 0 12 10" fill="none">
                      <path d="M1 5L4.5 8.5L11 1" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 하단 선택 모드 액션바 */}
      {selectMode && (
        <div style={{
          position: 'fixed',
          bottom: 0,
          left: '50%',
          transform: 'translateX(-50%)',
          width: '100%',
          maxWidth: 420,
          padding: '12px 24px 20px',
          background: 'var(--L-bg)',
          borderTop: '1px solid var(--L-border)',
          display: 'flex',
          gap: 10,
          zIndex: 100,
        }}>
          <button
            onClick={() => {
              if (selected.size === history.length) {
                setSelected(new Set());
              } else {
                setSelected(new Set(history.map(h => h.id)));
              }
            }}
            style={{
              flex: 1,
              padding: '12px',
              background: 'var(--L-card)',
              border: '1px solid var(--L-border)',
              borderRadius: 10,
              fontSize: 13,
              cursor: 'pointer',
              color: 'var(--L-ink)',
            }}
          >
            {selected.size === history.length ? '전체 해제' : '전체 선택'}
          </button>
          <button
            onClick={() => selected.size > 0 && setShowDeleteConfirm(true)}
            disabled={selected.size === 0}
            style={{
              flex: 1,
              padding: '12px',
              background: selected.size > 0 ? '#e84c4c' : 'var(--L-card)',
              border: 'none',
              borderRadius: 10,
              fontSize: 13,
              cursor: selected.size > 0 ? 'pointer' : 'not-allowed',
              color: selected.size > 0 ? '#fff' : 'var(--L-sub)',
              transition: 'all 0.15s',
            }}
          >
            {selected.size > 0 ? `${selected.size}개 삭제` : '삭제'}
          </button>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {showDeleteConfirm && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 999,
          }}
          onClick={() => !deleting && setShowDeleteConfirm(false)}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              background: 'var(--L-bg)',
              borderRadius: 16,
              padding: '24px 20px',
              width: 'min(320px, 85vw)',
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
          >
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--L-ink)', textAlign: 'center' }}>
              {selected.size}개의 대화를 삭제할까요?
            </div>
            <div style={{ fontSize: 13, color: 'var(--L-sub)', textAlign: 'center', lineHeight: 1.6 }}>
              삭제하면 대화 내용과 리포트를<br />다시 볼 수 없어요.
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
                style={{
                  flex: 1,
                  padding: '12px',
                  background: 'var(--L-card)',
                  border: '1px solid var(--L-border)',
                  borderRadius: 10,
                  fontSize: 14,
                  cursor: 'pointer',
                  color: 'var(--L-ink)',
                }}
              >
                취소
              </button>
              <button
                onClick={handleDeleteSelected}
                disabled={deleting}
                style={{
                  flex: 1,
                  padding: '12px',
                  background: '#e84c4c',
                  border: 'none',
                  borderRadius: 10,
                  fontSize: 14,
                  cursor: deleting ? 'not-allowed' : 'pointer',
                  color: '#fff',
                  opacity: deleting ? 0.6 : 1,
                }}
              >
                {deleting ? '삭제 중...' : '삭제'}
              </button>
            </div>
          </div>
        </div>
      )}
    </PhoneFrame>
  );
}
