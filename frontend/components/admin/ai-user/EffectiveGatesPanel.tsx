'use client';

import { useState, useEffect, useCallback } from 'react';
import { Badge } from '@/components/ui/badge';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getEffectiveGates,
  type EffectiveGates,
} from '@/lib/api/admin/ai-user';

/**
 * env/yml/DB/LLM 게이트를 orchestrator가 한 번에 해석한 결과를 보여준다.
 * `allOff` 배지(kill switch + 4 provider)는 이 화면에서 여전히 보이지만
 * yml/env 게이트나 LLM 게이트가 막고 있는 경우를 드러내지 못한다 — 이 패널이 그 공백을 채운다.
 * 엔드포인트가 아직 없거나(404) 오류가 나면 페이지를 깨뜨리지 않고 안내 문구로 대체한다.
 */
export function EffectiveGatesPanel({ className }: { className?: string }) {
  const [gates, setGates] = useState<EffectiveGates | null>(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getEffectiveGates();
      setGates(result);
      setFailed(false);
    } catch (e) {
      setGates(null);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <AdminSection
      title="게이트 해석"
      subtitle="env·yml·DB·LLM 게이트를 orchestrator가 한 번에 해석한 결과. 왜 생성/발행이 막혀있는지 여기서 확인한다."
      className={className}
    >
      <div data-testid="ai-user-gates-panel" className="px-1 space-y-4">
        {loading && (
          <div className="text-sm text-gray-400" data-testid="ai-user-gates-loading">
            불러오는 중...
          </div>
        )}

        {!loading && failed && (
          <div
            className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2.5 text-sm text-gray-500"
            data-testid="ai-user-gates-error"
          >
            게이트 상태를 불러올 수 없음
          </div>
        )}

        {!loading && !failed && gates && (
          <>
            <div className="flex items-center gap-2">
              <Badge
                className={
                  gates.generationAllowed
                    ? 'bg-emerald-100 text-emerald-700 border-emerald-200'
                    : 'bg-red-100 text-red-700 border-red-200'
                }
                data-testid="ai-user-gate-generation"
              >
                생성 {gates.generationAllowed ? '열림' : '막힘'}
              </Badge>
              <Badge
                className={
                  gates.publishingAllowed
                    ? 'bg-emerald-100 text-emerald-700 border-emerald-200'
                    : 'bg-red-100 text-red-700 border-red-200'
                }
                data-testid="ai-user-gate-publishing"
              >
                발행 {gates.publishingAllowed ? '열림' : '막힘'}
              </Badge>
            </div>

            <p
              className="text-xs text-gray-400"
              data-testid="ai-user-gates-caveat"
            >
              요약 판정입니다. 워크로드별 실제 동작은 gates 표와 각 스케줄러 로그를 기준으로 확인하세요.
            </p>

            {gates.reasons.length > 0 && (
              <ul
                className="list-disc pl-5 space-y-1 text-sm text-gray-600"
                data-testid="ai-user-gate-reasons"
              >
                {gates.reasons.map((reason, i) => (
                  <li key={i}>{reason}</li>
                ))}
              </ul>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-sm" data-testid="ai-user-gate-table">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left px-3 py-2 font-medium text-gray-700">이름</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-700">출처</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-700">값</th>
                    <th className="text-left px-3 py-2 font-medium text-gray-700">막는 것</th>
                  </tr>
                </thead>
                <tbody>
                  {gates.gates.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="text-center py-6 text-gray-400">
                        데이터 없음
                      </td>
                    </tr>
                  ) : (
                    gates.gates.map(gate => (
                      <tr key={gate.name} className="border-b border-gray-100">
                        <td className="px-3 py-2 font-medium text-gray-800">{gate.name}</td>
                        <td className="px-3 py-2 text-gray-500">{gate.source}</td>
                        <td className="px-3 py-2 text-gray-700">{String(gate.value)}</td>
                        <td className="px-3 py-2 text-gray-500">{gate.blocks || '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </AdminSection>
  );
}
