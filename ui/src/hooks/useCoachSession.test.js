// @spec SC-RL-010
import { describe, it, expect, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCoachSession } from './useCoachSession.js';

vi.mock('../api/sudokuApi.js', () => ({
  postCoachMessage: vi.fn(),
}));

import { postCoachMessage } from '../api/sudokuApi.js';

describe('useCoachSession — token counter', () => {
  it('seeds tokensUsedThisMonth from the initial value', () => {
    const { result } = renderHook(() =>
      useCoachSession({ gameId: 'g1', currentGrid: [], initialTokensUsedThisMonth: 500 })
    );

    expect(result.current.tokensUsedThisMonth).toBe(500);
  });

  it('follows a late-arriving initial value until a coach response is received', () => {
    const { result, rerender } = renderHook(
      ({ initialTokensUsedThisMonth }) =>
        useCoachSession({ gameId: 'g1', currentGrid: [], initialTokensUsedThisMonth }),
      { initialProps: { initialTokensUsedThisMonth: 0 } }
    );

    expect(result.current.tokensUsedThisMonth).toBe(0);

    // playerProfile fetch resolves after mount, prop changes
    rerender({ initialTokensUsedThisMonth: 750 });

    expect(result.current.tokensUsedThisMonth).toBe(750);
  });

  it('updates tokensUsedThisMonth from a successful coach response', async () => {
    postCoachMessage.mockResolvedValue({
      aiMessage: 'Try looking at row 3.',
      hint: null,
      tokensUsedThisMonth: 4242,
    });

    const { result } = renderHook(() =>
      useCoachSession({ gameId: 'g1', currentGrid: [], initialTokensUsedThisMonth: 500 })
    );

    await act(async () => {
      await result.current.sendMessage("I'm stuck");
    });

    await waitFor(() => expect(result.current.tokensUsedThisMonth).toBe(4242));
  });

  it('does not let a stale initial value clobber a live coach-response total', async () => {
    postCoachMessage.mockResolvedValue({
      aiMessage: 'Try looking at row 3.',
      hint: null,
      tokensUsedThisMonth: 4242,
    });

    const { result, rerender } = renderHook(
      ({ initialTokensUsedThisMonth }) =>
        useCoachSession({ gameId: 'g1', currentGrid: [], initialTokensUsedThisMonth }),
      { initialProps: { initialTokensUsedThisMonth: 0 } }
    );

    await act(async () => {
      await result.current.sendMessage("I'm stuck");
    });
    await waitFor(() => expect(result.current.tokensUsedThisMonth).toBe(4242));

    // A late profile refresh with a stale (pre-call) count should not overwrite the live total.
    rerender({ initialTokensUsedThisMonth: 500 });

    expect(result.current.tokensUsedThisMonth).toBe(4242);
  });
});
