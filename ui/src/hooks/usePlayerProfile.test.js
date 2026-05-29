// @spec FE-BE-007, FE-UI-042
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { usePlayerProfile } from './usePlayerProfile.js';

vi.mock('../api/sudokuApi.js', () => ({
  getEmailFromSession: vi.fn().mockResolvedValue('user@example.com'),
  getPlayerProfile: vi.fn().mockResolvedValue({ avatarKey: 'Person', displayName: 'Test' }),
  updatePlayerProfile: vi.fn(),
  getGameHistory: vi.fn().mockResolvedValue([]),
  warmupImageRecognition: vi.fn(),
  ForbiddenError: class ForbiddenError extends Error {},
}));

const MOCK_USER = { username: 'test-user' };
const LS_KEY = 'sudoku_gameHistory';

beforeEach(() => {
  localStorage.clear();
});

// ── recordGame ─────────────────────────────────────────────────────────────────

describe('recordGame', () => {
  // @spec FE-UI-042
  it('adds a completed game to history and persists it to localStorage', async () => {
    const { result } = renderHook(() => usePlayerProfile(MOCK_USER));

    act(() => {
      result.current.recordGame({
        gameId: 'game-1',
        difficulty: 'easy',
        outcome: 'won',
        elapsedSeconds: 120,
        hintsUsed: 0,
      });
    });

    expect(result.current.history).toHaveLength(1);
    expect(result.current.history[0]).toMatchObject({
      id: 'game-1',
      difficulty: 'easy',
      outcome: 'won',
      elapsedSeconds: 120,
      hintsUsed: 0,
    });

    const stored = JSON.parse(localStorage.getItem(LS_KEY));
    expect(stored).toHaveLength(1);
    expect(stored[0].id).toBe('game-1');
  });

  it('does nothing when gameId is absent', () => {
    const { result } = renderHook(() => usePlayerProfile(MOCK_USER));
    act(() => {
      result.current.recordGame({ difficulty: 'easy', outcome: 'won', elapsedSeconds: 60 });
    });
    expect(result.current.history).toHaveLength(0);
  });
});

// ── clear on sign-out ──────────────────────────────────────────────────────────

describe('sign-out history clear', () => {
  // @spec FE-BE-007
  it('clears history state and localStorage when user becomes null', async () => {
    // Seed localStorage so it looks like a previous session
    localStorage.setItem(LS_KEY, JSON.stringify([
      { id: 'g1', difficulty: 'easy', outcome: 'won', elapsedSeconds: 60, hintsUsed: 0, completedAt: '2026-05-01T10:00:00Z' },
    ]));

    // Mount as signed-in user; initial state reads from localStorage
    const { result, rerender } = renderHook(({ user }) => usePlayerProfile(user), {
      initialProps: { user: MOCK_USER },
    });

    expect(result.current.history).toHaveLength(1);

    // Sign out — pass null user
    rerender({ user: null });

    await waitFor(() => expect(result.current.history).toHaveLength(0));
    expect(localStorage.getItem(LS_KEY)).toBeNull();
  });
});
