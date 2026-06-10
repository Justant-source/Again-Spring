'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { exchangeYoutubeOauth } from '@/lib/api/admin/marketing';

/**
 * YouTube OAuth 2.0 콜백 페이지.
 *
 * Google이 authorization code를 이 URL로 리다이렉트한다.
 * code + state를 AS 백엔드 → ASM으로 전송해 refresh_token을 저장한 뒤
 * 팝업 오프너에 성공/실패를 postMessage로 알리고 창을 닫는다.
 */
export default function YoutubeOAuthCallback() {
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [message, setMessage] = useState('Google 인증 처리 중…');

  useEffect(() => {
    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const error = searchParams.get('error');

    if (error) {
      const msg = error === 'access_denied' ? '인증이 취소되었습니다.' : `Google 오류: ${error}`;
      setStatus('error');
      setMessage(msg);
      if (window.opener) {
        window.opener.postMessage(`youtube-oauth-error:${msg}`, window.location.origin);
      }
      return;
    }

    if (!code || !state) {
      const msg = 'code 또는 state 파라미터가 없습니다.';
      setStatus('error');
      setMessage(msg);
      if (window.opener) {
        window.opener.postMessage(`youtube-oauth-error:${msg}`, window.location.origin);
      }
      return;
    }

    exchangeYoutubeOauth(code, state)
      .then(() => {
        setStatus('success');
        setMessage('YouTube 계정 연결 완료!');
        if (window.opener) {
          window.opener.postMessage('youtube-oauth-success', window.location.origin);
        }
        // 1.5초 후 자동 닫기
        setTimeout(() => window.close(), 1500);
      })
      .catch((err: unknown) => {
        let msg = '알 수 없는 오류';
        if (typeof err === 'object' && err !== null) {
          const anyErr = err as { response?: { data?: { message?: string; detail?: string } }; message?: string };
          const data = anyErr.response?.data;
          msg = data?.detail ?? data?.message ?? anyErr.message ?? String(err);
        }
        setStatus('error');
        setMessage(`연결 실패: ${msg}`);
        if (window.opener) {
          window.opener.postMessage(`youtube-oauth-error:${msg}`, window.location.origin);
        }
      });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-white p-8">
      {status === 'processing' && (
        <div className="text-center">
          <div className="mb-4 text-4xl">⏳</div>
          <p className="text-gray-700">{message}</p>
        </div>
      )}
      {status === 'success' && (
        <div className="text-center">
          <div className="mb-4 text-4xl">✅</div>
          <p className="font-medium text-green-700">{message}</p>
          <p className="mt-2 text-sm text-gray-500">이 창은 자동으로 닫힙니다.</p>
        </div>
      )}
      {status === 'error' && (
        <div className="text-center">
          <div className="mb-4 text-4xl">❌</div>
          <p className="font-medium text-red-700">{message}</p>
          <button
            onClick={() => window.close()}
            className="mt-4 rounded border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
          >
            창 닫기
          </button>
        </div>
      )}
    </div>
  );
}
