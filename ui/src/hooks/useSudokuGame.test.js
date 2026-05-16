import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useSudokuGame } from './useSudokuGame.js';
import { CANNED_GAME_STATE, CANNED_CANDIDATES } from '../mocks/cannedData.js';

// sudokuApi.js (real mode) unwraps {rows:[...]} before returning to consumers.
// Since sudokuApi is fully mocked here, we must provide pre-unwrapped data
// so the hook receives plain arrays, matching DT-UI-007.
const GAME_STATE = {
  ...CANNED_GAME_STATE,
  originalGrid: CANNED_GAME_STATE.originalGrid.rows,
  currentGrid: CANNED_GAME_STATE.currentGrid.rows,
  candidates: CANNED_GAME_STATE.candidates.rows,
};

const CANDIDATES = {
  candidatesGrid: CANNED_CANDIDATES.candidatesGrid.rows,
};

// ─── Module mocks ─────────────────────────────────────────────────────────────

vi.mock('../api/sudokuApi.js', () => {
  class ForbiddenError extends Error {
    constructor() { super('Access denied'); this.name = 'ForbiddenError'; }
  }
  return {
    createGame: vi.fn(),
    loadGame: vi.fn(),
    saveGame: vi.fn(),
    getCurrentGame: vi.fn(),
    validatePuzzle: vi.fn(),
    getHint: vi.fn(),
    getCandidates: vi.fn(),
    importPuzzle: vi.fn(),
    createGameFromGrid: vi.fn(),
    getDemoGrid: vi.fn(),
    ForbiddenError,
  };
});

import {
  createGame,
  loadGame,
  saveGame,
  getCurrentGame,
  validatePuzzle,
  getHint,
  getCandidates,
  ForbiddenError,
} from '../api/sudokuApi.js';

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Mount the hook, start a new game, and wait until originalGrid is populated. */
async function mountAndWait(user = null) {
  const hook = renderHook(() => useSudokuGame(user));
  await waitFor(() => expect(hook.result.current.isLoading).toBe(false));
  await act(async () => hook.result.current.startNewGame('easy'));
  await waitFor(() => expect(hook.result.current.originalGrid).not.toBeNull());
  return hook;
}

