// @spec FE-UI-042, NAV-STATE-001, NAV-STATE-002, NAV-STATE-004, NAV-STATE-005, NAV-KBD-001
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
vi.mock('./components/Header.jsx', () => ({
  default: ({ onNavigate }) => (
    <div data-testid="header">
      <button data-testid="nav-profile"     onClick={() => onNavigate('profile')}>Profile</button>
      <button data-testid="nav-history"     onClick={() => onNavigate('history')}>History</button>
      <button data-testid="nav-statistics"  onClick={() => onNavigate('statistics')}>Statistics</button>
      <button data-testid="nav-leaderboard" onClick={() => onNavigate('leaderboard')}>League</button>
    </div>
  ),
}));
vi.mock('./components/views/AppView.jsx', () => ({
  default: ({ currentView, navigateBack }) => (
    <div data-testid={`view-${currentView}`}>
      <button data-testid="back-button" onClick={navigateBack}>Back</button>
    </div>
  ),
}));
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

// ── Navigation ────────────────────────────────────────────────────────────────

// @spec NAV-STATE-001, NAV-STATE-002
describe('navigation — initial state', () => {
  it('renders the game view (not a player view) on first load', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    // AppView receives currentView='game' initially — its testid is view-game
    expect(screen.getByTestId('view-game')).toBeTruthy();
  });
});

// @spec NAV-STATE-004
describe('navigation — navigateTo', () => {
  it('shows profile view when Header triggers onNavigate("profile")', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-profile'));
    expect(screen.getByTestId('view-profile')).toBeTruthy();
  });

  it('shows history view when Header triggers onNavigate("history")', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-history'));
    expect(screen.getByTestId('view-history')).toBeTruthy();
  });

  it('shows statistics view when Header triggers onNavigate("statistics")', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-statistics'));
    expect(screen.getByTestId('view-statistics')).toBeTruthy();
  });

  it('shows leaderboard view when Header triggers onNavigate("leaderboard")', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-leaderboard'));
    expect(screen.getByTestId('view-leaderboard')).toBeTruthy();
  });
});

// @spec NAV-STATE-005
describe('navigation — navigateBack', () => {
  it('returns to game view when back is triggered from a player view', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-history'));
    expect(screen.getByTestId('view-history')).toBeTruthy();
    fireEvent.click(screen.getByTestId('back-button'));
    expect(screen.getByTestId('view-game')).toBeTruthy();
  });

  it('navigateBack with empty stack returns to game view', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    // Already at 'game', back should stay at 'game'
    fireEvent.click(screen.getByTestId('back-button'));
    expect(screen.getByTestId('view-game')).toBeTruthy();
  });
});

// @spec NAV-KBD-001
describe('navigation — keyboard suppression', () => {
  it('passes isModalOpen=false to useSudokuGame when currentView is game', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    // useSudokuGame called with options; isModalOpen should be false when view='game'
    const callArgs = mockUseSudokuGame.mock.calls[0];
    const options = callArgs[1] ?? {};
    expect(options.isModalOpen).toBe(false);
  });

  it('passes isModalOpen=true to useSudokuGame when a player view is active', () => {
    mockUseSudokuGame.mockReturnValue(baseHookValues());
    render(<App />);
    fireEvent.click(screen.getByTestId('nav-profile'));
    const lastCall = mockUseSudokuGame.mock.calls[mockUseSudokuGame.mock.calls.length - 1];
    const options = lastCall[1] ?? {};
    expect(options.isModalOpen).toBe(true);
  });
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
