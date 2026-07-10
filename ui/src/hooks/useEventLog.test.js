import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEventLog, newCid } from './useEventLog.js';

describe('useEventLog', () => {
  it('records events with a client timestamp', () => {
    // @spec FE-BE-020
    const { result } = renderHook(() => useEventLog());
    act(() => result.current.recordEvent({ type: 'NUMBER', r: 1, c: 2, v: 5 }));

    const batch = result.current.takeBatch();
    expect(batch.events).toHaveLength(1);
    expect(batch.events[0]).toMatchObject({ type: 'NUMBER', r: 1, c: 2, v: 5 });
    expect(typeof batch.events[0].clientTs).toBe('number');
  });

  it('takeBatch drains the buffer so a second take returns null', () => {
    // @spec FE-BE-022
    const { result } = renderHook(() => useEventLog());
    act(() => result.current.recordEvent({ type: 'NUMBER_CLEAR', r: 0, c: 0 }));

    expect(result.current.takeBatch().events).toHaveLength(1);
    expect(result.current.takeBatch()).toBeNull();
  });

  it('restoreBatch puts a failed batch back ahead of newer events', () => {
    // @spec FE-BE-022 — clear only after success; retain (re-queue) on failure
    const { result } = renderHook(() => useEventLog());
    act(() => result.current.recordEvent({ type: 'NUMBER', r: 0, c: 0, v: 1 }));

    const failed = result.current.takeBatch();
    act(() => result.current.recordEvent({ type: 'NUMBER', r: 8, c: 8, v: 9 }));
    act(() => result.current.restoreBatch(failed));

    const next = result.current.takeBatch();
    expect(next.events.map((e) => e.v)).toEqual([1, 9]);
  });

  it('caps the buffer at 500, dropping oldest and prepending an EVENTS_TRUNCATED marker', () => {
    // @spec FE-BE-023
    const { result } = renderHook(() => useEventLog());
    act(() => {
      for (let i = 0; i < 520; i++) {
        result.current.recordEvent({ type: 'NUMBER_CLEAR', r: 0, c: 0, seq: i });
      }
    });

    const batch = result.current.takeBatch();
    expect(batch.truncated).toBe(true);
    expect(batch.events).toHaveLength(500);
    // oldest (seq 0..19) were dropped
    expect(batch.events[0].seq).toBe(20);
    // wire form carries the truncation marker as its own leading event
    expect(batch.wire[0].type).toBe('EVENTS_TRUNCATED');
    expect(batch.wire).toHaveLength(501);
  });

  it('resetEvents clears the buffer and truncation flag', () => {
    // @spec FE-BE-024
    const { result } = renderHook(() => useEventLog());
    act(() => result.current.recordEvent({ type: 'NUMBER', r: 0, c: 0, v: 1 }));
    act(() => result.current.resetEvents());

    expect(result.current.takeBatch()).toBeNull();
  });

  it('newCid returns distinct ids', () => {
    expect(newCid()).not.toBe(newCid());
  });
});