/** Wait for the hook to settle after mount (isLoading becomes false — works for empty-board state too). */
async function mountAndSettle(user = null) {
  const hook = renderHook(() => useSudokuGame(user));
  await waitFor(() => expect(hook.result.current.isLoading).toBe(false));
  return hook;
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  localStorage.clear();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  createGame.mockResolvedValue(GAME_STATE);
  loadGame.mockResolvedValue(GAME_STATE);
  getCurrentGame.mockResolvedValue(null);
  saveGame.mockResolvedValue(undefined);
  validatePuzzle.mockResolvedValue({ isValid: true, isSolved: false, errors: [] });
  getHint.mockResolvedValue({
    techniqueName: 'Naked Single',
    strategyRank: 20,
    nudge: 'There is a naked single on the board.',
    focus: 'Look at cell (0,2).',
    reveal: 'Cell (0,2) = 4.',
    highlightCells: [{ row: 0, col: 2 }],
    eliminatedCandidates: [],
    solvedCells: [{ row: 0, col: 2, value: 4 }],
  });
  getCandidates.mockResolvedValue(CANDIDATES);
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

// ─── Initialisation ───────────────────────────────────────────────────────────

describe('initialisation', () => {
  it('auto-starts a new game on mount when no saved gameId exists', async () => {
    const { result } = await mountAndSettle();
    expect(createGame).toHaveBeenCalled();
    expect(loadGame).not.toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
    expect(result.current.currentGrid).not.toBeNull();
    expect(result.current.gameStatus).toBe('idle');
  });

  it('loads a saved game when localStorage contains a gameId', async () => {
    localStorage.setItem('sudoku_gameId', GAME_STATE.gameId);
    const { result } = await mountAndSettle();
    expect(loadGame).toHaveBeenCalledWith(GAME_STATE.gameId);
    expect(createGame).not.toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
    expect(result.current.gameId).toBe(GAME_STATE.gameId);
  });

  it('loads in-progress game from server when authenticated user has no saved gameId', async () => {
    getCurrentGame.mockResolvedValueOnce(GAME_STATE);
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(getCurrentGame).toHaveBeenCalled();
    expect(createGame).not.toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
    expect(result.current.gameId).toBe(GAME_STATE.gameId);
  });

  it('auto-starts a new game when authenticated user has no in-progress game on server', async () => {
    getCurrentGame.mockResolvedValueOnce(null);
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(getCurrentGame).toHaveBeenCalled();
    expect(createGame).toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
  });

  it('restores candidates from backend on device-switch load', async () => {
    const fakeCandidates = Array(9).fill(null).map(() =>
      Array(9).fill(null).map(() => [1, 2])
    );
    getCurrentGame.mockResolvedValueOnce({
      ...GAME_STATE,
      candidates: fakeCandidates,
      timeSpentSeconds: 120,
    });
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(result.current.candidateGrid[0][0]).toEqual([1, 2]);
    expect(result.current.elapsedSeconds).toBe(120);
  });

  it('falls back to empty candidates when backend returns null candidates on device-switch', async () => {
    getCurrentGame.mockResolvedValueOnce({
      ...GAME_STATE,
      candidates: null,
      timeSpentSeconds: 60,
    });
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(result.current.candidateGrid[0][0]).toEqual([]);
  });

  it('uses backend timeSpentSeconds when greater than localStorage elapsed on same-device reload', async () => {
    localStorage.setItem('sudoku_gameId', GAME_STATE.gameId);
    localStorage.setItem('sudoku_elapsedSeconds', '10');
    loadGame.mockResolvedValueOnce({
      ...GAME_STATE,
      timeSpentSeconds: 90,
    });
    const { result } = await mountAndSettle();
    expect(result.current.elapsedSeconds).toBe(90);
  });

  it('auto-starts a new game when not authenticated (no getCurrentGame call)', async () => {
    const { result } = await mountAndSettle(null);
    expect(getCurrentGame).not.toHaveBeenCalled();
    expect(createGame).toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
  });

  it('shows empty board when saved gameId fails to load (no auto-start fallback)', async () => {
    localStorage.setItem('sudoku_gameId', 'stale-id');
    loadGame.mockRejectedValueOnce(new Error('404'));
    const { result } = await mountAndSettle();
    expect(loadGame).toHaveBeenCalledWith('stale-id');
    expect(createGame).not.toHaveBeenCalled();
    expect(result.current.originalGrid).toBeNull();
    expect(result.current.currentGrid).toBeNull();
    expect(localStorage.getItem('sudoku_gameId')).toBeNull();
  });

  it('ignores abandoned game in localStorage and fetches current game from API when authenticated', async () => {
    localStorage.setItem('sudoku_gameId', 'abandoned-id');
    loadGame.mockResolvedValueOnce({ ...GAME_STATE, gameId: 'abandoned-id', status: 'ABANDONED' });
    const activeGame = { ...GAME_STATE, gameId: 'active-id', status: 'IN_PROGRESS' };
    getCurrentGame.mockResolvedValueOnce(activeGame);
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(loadGame).toHaveBeenCalledWith('abandoned-id');
    expect(getCurrentGame).toHaveBeenCalled();
    expect(result.current.gameId).toBe('active-id');
    // active game's id should now be in localStorage, not the abandoned one
    expect(localStorage.getItem('sudoku_gameId')).toBe('active-id');
  });

  it('starts new game when abandoned game in localStorage and no active game on server', async () => {
    localStorage.setItem('sudoku_gameId', 'abandoned-id');
    loadGame.mockResolvedValueOnce({ ...GAME_STATE, gameId: 'abandoned-id', status: 'ABANDONED' });
    getCurrentGame.mockResolvedValueOnce(null);
    const mockUser = { username: 'test-user' };
    const { result } = await mountAndSettle(mockUser);
    expect(getCurrentGame).toHaveBeenCalled();
    expect(createGame).toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
    // stale id cleared; new game's id is now in localStorage
    expect(localStorage.getItem('sudoku_gameId')).not.toBe('abandoned-id');
  });

  it('ignores solved game in localStorage and starts new game when unauthenticated', async () => {
    localStorage.setItem('sudoku_gameId', 'solved-id');
    loadGame.mockResolvedValueOnce({ ...GAME_STATE, gameId: 'solved-id', status: 'SOLVED' });
    const { result } = await mountAndSettle(null);
    expect(loadGame).toHaveBeenCalledWith('solved-id');
    expect(getCurrentGame).not.toHaveBeenCalled();
    expect(createGame).toHaveBeenCalled();
    expect(result.current.originalGrid).toEqual(GAME_STATE.originalGrid);
    // stale id cleared; new game's id is now in localStorage
    expect(localStorage.getItem('sudoku_gameId')).not.toBe('solved-id');
  });
});

// ─── Cell selection ───────────────────────────────────────────────────────────

describe('cell selection', () => {
  it('updateCell sets selectedCell', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.updateCell(4, 4));
    expect(result.current.selectedCell).toEqual({ row: 4, col: 4 });
  });
});

