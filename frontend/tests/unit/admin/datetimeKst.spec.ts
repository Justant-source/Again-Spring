import { describe, expect, it } from 'vitest';
import {
  applyPostAtDeltaToItems,
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

  it('applyPostAtDeltaToItems shifts all item times from baseline→final postAt', () => {
    const baseline = '2026-08-01T20:00';
    const value = {
      postAtLocal: '2026-08-01T20:10',
      items: [
        { key: 'c1', atLocal: '2026-08-01T20:03' },
        { key: 'c2', atLocal: '2026-08-01T20:08' },
      ],
    };
    const next = applyPostAtDeltaToItems(value, baseline);
    expect(next.items[0].atLocal).toBe('2026-08-01T20:13');
    expect(next.items[1].atLocal).toBe('2026-08-01T20:18');
    expect(next.postAtLocal).toBe('2026-08-01T20:10');
  });

  it('applyPostAtDeltaToItems is no-op when postAt unchanged', () => {
    const value = {
      postAtLocal: '2026-08-01T20:00',
      items: [{ key: 'c1', atLocal: '2026-08-01T20:03' }],
    };
    expect(applyPostAtDeltaToItems(value, '2026-08-01T20:00')).toBe(value);
  });
});
