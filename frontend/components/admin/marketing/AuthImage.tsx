'use client';

import { useEffect, useState } from 'react';

interface Props {
  src: string;
  alt: string;
  style?: React.CSSProperties;
}

export function AuthImage({ src, alt, style }: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    setFailed(false);
    setBlobUrl(null);

    const token = typeof window !== 'undefined'
      ? localStorage.getItem('again-spring-token')
      : null;

    fetch(src, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then((res) => {
        if (!res.ok) throw new Error(`${res.status}`);
        return res.blob();
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      })
      .catch(() => setFailed(true));

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [src]);

  if (failed) {
    return (
      <div
        style={{
          ...style,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#f5f5f5',
          color: '#bbb',
          fontSize: 11,
        }}
      >
        로드 실패
      </div>
    );
  }

  if (!blobUrl) {
    return (
      <>
        <style>{`@keyframes ag-shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}`}</style>
        <div
          style={{
            ...style,
            background: 'linear-gradient(90deg,#f0f0f0 25%,#e8e8e8 50%,#f0f0f0 75%)',
            backgroundSize: '200% 100%',
            animation: 'ag-shimmer 1.4s infinite',
          }}
        />
      </>
    );
  }

  return <img src={blobUrl} alt={alt} style={style} />;
}