// ─── Writing values ───────────────────────────────────────────────────────────

describe('writing values (normal mode)', () => {
  it('handleNumberSelect writes to the currently selected cell', async () => {
    const { result } = await mountAndWait();
    // row 0, col 2 is 0 (empty) in CANNED_GAME_STATE
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(7));
    expect(result.current.currentGrid[0][2]).toBe(7);
  });

  it('does not overwrite a given (non-zero original) cell', async () => {
    const { result } = await mountAndWait();
    // row 0, col 0 = 5 in the canned puzzle — a given cell
    act(() => result.current.updateCell(0, 0));
    act(() => result.current.handleNumberSelect(9));
    expect(result.current.currentGrid[0][0]).toBe(5); // unchanged
  });

  it('writing to a cell clears its error highlight', async () => {
    validatePuzzle.mockResolvedValueOnce({
      isValid: false,
      errors: [{ row: 0, col: 2 }],
    });
    const { result } = await mountAndWait();
    await act(async () => result.current.requestValidation());
    expect(result.current.errorCells.has('0,2')).toBe(true);
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(4));
    expect(result.current.errorCells.has('0,2')).toBe(false);
  });
});

// ─── Candidate mode ───────────────────────────────────────────────────────────

describe('candidate mode', () => {
  it('toggles a candidate in an empty cell', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.setInputMode('candidate'));
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(3));
    expect(result.current.candidateGrid[0][2]).toContain(3);
  });

  it('removes a candidate when the same number is selected again', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.setInputMode('candidate'));
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(3));
    act(() => result.current.handleNumberSelect(3));
    expect(result.current.candidateGrid[0][2]).not.toContain(3);
  });
});

// ─── Undo ─────────────────────────────────────────────────────────────────────

describe('undo', () => {
  it('undoes the last normal-mode move', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(4));
    expect(result.current.currentGrid[0][2]).toBe(4);
    act(() => result.current.undoLastMove());
    expect(result.current.currentGrid[0][2]).toBe(0);
  });

  it('canUndo is false initially and true after a move', async () => {
    const { result } = await mountAndWait();
    expect(result.current.canUndo).toBe(false);
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(4));
    expect(result.current.canUndo).toBe(true);
  });

  it('undoes the last candidate-mode move', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.setInputMode('candidate'));
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(5));
    expect(result.current.candidateGrid[0][2]).toContain(5);
    act(() => result.current.undoLastMove());
    expect(result.current.candidateGrid[0][2]).not.toContain(5);
  });
});

// ─── Clear cell ───────────────────────────────────────────────────────────────

describe('clearCell', () => {
  it('removes the value from the selected non-given cell', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.updateCell(0, 2));
    act(() => result.current.handleNumberSelect(4));
    expect(result.current.currentGrid[0][2]).toBe(4);
    act(() => result.current.clearCell(0, 2));
    expect(result.current.currentGrid[0][2]).toBe(0);
  });

  it('does not clear a given cell', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.clearCell(0, 0)); // given cell (value = 5)
    expect(result.current.currentGrid[0][0]).toBe(5);
  });
});

// ─── Difficulty ───────────────────────────────────────────────────────────────

describe('difficulty', () => {
  it('setDifficulty updates the difficulty state', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.setDifficulty('hard'));
    expect(result.current.difficulty).toBe('hard');
  });
});

