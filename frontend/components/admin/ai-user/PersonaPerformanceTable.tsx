'use client';

import { useState, useEffect, useCallback } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ArrowUp, ArrowDown } from 'lucide-react';
import {
  getPersonaPerformance,
  type PersonaPerformanceDto,
} from '@/lib/api/admin/ai-user';

type SortKey = 'actionsCompleted' | 'failureRate' | null;
type SortDir = 'asc' | 'desc';
type RangeType = '24h' | '7d';

export function PersonaPerformanceTable({ className }: { className?: string }) {
  const [personas, setPersonas] = useState<PersonaPerformanceDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<RangeType>('24h');
  const [sortKey, setSortKey] = useState<SortKey>('actionsCompleted');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const fetchPersonas = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getPersonaPerformance(range);
      setPersonas(result);
    } catch (e) {
      console.error('Failed to fetch persona performance:', e);
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    fetchPersonas();
  }, [fetchPersonas]);

  const getTierColor = (tier: string | null) => {
    if (!tier) return 'bg-gray-100 text-gray-700 border-gray-200';
    const colors: Record<string, string> = {
      HEAVY: 'bg-purple-100 text-purple-700 border-purple-200',
      STANDARD: 'bg-blue-100 text-blue-700 border-blue-200',
      LIGHT: 'bg-gray-100 text-gray-700 border-gray-200',
    };
    return colors[tier] || colors.STANDARD;
  };

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  };

  const sortedPersonas = [...personas].sort((a, b) => {
    if (!sortKey) return 0;
    const aVal = a[sortKey];
    const bVal = b[sortKey];
    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return sortDir === 'asc' ? aVal - bVal : bVal - aVal;
    }
    return 0;
  });

  const SortIcon = ({ columnKey }: { columnKey: SortKey }) => {
    if (sortKey !== columnKey) return null;
    return sortDir === 'asc' ? (
      <ArrowUp className="h-3 w-3 inline ml-1" />
    ) : (
      <ArrowDown className="h-3 w-3 inline ml-1" />
    );
  };

  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-6 ${className || ''}`}>
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-800">페르소나 성과</h3>
        <div className="flex gap-2">
          {(['24h', '7d'] as RangeType[]).map(r => (
            <Button
              key={r}
              variant={range === r ? 'default' : 'outline'}
              size="sm"
              onClick={() => setRange(r)}
              className="text-xs"
            >
              {r === '24h' ? '24시간' : '7일'}
            </Button>
          ))}
        </div>
      </div>

      {/* 테이블 */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm" data-testid="ai-persona-performance">
          <thead>
            <tr className="border-b border-gray-200">
              <th className="text-left px-3 py-2 font-medium text-gray-700">이름</th>
              <th className="text-left px-3 py-2 font-medium text-gray-700">Tier</th>
              <th className="text-left px-3 py-2 font-medium text-gray-700">상태</th>
              <th
                className="text-left px-3 py-2 font-medium text-gray-700 cursor-pointer hover:bg-gray-50"
                onClick={() => handleSort('actionsCompleted')}
              >
                행동 수 <SortIcon columnKey="actionsCompleted" />
              </th>
              <th
                className="text-left px-3 py-2 font-medium text-gray-700 cursor-pointer hover:bg-gray-50"
                onClick={() => handleSort('failureRate')}
              >
                실패율 <SortIcon columnKey="failureRate" />
              </th>
              <th className="text-left px-3 py-2 font-medium text-gray-700">실유저 반응</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="text-center py-8 text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            ) : sortedPersonas.length === 0 ? (
              <tr>
                <td colSpan={6} className="text-center py-8 text-gray-400">
                  데이터 없음
                </td>
              </tr>
            ) : (
              sortedPersonas.map(persona => (
                <tr
                  key={persona.personaId}
                  className={`border-b border-gray-100 hover:bg-gray-50 ${!persona.active ? 'opacity-50' : ''}`}
                >
                  <td className="px-3 py-3 font-medium text-gray-800">
                    {persona.nickname || persona.personaId}
                  </td>
                  <td className="px-3 py-3">
                    {persona.tier ? (
                      <Badge className={`${getTierColor(persona.tier)} text-xs border`}>
                        {persona.tier}
                      </Badge>
                    ) : (
                      <span className="text-gray-400">-</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <Badge
                      className={`${
                        persona.active
                          ? 'bg-green-100 text-green-700 border-green-200'
                          : 'bg-gray-100 text-gray-500 border-gray-200'
                      } text-xs border`}
                    >
                      {persona.active ? '활성' : '비활성'}
                    </Badge>
                  </td>
                  <td className="px-3 py-3 text-gray-700">
                    {persona.actionsCompleted.toLocaleString()}
                  </td>
                  <td className="px-3 py-3">
                    <span
                      className={
                        persona.failureRate > 0.1
                          ? 'text-red-600 font-semibold'
                          : 'text-gray-700'
                      }
                    >
                      {(persona.failureRate * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-3 py-3 text-gray-700">
                    {persona.realUserReactions.toLocaleString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
