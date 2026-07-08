// Covers the DEV_TOOLS||admin gate on the data-browser menu entry (App.jsx), exercised via the
// real (non-SKIP_AUTH) Authenticator path so `user` is non-null and the admin effect can run.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';

const mockIsAdmin = vi.fn();
vi.mock('./api/sudokuApi.js', () => ({
  isAdmin: (...args) => mockIsAdmin(...args),
}));

vi.mock('@aws-amplify/ui-react', () => ({
  Authenticator: ({ children }) => children({ user: { username: 'admin-user' }, signOut: vi.fn() }),
  ThemeProvider: ({ children }) => children,
  createTheme: () => ({}),
}));
vi.mock('@aws-amplify/ui-react/styles.css', () => ({}));

vi.mock('./hooks/useSudokuGame.js', () => ({
  useSudokuGame: () => ({
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
    finishGame: vi.fn(),
    elapsedSeconds: 0,
    timerRunning: false,
    isPaused: false,
    pauseGame: vi.fn(),
    resumeGame: vi.fn(),
    importStage: null,
  }),
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

vi.mock('./hooks/useKeyboardInput.js', () => ({ useKeyboardInput: () => {} }));

vi.mock('./components/SudokuGrid.jsx', () => ({ default: () => null }));
vi.mock('./components/views/AppView.jsx', () => ({ default: () => null }));
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
vi.mock('./components/DevDataDialog.jsx', () => ({
  default: ({ open }) => (open ? <div data-testid="dev-data-dialog" /> : null),
}));
vi.mock('./components/Header.jsx', () => ({
  default: ({ onDevData }) => (
    <div data-testid="header">
      {onDevData && (
        <button type="button" data-testid="open-dev-data" onClick={onDevData}>
          Data Browser
        </button>
      )}
    </div>
  ),
}));

describe('App — admin data-browser gating (VITE_DEV_TOOLS=false)', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_MOCK_API', 'false');
    vi.stubEnv('VITE_SKIP_AUTH', 'false');
    vi.stubEnv('VITE_DEV_TOOLS', 'false');
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
    mockIsAdmin.mockReset();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('shows the data browser menu entry once isAdmin resolves true', async () => {
    mockIsAdmin.mockResolvedValue(true);
    const { default: App } = await import('./App.jsx');
    render(<App />);

    await waitFor(() => expect(screen.getByTestId('open-dev-data')).toBeTruthy());
  });

  it('does not show the data browser menu entry when isAdmin resolves false', async () => {
    mockIsAdmin.mockResolvedValue(false);
    const { default: App } = await import('./App.jsx');
    render(<App />);

    await waitFor(() => expect(mockIsAdmin).toHaveBeenCalled());
    await act(async () => {}); // flush the isAdmin().then(setAdmin) microtask
    expect(screen.queryByTestId('open-dev-data')).toBeNull();
  });
});