// ─── Validation ───────────────────────────────────────────────────────────────

describe('requestValidation', () => {
  it('sets gameStatus to "valid" when the board is valid but unsolved', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestValidation());
    expect(result.current.gameStatus).toBe('valid');
  });

  it('sets gameStatus to "invalid" and populates errorCells on error', async () => {
    validatePuzzle.mockResolvedValueOnce({
      isValid: false,
      errors: [{ row: 0, col: 2 }, { row: 1, col: 3 }],
    });
    const { result } = await mountAndWait();
    await act(async () => result.current.requestValidation());
    expect(result.current.gameStatus).toBe('invalid');
    expect(result.current.errorCells.has('0,2')).toBe(true);
    expect(result.current.errorCells.has('1,3')).toBe(true);
  });
});

// ─── Hint ─────────────────────────────────────────────────────────────────────

describe('requestHint / advanceHint / dismissHint', () => {
  it('requestHint sets activeHint and hintStage to nudge', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    expect(result.current.activeHint).not.toBeNull();
    expect(result.current.hintStage).toBe('nudge');
  });

  it('advanceHint progresses from nudge → focus → reveal', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    act(() => result.current.advanceHint());
    expect(result.current.hintStage).toBe('focus');
    act(() => result.current.advanceHint());
    expect(result.current.hintStage).toBe('reveal');
  });

  it('at reveal stage, solvedCells are written into currentGrid', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    act(() => result.current.advanceHint()); // nudge → focus
    act(() => result.current.advanceHint()); // focus → reveal
    expect(result.current.currentGrid[0][2]).toBe(4); // from CANNED_HINT solvedCells
  });

  it('dismissHint clears activeHint and resets hintStage', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    act(() => result.current.dismissHint());
    expect(result.current.activeHint).toBeNull();
    expect(result.current.hintStage).toBe('nudge');
  });
});

// ─── Hint counting and deduplication ──────────────────────────────────────────

