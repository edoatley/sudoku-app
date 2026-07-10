import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';

vi.mock('../api/sudokuApi.js', () => {
  class ForbiddenError extends Error {
    constructor() {
      super('Access denied');
      this.name = 'ForbiddenError';
    }
  }
  return { getHint: vi.fn(), ForbiddenError };
});

import { getHint } from '../api/sudokuApi.js';
import { useHintSystem } from './useHintSystem.js';

const noop = () => {};

function setup(recordEvent) {
  const currentGridRef = { current: Array.from({ length: 9 }, () => Array(9).fill(0)) };
  const hintMinRankRef = { current: null };
  return renderHook(() =>
    useHintSystem({
      currentGridRef,
      hintMinRankRef,
      onForbidden: noop,
      setStatusMessage: noop,
      setGameStatus: noop,
      setIsLoading: noop,
      setCandidateGrid: noop,
      setCurrentGrid: noop,
      recordEvent,
    })
  );
}

describe('useHintSystem event logging', () => {
  beforeEach(() => vi.clearAllMocks());

  it('records a HINT_REQUEST and HINT_RESPONSE sharing a cid, found=true when a hint returns', async () => {
    // @spec FE-BE-021
    getHint.mockResolvedValue({ techniqueName: 'Naked Single', strategyRank: 1, difficulty: 'easy' });
    const recordEvent = vi.fn();
    const { result } = setup(recordEvent);

    await act(async () => {
      await result.current.requestHint();
    });

    const req = recordEvent.mock.calls.find((c) => c[0].type === 'HINT_REQUEST')[0];
    const resp = recordEvent.mock.calls.find((c) => c[0].type === 'HINT_RESPONSE')[0];
    expect(req.cid).toBe(resp.cid);
    expect(resp.found).toBe(true);
    expect(resp.techniqueName).toBe('Naked Single');
  });

  it('records HINT_RESPONSE with found=false when no strategy applies', async () => {
    // @spec FE-BE-021
    getHint.mockResolvedValue(null);
    const recordEvent = vi.fn();
    const { result } = setup(recordEvent);

    await act(async () => {
      await result.current.requestHint();
    });

    const resp = recordEvent.mock.calls.find((c) => c[0].type === 'HINT_RESPONSE')[0];
    expect(resp.found).toBe(false);
    expect(resp.techniqueName).toBeNull();
  });

  it('records no HINT_RESPONSE when the hint request errors', async () => {
    // @spec FE-BE-021 — transport error leaves a dangling HINT_REQUEST
    getHint.mockRejectedValue(new Error('network'));
    const recordEvent = vi.fn();
    const { result } = setup(recordEvent);

    await act(async () => {
      await result.current.requestHint();
    });

    expect(recordEvent.mock.calls.some((c) => c[0].type === 'HINT_REQUEST')).toBe(true);
    expect(recordEvent.mock.calls.some((c) => c[0].type === 'HINT_RESPONSE')).toBe(false);
  });
});
