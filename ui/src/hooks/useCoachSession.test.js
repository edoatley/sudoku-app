// @spec SC-RL-010, SC-UI-050, SC-UI-051
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

describe('useCoachSession — reveal writes to the board (SC-UI-050, SC-UI-051)', () => {
  const emptyGrid = () => Array.from({ length: 9 }, () => Array(9).fill(0));
  const emptyCandidates = () => Array.from({ length: 9 }, () => Array.from({ length: 9 }, () => []));

  it('writes solvedCells to the grid and clears their candidates when revealHint is true', async () => {
    postCoachMessage.mockResolvedValue({
      aiMessage: 'Row 8, Column 4 must be 1.',
      revealHint: true,
      hint: { highlightCells: [], solvedCells: [{ row: 7, col: 3, value: 1 }], eliminatedCandidates: [] },
    });

    const setCurrentGrid = vi.fn();
    const setCandidateGrid = vi.fn();

    const { result } = renderHook(() =>
      useCoachSession({ gameId: 'g1', currentGrid: emptyGrid(), setCurrentGrid, setCandidateGrid })
    );

    await act(async () => {
      await result.current.sendMessage('Just tell me the answer');
    });

    expect(setCurrentGrid).toHaveBeenCalled();
    const nextGrid = setCurrentGrid.mock.calls[0][0](emptyGrid());
    expect(nextGrid[7][3]).toBe(1);

    expect(setCandidateGrid).toHaveBeenCalled();
    const nextCandidates = setCandidateGrid.mock.calls[0][0](emptyCandidates());
    expect(nextCandidates[7][3]).toEqual([]);
  });

  it('removes eliminatedCandidates from the candidate grid when revealHint is true', async () => {
    postCoachMessage.mockResolvedValue({
      aiMessage: 'Digit 6 can be removed from the rest of row 2.',
      revealHint: true,
      hint: { highlightCells: [], solvedCells: [], eliminatedCandidates: [{ row: 1, col: 5, value: 6 }] },
    });

    const setCurrentGrid = vi.fn();
    const setCandidateGrid = vi.fn();

    const { result } = renderHook(() =>
      useCoachSession({ gameId: 'g1', currentGrid: emptyGrid(), setCurrentGrid, setCandidateGrid })
    );

    await act(async () => {
      await result.current.sendMessage('Just tell me the answer');
    });

    const seeded = emptyCandidates();
    seeded[1][5] = [2, 6, 8];
    const nextCandidates = setCandidateGrid.mock.calls[0][0](seeded);
    expect(nextCandidates[1][5]).toEqual([2, 8]);
  });

  it('does not write to the board when revealHint is false', async () => {
    postCoachMessage.mockResolvedValue({
      aiMessage: "Let's look at row 3 together.",
      revealHint: false,
      hint: {
        highlightCells: [{ row: 2, col: 0 }],
        solvedCells: [{ row: 2, col: 3, value: 7 }],
        eliminatedCandidates: [],
      },
    });

    const setCurrentGrid = vi.fn();
    const setCandidateGrid = vi.fn();

    const { result } = renderHook(() =>
      useCoachSession({ gameId: 'g1', currentGrid: emptyGrid(), setCurrentGrid, setCandidateGrid })
    );

    await act(async () => {
      await result.current.sendMessage("I'm stuck");
    });

    expect(setCurrentGrid).not.toHaveBeenCalled();
    expect(setCandidateGrid).not.toHaveBeenCalled();
  });
});