describe('hintsUsed and excludedHintRanks', () => {
  it('hintsUsed starts at 0', async () => {
    const { result } = await mountAndWait();
    expect(result.current.hintsUsed).toBe(0);
  });

  it('requestHint increments hintsUsed', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    expect(result.current.hintsUsed).toBe(1);
  });

  it('two requestHint calls increment hintsUsed to 2', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    await act(async () => result.current.requestHint());
    expect(result.current.hintsUsed).toBe(2);
  });

  it('requestHint passes excludedHintRanks on the second call', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // first call
    await act(async () => result.current.requestHint()); // second call
    // Second call should include rank 20 (from first hint) in excludedRanks
    expect(getHint).toHaveBeenLastCalledWith(
      expect.anything(),
      null,
      expect.arrayContaining([20])
    );
  });

  it('requestAlternateHint does NOT increment hintsUsed', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    expect(result.current.hintsUsed).toBe(1);
    await act(async () => result.current.requestAlternateHint());
    expect(result.current.hintsUsed).toBe(1);
  });

  it('requestAlternateHint passes excludedHintRanks accumulated so far', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // excludes rank 20
    getHint.mockResolvedValueOnce({
      techniqueName: 'Hidden Single',
      strategyRank: 40,
      nudge: 'nudge',
      focus: 'focus',
      reveal: 'reveal',
      highlightCells: [],
      eliminatedCandidates: [],
      solvedCells: [{ row: 1, col: 1, value: 3 }],
    });
    await act(async () => result.current.requestAlternateHint());
    expect(getHint).toHaveBeenLastCalledWith(
      expect.anything(),
      null,
      expect.arrayContaining([20])
    );
  });

  it('requestHint retries with empty exclusions when first call returns null and exclusions are non-empty', async () => {
    // @spec HE-UI-011 — fallback retry when NoStrategyApplied with non-empty excludedRanks
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // builds up excludedHintRanks = [20]

    const fullHouseHint = {
      techniqueName: 'Full House',
      strategyRank: 10,
      nudge: 'nudge',
      focus: 'focus',
      reveal: 'reveal',
      highlightCells: [],
      eliminatedCandidates: [],
      solvedCells: [{ row: 2, col: 2, value: 7 }],
    };
    getHint.mockResolvedValueOnce(null);       // first call: null with exclusions
    getHint.mockResolvedValueOnce(fullHouseHint); // retry call: clean slate

    await act(async () => result.current.requestHint());

    // Fallback call should have been with empty exclusions
    const calls = getHint.mock.calls;
    const retryCall = calls[calls.length - 1];
    expect(retryCall[2]).toEqual([]);

    // After fallback succeeds the new hint is shown
    expect(result.current.activeHint.techniqueName).toBe('Full House');
  });

  it('requestHint next call after fallback excludes only the fallback rank', async () => {
    // @spec HE-UI-011 — after a reset, the exclusion list starts fresh from the new rank
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // excludedHintRanks = [20]

    const fullHouseHint = {
      techniqueName: 'Full House',
      strategyRank: 10,
      nudge: 'nudge', focus: 'focus', reveal: 'reveal',
      highlightCells: [], eliminatedCandidates: [],
      solvedCells: [{ row: 2, col: 2, value: 7 }],
    };
    getHint.mockResolvedValueOnce(null);          // exhausted with [20]
    getHint.mockResolvedValueOnce(fullHouseHint); // retry: clean slate → rank 10
    getHint.mockResolvedValueOnce(fullHouseHint); // third call (below)

    await act(async () => result.current.requestHint()); // fallback fires, sets excluded=[10]
    await act(async () => result.current.requestHint()); // third call

    // Third call should exclude only rank 10 (not the old rank 20)
    const calls = getHint.mock.calls;
    const thirdCall = calls[calls.length - 1];
    expect(thirdCall[2]).toEqual([10]);
    expect(thirdCall[2]).not.toContain(20);
  });

  it('requestHint shows no-hints message when both calls return null', async () => {
    // @spec HE-UI-011 — genuinely no applicable strategy even with clean slate
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // builds excludedHintRanks = [20]

    getHint.mockResolvedValueOnce(null); // first call returns null
    getHint.mockResolvedValueOnce(null); // retry also returns null

    await act(async () => result.current.requestHint());
    expect(result.current.statusMessage).toBe('No more hints available.');
  });

  it('requestHint does not retry when exclusions are empty and call returns null', async () => {
    // With no exclusions, null means PuzzleSolved (204) — no retry needed
    const { result } = await mountAndWait();
    getHint.mockResolvedValueOnce(null);
    await act(async () => result.current.requestHint());
    expect(getHint).toHaveBeenCalledTimes(1);
    expect(result.current.statusMessage).toBe('No more hints available.');
  });

  it('requestAlternateHint retries with empty exclusions when first call returns null', async () => {
    // @spec HE-UI-011 — same fallback applies to requestAlternateHint
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint()); // excludedHintRanks = [20]

    const fullHouseHint = {
      techniqueName: 'Full House',
      strategyRank: 10,
      nudge: 'nudge', focus: 'focus', reveal: 'reveal',
      highlightCells: [], eliminatedCandidates: [], solvedCells: [],
    };
    getHint.mockResolvedValueOnce(null);
    getHint.mockResolvedValueOnce(fullHouseHint);

    await act(async () => result.current.requestAlternateHint());

    // Fallback call should have been with empty exclusions
    const calls = getHint.mock.calls;
    const retryCall = calls[calls.length - 1];
    expect(retryCall[2]).toEqual([]);
    expect(result.current.activeHint.techniqueName).toBe('Full House');
  });

  it('hintsUsed resets to 0 on startNewGame', async () => {
    const { result } = await mountAndWait();
    await act(async () => result.current.requestHint());
    expect(result.current.hintsUsed).toBe(1);
    await act(async () => result.current.startNewGame('easy'));
    expect(result.current.hintsUsed).toBe(0);
  });

  it('finishGame passes hintsUsed to onGameComplete callback', async () => {
    const onGameComplete = vi.fn();
    const hook = renderHook(() => useSudokuGame(null, { onGameComplete }));
    await waitFor(() => expect(hook.result.current.isLoading).toBe(false));
    await act(async () => hook.result.current.startNewGame('easy'));
    await waitFor(() => expect(hook.result.current.originalGrid).not.toBeNull());
    await act(async () => hook.result.current.requestHint());
    act(() => hook.result.current.finishGame());
    expect(onGameComplete).toHaveBeenCalledWith(
      expect.objectContaining({ hintsUsed: 1 })
    );
  });
});

