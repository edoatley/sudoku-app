// @spec FE-UI-042
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import App from './App.jsx';

// ─── Module mocks ──────────────────────────────────────────────────────────────

const mockFinishGame = vi.fn();
const mockUseSudokuGame = vi.fn();

vi.mock('./hooks/useSudokuGame.js', () => ({
  useSudokuGame: (...args) => mockUseSudokuGame(...args),
}));

vi.mock('./hooks/usePlayerProfile.js', () => ({
  usePlayerProfile: () => ({
    avatar: 'Person',
    history: [],
    recordGame: vi.fn(),
    fetchHistory: vi.fn(),
    playerProfile: null,
    sessionEmail: null,
    updateProfile: vi.fn(),
  }),
}));

// Stub every child component that would require heavy setup
vi.mock('./components/SudokuGrid.jsx', () => ({ default: () => <div data-testid="grid" /> }));
vi.mock('./components/Header.jsx', () => ({ default: () => <div data-testid="header" /> }));
vi.mock('./components/NumberPad.jsx', () => ({
  NumberPadToolbar: () => null,
  NumberPadInput: () => null,
  NumberPadStatus: () => null,
}));
vi.mock('./components/StatusBar.jsx', () => ({ default: () => null }));
vi.mock('./components/HintDialog.jsx', () => ({ default: () => null }));
vi.mock('./components/PauseOverlay.jsx', () => ({ default: () => null }));
vi.mock('./components/NewGameModal.jsx', () => ({ default: () => null }));
vi.mock('./components/ImportModal.jsx', () => ({ default: () => null }));
vi.mock('./components/TutorialModal.jsx', () => ({ default: () => null }));
vi.mock('./components/DevDataDialog.jsx', () => ({ default: () => null }));
vi.mock('./hooks/useKeyboardInput.js', () => ({ useKeyboardInput: () => {} }));

// ─── Helpers ───────────────────────────────────────────────────────────────────

function baseHookValues(overrides = {}) {
  return {
    originalGrid: null,
    currentGrid: null,
    candidateGrid: null,
    difficulty: 'easy',
    errorCells: [],
    activeHint: null,
    hintStage: null,
    highlightCells: [],
    isLoading: false,
    statusMessage: null,
    gameStatus: 'idle',
    inputMode: 'digit',
    selectedNumber: null,
    setInputMode: vi.fn(),
    handleNumberSelect: vi.fn(),
    startNewGame: vi.fn(),
    startNewGameFromImage: vi.fn(),
    loadDemoGame: vi.fn(),
    updateCell: vi.fn(),
    clearCell: vi.fn(),
    undoLastMove: vi.fn(),
    canUndo: false,
    requestValidation: vi.fn(),
    hintsUsed: 0,
    requestHint: vi.fn(),
    requestAlternateHint: vi.fn(),
    advanceHint: vi.fn(),
    dismissHint: vi.fn(),
    selectedCell: null,
    setSelectedCell: vi.fn(),
    writeCellValue: vi.fn(),
    fillCandidates: vi.fn(),
    clearStatus: vi.fn(),
    finishGame: mockFinishGame,
    elapsedSeconds: 0,
    timerRunning: false,
    isPaused: false,
    pauseGame: vi.fn(),
    resumeGame: vi.fn(),
    importStage: null,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

// ── Congrats dialog ────────────────────────────────────────────────────────────

describe('congrats dialog', () => {
  it('is not rendered when gameStatus is not solved', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues({ gameStatus: 'idle' }));
    render(<App />);
    // MUI Dialog with open=false is not mounted into the DOM
    expect(screen.queryByTestId('congrats-dialog')).toBeNull();
  });

  it('shows elapsed time when gameStatus is solved', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues({
      gameStatus: 'solved',
      elapsedSeconds: 185,
      hintsUsed: 0,
    }));
    render(<App />);
    expect(screen.getByTestId('congrats-dialog')).toBeTruthy();
    // 185s = 3m 5s
    expect(screen.getByText(/3m 5s/i)).toBeTruthy();
  });

  it('shows hints used when hints > 0', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues({
      gameStatus: 'solved',
      elapsedSeconds: 120,
      hintsUsed: 3,
    }));
    render(<App />);
    expect(screen.getByText(/hints used:\s*3/i)).toBeTruthy();
  });

  it('hides hints line when hintsUsed is 0', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues({
      gameStatus: 'solved',
      elapsedSeconds: 60,
      hintsUsed: 0,
    }));
    render(<App />);
    expect(screen.queryByText(/hints used/i)).toBeNull();
  });

  it('calls finishGame when Finish button is clicked', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues({
      gameStatus: 'solved',
      elapsedSeconds: 90,
      hintsUsed: 0,
    }));
    render(<App />);
    fireEvent.click(screen.getByTestId('finish-button'));
    expect(mockFinishGame).toHaveBeenCalledOnce();
  });
});
