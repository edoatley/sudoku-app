// @spec LT-UI-003, LT-UI-004, LT-UI-005
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useLeaderboard } from './useLeaderboard.js';

vi.mock('../api/sudokuApi.js', () => ({
  getLeaderboard: vi.fn(),
}));

import { getLeaderboard } from '../api/sudokuApi.js';

const MOCK_ENTRIES = [
  {
    userId: 'user-1',
    displayName: 'Ed',
    avatarKey: 'SportsBasketball',
    rank: 1,
    totalWins: 10,
    totalGames: 12,
    avgScore: 185,
    avgElapsedSeconds: 241,
    bestTimeByDifficulty: { easy: 95, medium: 241 },
  },
  {
    userId: 'user-2',
    displayName: 'Alice',
    avatarKey: 'DirectionsRun',
    rank: 2,
    totalWins: 7,
    totalGames: 9,
    avgScore: 140,
    avgElapsedSeconds: 310,
    bestTimeByDifficulty: { easy: 110, hard: 720 },
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

// @spec LT-UI-003
describe('useLeaderboard — initial fetch', () => {
  it('fetches leaderboard on mount and exposes entries', async () => {
    getLeaderboard.mockResolvedValue({ entries: MOCK_ENTRIES });

    const { result } = renderHook(() => useLeaderboard());

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.leaderboard).toEqual(MOCK_ENTRIES);
    expect(result.current.error).toBeNull();
  });

  it('starts with loading true before fetch resolves', () => {
    getLeaderboard.mockReturnValue(new Promise(() => {})); // never resolves

    const { result } = renderHook(() => useLeaderboard());

    expect(result.current.loading).toBe(true);
    expect(result.current.leaderboard).toEqual([]);
  });
});

// @spec LT-UI-004
describe('useLeaderboard — loading state', () => {
  it('sets loading false on successful fetch', async () => {
    getLeaderboard.mockResolvedValue({ entries: MOCK_ENTRIES });

    const { result } = renderHook(() => useLeaderboard());

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.loading).toBe(false);
  });

  it('sets loading false even when fetch errors', async () => {
    getLeaderboard.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useLeaderboard());

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.loading).toBe(false);
  });

  it('sets loading true again when refresh is called', async () => {
    getLeaderboard.mockResolvedValue({ entries: MOCK_ENTRIES });
    const { result } = renderHook(() => useLeaderboard());
    await waitFor(() => expect(result.current.loading).toBe(false));

    getLeaderboard.mockReturnValue(new Promise(() => {})); // stall second call
    act(() => {
      result.current.refresh();
    });

    expect(result.current.loading).toBe(true);
  });
});

// @spec LT-UI-005
describe('useLeaderboard — error state', () => {
  it('sets error message when fetch fails', async () => {
    getLeaderboard.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => useLeaderboard());

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.error).toBe('Service unavailable');
  });

  it('retains previously loaded data when refresh fails', async () => {
    getLeaderboard.mockResolvedValue({ entries: MOCK_ENTRIES });
    const { result } = renderHook(() => useLeaderboard());
    await waitFor(() => expect(result.current.loading).toBe(false));

    getLeaderboard.mockRejectedValue(new Error('Timeout'));
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.leaderboard).toEqual(MOCK_ENTRIES);
    expect(result.current.error).toBe('Timeout');
  });

  it('clears error on successful refresh after failure', async () => {
    getLeaderboard.mockRejectedValue(new Error('First error'));
    const { result } = renderHook(() => useLeaderboard());
    await waitFor(() => expect(result.current.loading).toBe(false));

    getLeaderboard.mockResolvedValue({ entries: MOCK_ENTRIES });
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.error).toBeNull();
    expect(result.current.leaderboard).toEqual(MOCK_ENTRIES);
  });
});

describe('useLeaderboard — refresh', () => {
  it('re-fetches and updates leaderboard when refresh is called', async () => {
    const updatedEntries = [{ ...MOCK_ENTRIES[0], totalWins: 11 }];
    getLeaderboard.mockResolvedValueOnce({ entries: MOCK_ENTRIES });
    getLeaderboard.mockResolvedValueOnce({ entries: updatedEntries });

    const { result } = renderHook(() => useLeaderboard());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.leaderboard).toEqual(updatedEntries);
    expect(getLeaderboard).toHaveBeenCalledTimes(2);
  });
});
