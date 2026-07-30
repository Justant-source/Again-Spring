'use client';

import { useState, useEffect, useCallback } from 'react';
import { getCrisisRecent } from '@/lib/api/admin';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { RefreshCw, AlertTriangle, ShieldAlert } from 'lucide-react';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { AdminTable } from '@/components/admin/AdminTable';
import { formatDateTime } from '@/lib/utils/adminFormat';

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
      <AdminPageHeader
        title="위기 모니터링"
        description={
          lastUpdated
            ? `마지막 갱신: ${lastUpdated.toLocaleTimeString('ko-KR')} · 30초마다 자동 갱신`
            : '30초마다 자동 갱신'
        }
        action={
          <Button variant="outline" size="sm" onClick={load} disabled={loading}>
            <RefreshCw size={14} className={`mr-2 ${loading ? 'animate-spin' : ''}`} />
            새로고침
          </Button>
        }
      />

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
          <AdminTable
            data={messages}
            columns={[
              {
                key: 'level',
                header: '레벨',
                render: (msg: any) => getLevelBadge(msg.level),
              },
              {
                key: 'sessionId',
                header: '세션 ID',
                render: (msg: any) => (
                  <span className="font-mono text-xs text-gray-600 max-w-[120px] truncate inline-block">
                    {msg.sessionId || '-'}
                  </span>
                ),
              },
              {
                key: 'sender',
                header: '발신자',
                render: (msg: any) => <span className="text-xs text-gray-600">{msg.sender || '-'}</span>,
              },
              {
                key: 'charCount',
                header: '글자수',
                render: (msg: any) => <span className="text-gray-600">{msg.charCount ?? '-'}</span>,
              },
              {
                key: 'createdAt',
                header: '발생 시각',
                render: (msg: any) => (
                  <span className="text-xs text-gray-500">
                    {msg.createdAt ? formatDateTime(msg.createdAt) : '-'}
                  </span>
                ),
              },
            ]}
            loading={loading && messages.length === 0}
            emptyMessage="감지된 위기 이벤트가 없습니다."
          />
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
