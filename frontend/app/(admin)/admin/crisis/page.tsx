'use client';

import { useState, useEffect, useCallback } from 'react';
import { getCrisisRecent } from '@/lib/api/admin';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { RefreshCw, AlertTriangle, ShieldAlert } from 'lucide-react';

export default function CrisisMonitorPage() {
  const [messages, setMessages] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getCrisisRecent(50);
      setMessages(data);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to load crisis messages', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [load]);

  const getLevelBadge = (level: string) => {
    switch (level?.toUpperCase()) {
      case 'HIGH':
        return <Badge variant="destructive">HIGH</Badge>;
      case 'MEDIUM':
        return <Badge className="bg-orange-500 text-white">MEDIUM</Badge>;
      default:
        return <Badge variant="secondary">{level || 'LOW'}</Badge>;
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldAlert className="text-orange-500" size={22} />
          <h1 className="text-xl font-semibold">위기 모니터링</h1>
          <Badge variant="secondary" className="text-xs">
            30초마다 자동 갱신
          </Badge>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw size={14} className={`mr-2 ${loading ? 'animate-spin' : ''}`} />
          새로고침
        </Button>
      </div>

      {lastUpdated && (
        <p className="text-xs text-gray-500">
          마지막 갱신: {lastUpdated.toLocaleTimeString('ko-KR')}
        </p>
      )}

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <AlertTriangle size={16} className="text-orange-500" />
            위기 감지 이벤트
            <span className="text-sm font-normal text-gray-500">
              ({messages.length}건)
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading && messages.length === 0 ? (
            <p className="text-sm text-gray-500 py-4 text-center">불러오는 중...</p>
          ) : messages.length === 0 ? (
            <p className="text-sm text-gray-500 py-8 text-center">
              감지된 위기 이벤트가 없습니다.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2 px-3 font-medium text-gray-600">레벨</th>
                    <th className="text-left py-2 px-3 font-medium text-gray-600">세션 ID</th>
                    <th className="text-left py-2 px-3 font-medium text-gray-600">발신자</th>
                    <th className="text-left py-2 px-3 font-medium text-gray-600">글자수</th>
                    <th className="text-left py-2 px-3 font-medium text-gray-600">발생 시각</th>
                  </tr>
                </thead>
                <tbody>
                  {messages.map((msg, idx) => (
                    <tr key={idx} className="border-b last:border-0 hover:bg-gray-50">
                      <td className="py-2 px-3">{getLevelBadge(msg.level)}</td>
                      <td className="py-2 px-3 font-mono text-xs text-gray-600 max-w-[120px] truncate">
                        {msg.sessionId || '-'}
                      </td>
                      <td className="py-2 px-3 text-xs text-gray-600">{msg.sender || '-'}</td>
                      <td className="py-2 px-3 text-gray-600">{msg.charCount ?? '-'}</td>
                      <td className="py-2 px-3 text-xs text-gray-500">
                        {msg.createdAt
                          ? new Date(msg.createdAt).toLocaleString('ko-KR')
                          : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border-orange-100 bg-orange-50">
        <CardContent className="pt-4">
          <p className="text-xs text-orange-700">
            ⚠️ 위기 모니터링은 메타데이터(레벨·세션ID·글자수)만 표시합니다. 사용자 실제 내용은 절대 노출하지 않습니다.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
