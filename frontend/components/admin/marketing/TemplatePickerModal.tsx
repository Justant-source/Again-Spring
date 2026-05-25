'use client';

import { useEffect, useState } from 'react';
import { getTemplates, generateFromTemplate, type Template } from '@/lib/api/marketing/templateApi';
import { getSimulations, type SimulationSummaryResponse } from '@/lib/api/marketing/simulationApi';

interface Props {
  onClose: () => void;
  onGenerated: () => void;
}

const PLATFORMS = ['X', 'INSTAGRAM', 'NAVER_BLOG', 'THREADS', 'FACEBOOK'];
const PLATFORM_LABELS: Record<string, string> = { X: 'X', INSTAGRAM: 'Instagram', NAVER_BLOG: '네이버블로그', THREADS: 'Threads', FACEBOOK: 'Facebook' };

function extractVariables(template: string): string[] {
  const matches = template.match(/\$\{([^}]+)\}/g) ?? [];
  return [...new Set(matches.map(m => m.slice(2, -1)))];
}

export function TemplatePickerModal({ onClose, onGenerated }: Props) {
  const [step, setStep] = useState<'template' | 'variables'>('template');
  const [templates, setTemplates] = useState<Template[]>([]);
  const [sims, setSims] = useState<SimulationSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
  const [selectedSimId, setSelectedSimId] = useState<number | null>(null);
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([getTemplates(undefined, true), getSimulations('COMPLETED')])
      .then(([tmpl, simList]) => { setTemplates(tmpl); setSims(simList); })
      .catch(() => setError('데이터 로딩 실패'))
      .finally(() => setLoading(false));
  }, []);

  function handleSelectTemplate(t: Template) {
    setSelectedTemplate(t);
    const vars = extractVariables(t.bodyTemplate);
    const init: Record<string, string> = {};
    vars.forEach(v => { init[v] = ''; });
    setVariables(init);
    if (vars.length > 0) setStep('variables');
    else setStep('variables');
  }

  async function handleGenerate() {
    if (!selectedTemplate || !selectedSimId) { setError('템플릿과 시뮬레이션을 선택해주세요.'); return; }
    setGenerating(true);
    setError('');
    try {
      await generateFromTemplate(selectedTemplate.id, selectedSimId, undefined, variables);
      onGenerated();
      onClose();
    } catch (e: any) {
      setError(e.response?.data?.message ?? '생성 실패');
    } finally { setGenerating(false); }
  }

  const varKeys = selectedTemplate ? extractVariables(selectedTemplate.bodyTemplate) : [];

  return (
    <div
      style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'white', borderRadius: 12, padding: 24, maxWidth: 480, width: '90%', maxHeight: '80vh', overflowY: 'auto', boxShadow: '0 10px 40px rgba(0,0,0,0.2)' }}
        onClick={e => e.stopPropagation()}
      >
        <h3 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 600, color: '#1A1A2E' }}>
          {step === 'template' ? '템플릿 선택' : '변수 입력'}
        </h3>

        {error && <p style={{ color: '#b33333', fontSize: 13, marginBottom: 12 }}>{error}</p>}

        {loading ? (
          <p style={{ color: '#aaa', fontSize: 13 }}>불러오는 중...</p>
        ) : step === 'template' ? (
          <>
            {templates.length === 0 ? (
              <p style={{ color: '#aaa', fontSize: 13 }}>활성화된 템플릿이 없습니다.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                {templates.map(t => (
                  <div
                    key={t.id}
                    onClick={() => handleSelectTemplate(t)}
                    style={{ padding: '12px 14px', border: '1px solid', borderColor: selectedTemplate?.id === t.id ? '#1A1A2E' : '#e7e3d8', borderRadius: 8, cursor: 'pointer', background: selectedTemplate?.id === t.id ? '#f0f2f8' : 'white' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
                      <span style={{ fontSize: 11, fontWeight: 600, padding: '1px 6px', background: '#1A1A2E', color: 'white', borderRadius: 3 }}>
                        {PLATFORM_LABELS[t.platform] ?? t.platform}
                      </span>
                      <span style={{ fontSize: 13, fontWeight: 500, color: '#1A1A2E' }}>{t.name}</span>
                    </div>
                    <p style={{ margin: 0, fontSize: 11, color: '#888', overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>
                      {t.bodyTemplate.slice(0, 60)}...
                    </p>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : (
          <>
            <div style={{ marginBottom: 16 }}>
              <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 6 }}>시뮬레이션 선택</label>
              <div style={{ maxHeight: 140, overflowY: 'auto', border: '1px solid #eee', borderRadius: 6 }}>
                {sims.map(s => (
                  <div key={s.id} onClick={() => setSelectedSimId(s.id)}
                    style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f0f0f0', background: selectedSimId === s.id ? '#f0f4ff' : 'white', borderLeft: selectedSimId === s.id ? '3px solid #2d4a7a' : '3px solid transparent' }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>#{s.id} · 사연 #{s.storyId}</div>
                    <div style={{ fontSize: 11, color: '#888' }}>{s.turnCount}턴</div>
                  </div>
                ))}
              </div>
            </div>

            {varKeys.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, fontWeight: 600, color: '#666', display: 'block', marginBottom: 8 }}>변수 입력</label>
                {varKeys.map(k => (
                  <div key={k} style={{ marginBottom: 8 }}>
                    <label style={{ fontSize: 11, color: '#888', display: 'block', marginBottom: 3 }}>{k}</label>
                    <input type="text" value={variables[k] ?? ''} onChange={e => setVariables(prev => ({ ...prev, [k]: e.target.value }))}
                      style={{ width: '100%', padding: '7px 10px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 8 }}>
          {step === 'variables' && (
            <button onClick={() => setStep('template')} style={{ padding: '8px 14px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
              이전
            </button>
          )}
          <button onClick={onClose} style={{ padding: '8px 14px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>취소</button>
          {step === 'variables' && (
            <button onClick={handleGenerate} disabled={generating || !selectedSimId}
              style={{ padding: '8px 16px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: (generating || !selectedSimId) ? 'not-allowed' : 'pointer', fontSize: 13, opacity: (generating || !selectedSimId) ? 0.6 : 1 }}>
              {generating ? '생성 중...' : '생성'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
