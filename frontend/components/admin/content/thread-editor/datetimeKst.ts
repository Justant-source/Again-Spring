/** ISO Instant → datetime-local value in Asia/Seoul. */
export function toDatetimeLocalKst(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}

/** datetime-local (KST wall clock) → ISO Instant. */
export function fromDatetimeLocalKst(local: string): string {
  if (!local) return '';
  const withSeconds = local.length === 16 ? `${local}:00` : local;
  return new Date(`${withSeconds}+09:00`).toISOString();
}

/** Shift a datetime-local (KST) value by {@code deltaMs}. Empty/invalid → unchanged. */
export function shiftDatetimeLocalKst(local: string, deltaMs: number): string {
  if (!local || !deltaMs) return local;
  const iso = fromDatetimeLocalKst(local);
  if (!iso) return local;
  const next = new Date(new Date(iso).getTime() + deltaMs);
  if (Number.isNaN(next.getTime())) return local;
  return toDatetimeLocalKst(next.toISOString());
}

/**
 * Delta between two datetime-local (KST) values in ms.
 * Returns 0 if either side is empty/invalid.
 */
export function datetimeLocalKstDeltaMs(prevLocal: string, nextLocal: string): number {
  if (!prevLocal || !nextLocal) return 0;
  const prevIso = fromDatetimeLocalKst(prevLocal);
  const nextIso = fromDatetimeLocalKst(nextLocal);
  if (!prevIso || !nextIso) return 0;
  const prevMs = new Date(prevIso).getTime();
  const nextMs = new Date(nextIso).getTime();
  if (Number.isNaN(prevMs) || Number.isNaN(nextMs)) return 0;
  return nextMs - prevMs;
}

export function formatKstLabel(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
}