// ─── Pause / resume ───────────────────────────────────────────────────────────

describe('pause / resume', () => {
  it('pauseGame sets isPaused to true', async () => {
    const { result } = await mountAndWait();
    expect(result.current.isPaused).toBe(false);
    act(() => result.current.pauseGame());
    expect(result.current.isPaused).toBe(true);
  });

  it('resumeGame clears isPaused', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.pauseGame());
    act(() => result.current.resumeGame());
    expect(result.current.isPaused).toBe(false);
  });

  it('timer actually advances after resumeGame', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.pauseGame());
    act(() => result.current.resumeGame());
    const before = result.current.elapsedSeconds;
    act(() => { vi.advanceTimersByTime(3000); });
    expect(result.current.elapsedSeconds).toBe(before + 3);
  });

  it('startNewGame resets isPaused to false', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.pauseGame());
    expect(result.current.isPaused).toBe(true);
    await act(async () => result.current.startNewGame('easy'));
    expect(result.current.isPaused).toBe(false);
  });

  it('timer stops on tab hidden and restarts on tab visible', async () => {
    const { result } = await mountAndWait();
    const beforeHide = result.current.elapsedSeconds;

    // Simulate tab hidden — async because the handler calls saveGame
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    act(() => { vi.advanceTimersByTime(5000); });
    const afterHide = result.current.elapsedSeconds;
    expect(afterHide).toBe(beforeHide); // timer should not advance while hidden

    // Simulate tab visible again — async because the handler may update state
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    act(() => { vi.advanceTimersByTime(3000); });
    expect(result.current.elapsedSeconds).toBe(afterHide + 3); // timer should advance again
  });

  it('timer does NOT restart on tab visible when user explicitly paused', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.pauseGame());

    // async because the tab-hidden handler calls saveGame
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    const snapshot = result.current.elapsedSeconds;
    act(() => { vi.advanceTimersByTime(3000); });
    expect(result.current.elapsedSeconds).toBe(snapshot); // still frozen
    expect(result.current.isPaused).toBe(true); // still paused
  });
});

// ─── Status ───────────────────────────────────────────────────────────────────

describe('clearStatus', () => {
  it('resets statusMessage and gameStatus to idle', async () => {
    validatePuzzle.mockResolvedValueOnce({
      isValid: false,
      errors: [{ row: 0, col: 2 }],
    });
    const { result } = await mountAndWait();
    await act(async () => result.current.requestValidation());
    expect(result.current.gameStatus).toBe('invalid');
    act(() => result.current.clearStatus());
    expect(result.current.statusMessage).toBeNull();
    expect(result.current.gameStatus).toBe('idle');
  });
});

// ─── Auto-completion ──────────────────────────────────────────────────────────

const almostSolved = [
  [5, 3, 4, 6, 7, 8, 9, 1, 2],
  [6, 7, 2, 1, 9, 5, 3, 4, 8],
  [1, 9, 8, 3, 4, 2, 5, 6, 7],
  [8, 5, 9, 7, 6, 1, 4, 2, 3],
  [4, 2, 6, 8, 5, 3, 7, 9, 1],
  [7, 1, 3, 9, 2, 4, 8, 5, 6],
  [9, 6, 1, 5, 3, 7, 2, 8, 4],
  [2, 8, 7, 4, 1, 9, 6, 3, 5],
  [3, 4, 5, 2, 8, 6, 1, 7, 0], // [8][8] = 0 — the only editable cell
];

