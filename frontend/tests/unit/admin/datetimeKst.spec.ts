import { describe, expect, it } from 'vitest';
import {
  datetimeLocalKstDeltaMs,
  fromDatetimeLocalKst,
  shiftDatetimeLocalKst,
  toDatetimeLocalKst,
} from '@/components/admin/content/thread-editor/datetimeKst';

describe('datetimeKst', () => {
  it('round-trips ISO ↔ datetime-local in KST', () => {
    // 2026-08-01 12:30 KST = 03:30 UTC
    const iso = '2026-08-01T03:30:00.000Z';
    expect(toDatetimeLocalKst(iso)).toBe('2026-08-01T12:30');
    expect(fromDatetimeLocalKst('2026-08-01T12:30')).toBe(iso);
  });

  it('shiftDatetimeLocalKst moves by the same wall-clock delta', () => {
    expect(shiftDatetimeLocalKst('2026-08-01T12:30', 10 * 60_000)).toBe('2026-08-01T12:40');
    expect(shiftDatetimeLocalKst('2026-08-01T12:30', -10 * 60_000)).toBe('2026-08-01T12:20');
  });

  it('datetimeLocalKstDeltaMs returns ms between two locals', () => {
    expect(datetimeLocalKstDeltaMs('2026-08-01T12:30', '2026-08-01T12:40')).toBe(10 * 60_000);
    expect(datetimeLocalKstDeltaMs('', '2026-08-01T12:40')).toBe(0);
  });

  it('shifting post slot by 10m keeps comment offset', () => {
    const postPrev = '2026-08-01T20:00';
    const postNext = '2026-08-01T20:10';
    const commentAt = '2026-08-01T20:03';
    const delta = datetimeLocalKstDeltaMs(postPrev, postNext);
    expect(shiftDatetimeLocalKst(commentAt, delta)).toBe('2026-08-01T20:13');
  });
});
