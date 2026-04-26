import { useEffect, useRef } from 'react';

export function usePolling(callback: () => void, interval: number) {
  const callbackRef = useRef(callback);
  useEffect(() => { callbackRef.current = callback; }, [callback]);

  useEffect(() => {
    let timer: NodeJS.Timeout;
    let active = true;

    const tick = () => {
      if (active && document.visibilityState === 'visible') {
        callbackRef.current();
      }
      timer = setTimeout(tick, interval);
    };

    timer = setTimeout(tick, interval);
    return () => { active = false; clearTimeout(timer); };
  }, [interval]);
}