describe('auto-completion', () => {
  it('auto-sends PATCH with isComplete=true when the last cell is filled validly', async () => {
    loadGame.mockResolvedValueOnce({
      ...GAME_STATE,
      originalGrid: almostSolved,
      currentGrid: almostSolved,
    });
    validatePuzzle.mockResolvedValue({ isValid: true, errors: [] });
    localStorage.setItem('sudoku_gameId', GAME_STATE.gameId);
    const { result } = await mountAndSettle();

    await act(async () => result.current.updateCell(8, 8));
    await act(async () => result.current.handleNumberSelect(9));

    await waitFor(() =>
      expect(saveGame).toHaveBeenCalledWith(
        GAME_STATE.gameId,
        expect.objectContaining({ isComplete: true })
      )
    );
    expect(result.current.gameStatus).toBe('solved');
  });

  it('does NOT send PATCH with isComplete when the board is filled but invalid', async () => {
    loadGame.mockResolvedValueOnce({
      ...GAME_STATE,
      originalGrid: almostSolved,
      currentGrid: almostSolved,
    });
    validatePuzzle.mockResolvedValue({ isValid: false, errors: [{ row: 8, col: 8 }] });
    localStorage.setItem('sudoku_gameId', GAME_STATE.gameId);
    const { result } = await mountAndSettle();

    act(() => result.current.updateCell(8, 8));
    act(() => result.current.handleNumberSelect(5));

    await act(async () => { await new Promise((r) => setTimeout(r, 0)); });

    expect(saveGame).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ isComplete: true })
    );
    expect(result.current.gameStatus).not.toBe('solved');
  });
});

// ─── Fill candidates ────────────────────────────────────────────────────────

describe('fillCandidates', () => {
  it('fetches and populates candidateGrid for empty cells', async () => {
    getCandidates.mockResolvedValueOnce(CANDIDATES);
    const { result } = await mountAndWait();
    await act(async () => result.current.fillCandidates());

    // Row 0 col 2 is empty (0 in currentGrid), expects [1,2,4,6]
    expect(result.current.candidateGrid[0][2]).toEqual([1, 2, 4, 6]);
  });

  it('does not overwrite candidates for filled cells', async () => {
    getCandidates.mockResolvedValueOnce(CANDIDATES);
    const { result } = await mountAndWait();
    await act(async () => result.current.fillCandidates());

    // Row 0 col 0 is filled (5), should remain empty
    expect(result.current.candidateGrid[0][0]).toEqual([]);
  });

  it('is undoable — undo restores the previous candidateGrid', async () => {
    getCandidates.mockResolvedValueOnce(CANDIDATES);
    const { result } = await mountAndWait();
    await act(async () => result.current.fillCandidates());
    expect(result.current.candidateGrid[0][2]).toEqual([1, 2, 4, 6]);
    expect(result.current.canUndo).toBe(true);
    // Undo the fill — should restore empty candidateGrid
    act(() => result.current.undoLastMove());
    expect(result.current.candidateGrid[0][2]).toEqual([]);
  });

  it('persists populated candidates to localStorage', async () => {
    getCandidates.mockResolvedValueOnce(CANDIDATES);
    const { result } = await mountAndWait();
    await act(async () => result.current.fillCandidates());

    const stored = JSON.parse(localStorage.getItem('sudoku_candidateGrid'));
    expect(stored[0][2]).toEqual([1, 2, 4, 6]);
  });
});

// ─── Access control ───────────────────────────────────────────────────────────

describe('access control — ForbiddenError handling', () => {
  it('calls onForbidden when startNewGame receives a 403', async () => {
    const onForbidden = vi.fn();
    // Let the initial auto-start succeed, then return 403 on the explicit call
    createGame.mockResolvedValueOnce(GAME_STATE);
    createGame.mockRejectedValueOnce(new ForbiddenError());
    const { result } = renderHook(() => useSudokuGame(null, { onForbidden }));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => result.current.startNewGame('easy'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(onForbidden).toHaveBeenCalled();
    expect(result.current.statusMessage).toBeNull();
    expect(result.current.gameStatus).not.toBe('error');
  });

  it('does not call onForbidden for non-403 errors', async () => {
    const onForbidden = vi.fn();
    // Let the initial auto-start succeed, then reject on the explicit call
    createGame.mockResolvedValueOnce(GAME_STATE);
    createGame.mockRejectedValueOnce(new Error('HTTP 500'));
    const { result } = renderHook(() => useSudokuGame(null, { onForbidden }));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => result.current.startNewGame('easy'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(onForbidden).not.toHaveBeenCalled();
    expect(result.current.statusMessage).toContain('Failed to load puzzle');
    expect(result.current.gameStatus).toBe('error');
  });
});
