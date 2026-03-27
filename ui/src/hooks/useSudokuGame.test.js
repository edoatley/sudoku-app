import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useSudokuGame } from './useSudokuGame.js';
import { CANNED_GAME_STATE } from '../mocks/cannedData.js';

// ─── Module mocks ─────────────────────────────────────────────────────────────

vi.mock('../api/sudokuApi.js', () => ({
  createGame: vi.fn(),
  loadGame: vi.fn(),
  saveGame: vi.fn(),
  validatePuzzle: vi.fn(),
  getHint: vi.fn(),
  getCandidates: vi.fn(),
}));

import {
  createGame,
  loadGame,
  saveGame,
  validatePuzzle,
  getHint,
  getCandidates,
} from '../api/sudokuApi.js';

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Wait for the hook to finish its initial load (originalGrid becomes non-null). */
async function mountAndWait(user = null) {
  const hook = renderHook(() => useSudokuGame(user));
  await waitFor(() => expect(hook.result.current.originalGrid).not.toBeNull());
  return hook;
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  localStorage.clear();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  createGame.mockResolvedValue(CANNED_GAME_STATE);
  loadGame.mockResolvedValue(CANNED_GAME_STATE);
  saveGame.mockResolvedValue(undefined);
  validatePuzzle.mockResolvedValue({ isValid: true, isSolved: false, errors: [] });
  getHint.mockResolvedValue({
    techniqueName: 'Naked Single',
    nudge: 'There is a naked single on the board.',
    focus: 'Look at cell (0,2).',
    reveal: 'Cell (0,2) = 4.',
    highlightCells: [{ row: 0, col: 2 }],
    eliminatedCandidates: [],
    solvedCells: [{ row: 0, col: 2, value: 4 }],
  });
  getCandidates.mockResolvedValue({
    candidatesGrid: Array(9).fill(null).map(() => Array(9).fill([])),
  });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

// ─── Initialisation ───────────────────────────────────────────────────────────

describe('initialisation', () => {
  it('starts a new game when no saved game is in localStorage', async () => {
    const { result } = await mountAndWait();
    expect(createGame).toHaveBeenCalledWith('easy', expect.any(AbortSignal));
    expect(result.current.originalGrid).toEqual(CANNED_GAME_STATE.originalGrid);
    expect(result.current.gameId).toBe(CANNED_GAME_STATE.gameId);
  });

  it('loads a saved game when localStorage contains a gameId', async () => {
    localStorage.setItem('sudoku_gameId', CANNED_GAME_STATE.gameId);
    const { result } = await mountAndWait();
    expect(loadGame).toHaveBeenCalledWith(CANNED_GAME_STATE.gameId);
    expect(result.current.originalGrid).toEqual(CANNED_GAME_STATE.originalGrid);
  });

  it('falls back to a new game when loadGame rejects', async () => {
    localStorage.setItem('sudoku_gameId', 'stale-id');
    loadGame.mockRejectedValueOnce(new Error('404'));
    const { result } = await mountAndWait();
    expect(createGame).toHaveBeenCalled();
    expect(result.current.originalGrid).not.toBeNull();
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
    // selectedCell is still {row:0, col:2} — clearCell uses it
    act(() => result.current.clearCell());
    expect(result.current.currentGrid[0][2]).toBe(0);
  });

  it('does not clear a given cell', async () => {
    const { result } = await mountAndWait();
    act(() => result.current.updateCell(0, 0)); // given cell (value = 5)
    act(() => result.current.clearCell());
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
