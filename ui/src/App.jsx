import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

import { useSudokuGame } from './hooks/useSudokuGame.js';
import { useKeyboardInput } from './hooks/useKeyboardInput.js';
import { usePlayerProfile } from './hooks/usePlayerProfile.js';
import SudokuGrid from './components/SudokuGrid.jsx';
import StatusBar from './components/StatusBar.jsx';
import { NumberPadToolbar, NumberPadInput, NumberPadStatus } from './components/NumberPad.jsx';
import HintDialog from './components/HintDialog.jsx';
import Header from './components/Header.jsx';
import PauseOverlay from './components/PauseOverlay.jsx';
import NewGameModal from './components/NewGameModal.jsx';
import ImportModal from './components/ImportModal.jsx';
import DevDataDialog from './components/DevDataDialog.jsx';
import TutorialModal from './components/TutorialModal.jsx';
import AppView from './components/views/AppView.jsx';
import CoachWidget from './components/coach/CoachWidget.jsx';
import { isAdmin } from './api/sudokuApi.js';
import { AUTH_PROVIDER } from './auth/session.js';
import FirebaseAuthGate from './auth/FirebaseAuthGate.jsx';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';
const DEV_TOOLS = import.meta.env.VITE_DEV_TOOLS === 'true';
const AI_COACH = import.meta.env.VITE_AI_COACH === 'true';

// Only import Amplify's Authenticator for the Cognito build with auth enabled — never in mock mode
// or the Firebase (GCP) build, so the AWS SDK is not bundled there.
let Authenticator = null;
let AmplifyThemeProvider = null;
let amplifyTheme = null;
if (!MOCK_API && !SKIP_AUTH && AUTH_PROVIDER === 'cognito') {
  const amplifyUi = await import('@aws-amplify/ui-react');
  await import('@aws-amplify/ui-react/styles.css');
  Authenticator = amplifyUi.Authenticator;
  AmplifyThemeProvider = amplifyUi.ThemeProvider;
  amplifyTheme = amplifyUi.createTheme({
    name: 'sudoku-theme',
    tokens: {
      colors: {
        brand: {
          primary: {
            10: { value: '#e3f2fd' },
            20: { value: '#bbdefb' },
            40: { value: '#64b5f6' },
            60: { value: '#42a5f5' },
            80: { value: '#1976d2' },
            90: { value: '#1565c0' },
            100: { value: '#0d47a1' },
          },
        },
      },
      components: {
        button: {
          primary: {
            backgroundColor: { value: '#1976d2' },
          },
        },
      },
    },
  });
}

// @spec NAV-STATE-001, NAV-STATE-002, NAV-STATE-004, NAV-STATE-005
function SudokuApp({ user, signOut }) {
  const [forbidden, setForbidden] = useState(false);
  const [forbiddenEmail, setForbiddenEmail] = useState(null);
  const handleForbidden = useCallback((email = null) => {
    setForbidden(true);
    setForbiddenEmail(email);
  }, []);
  const { avatar, history, recordGame, fetchHistory, playerProfile, sessionEmail, updateProfile } = usePlayerProfile(
    user,
    { onForbidden: handleForbidden }
  );

  const [currentView, setCurrentView] = useState('game');
  const viewStack = useRef([]);

  const navigateTo = useCallback(
    (view) => {
      viewStack.current.push(currentView);
      setCurrentView(view);
    },
    [currentView]
  );

  const navigateBack = useCallback(() => {
    const target = viewStack.current.pop();
    if (target !== undefined) setCurrentView(target);
  }, []);

  const [colorMode, setColorMode] = useState(() => localStorage.getItem('sudoku_colorMode') ?? 'light');
  const handleToggleColorMode = () => {
    setColorMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem('sudoku_colorMode', next);
      return next;
    });
  };
  const muiTheme = useMemo(
    () =>
      createTheme({
        palette: {
          mode: colorMode,
          primary: { main: colorMode === 'dark' ? '#9c27b0' : '#3949ab' },
          background: {
            default: colorMode === 'light' ? '#f5f5f0' : '#121212',
            paper: colorMode === 'light' ? '#ffffff' : '#1e1e1e',
          },
        },
        shape: { borderRadius: 6 },
      }),
    [colorMode]
  );

  const [newGameModalOpen, setNewGameModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [devDataOpen, setDevDataOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [admin, setAdmin] = useState(false);

  useEffect(() => {
    if (!user) {
      setAdmin(false);
      return;
    }
    isAdmin()
      .then(setAdmin)
      .catch(() => setAdmin(false));
  }, [user]);

  const {
    gameId,
    originalGrid,
    currentGrid,
    setCurrentGrid,
    candidateGrid,
    setCandidateGrid,
    difficulty,
    errorCells,
    activeHint,
    hintStage,
    highlightCells,
    setHighlightCells,
    isLoading,
    statusMessage,
    gameStatus,
    inputMode,
    selectedNumber,
    setInputMode,
    handleNumberSelect,
    startNewGame,
    startNewGameFromImage,
    loadDemoGame,
    updateCell,
    clearCell,
    undoLastMove,
    canUndo,
    requestValidation,
    hintsUsed,
    requestHint,
    requestAlternateHint,
    advanceHint,
    dismissHint,
    selectedCell,
    setSelectedCell,
    writeCellValue,
    fillCandidates,
    clearStatus,
    finishGame,
    elapsedSeconds,
    timerRunning,
    isPaused,
    pauseGame,
    resumeGame,
    importStage,
  } = useSudokuGame(user, {
    onGameComplete: recordGame,
    onForbidden: handleForbidden,
    isModalOpen: currentView !== 'game',
  });

  // @spec FE-UI-006 — disable digit buttons in normal mode once a digit appears 9 times in the grid
  const completedNumbers = useMemo(() => {
    if (!currentGrid) return new Set();
    const counts = {};
    for (const row of currentGrid) {
      for (const val of row) {
        if (val !== 0) counts[val] = (counts[val] || 0) + 1;
      }
    }
    return new Set(
      Object.entries(counts)
        .filter(([, c]) => c === 9)
        .map(([n]) => Number(n))
    );
  }, [currentGrid]);

  // @spec KBD-032, KBD-033, NAV-KBD-001 — suppress keyboard input while any modal or view is open
  const isKeyboardSuppressed =
    currentView !== 'game' ||
    newGameModalOpen ||
    importModalOpen ||
    devDataOpen ||
    helpOpen ||
    gameStatus === 'solved' ||
    !!activeHint;

  useKeyboardInput({
    selectedCell,
    inputMode,
    originalGrid,
    currentGrid,
    gameStatus,
    isPaused,
    isModalOpen: isKeyboardSuppressed,
    onDigit: writeCellValue,
    onClear: clearCell,
    onSelectCell: setSelectedCell,
    onToggleMode: setInputMode,
    onUndo: undoLastMove,
    onCheck: requestValidation,
    onHint: requestHint,
    onFill: fillCandidates,
    onHelp: () => setHelpOpen(true),
    onPause: isPaused ? resumeGame : pauseGame,
  });

  const handleNewGameConfirm = (selectedDifficulty) => {
    setNewGameModalOpen(false);
    startNewGame(selectedDifficulty);
  };

  const handleImportConfirm = (imageFile) => {
    setImportModalOpen(false);
    startNewGameFromImage(imageFile);
  };

  const effectiveUser = user ?? (MOCK_API || SKIP_AUTH ? { username: 'Guest' } : null);

  if (forbidden) {
    return (
      <ThemeProvider theme={muiTheme}>
        <CssBaseline />
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            gap: 3,
            p: 3,
          }}
        >
          <Typography variant="h5">Access Denied</Typography>
          <Typography color="text.secondary" align="center">
            {forbiddenEmail ? (
              <>
                <strong>{forbiddenEmail}</strong> is not authorised to access this application.
              </>
            ) : (
              'You are not authorised to use this application.'
            )}
          </Typography>
          {signOut && (
            <Button variant="contained" onClick={signOut}>
              Sign Out
            </Button>
          )}
        </Box>
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <Header
        elapsedSeconds={elapsedSeconds}
        timerRunning={timerRunning}
        gameStarted={!!currentGrid}
        hintsUsed={hintsUsed}
        user={effectiveUser}
        onSignOut={signOut}
        avatar={avatar}
        playerProfile={playerProfile}
        sessionEmail={sessionEmail}
        isPaused={isPaused}
        onPause={pauseGame}
        onResume={resumeGame}
        onNewGame={() => setNewGameModalOpen(true)}
        onImport={() => setImportModalOpen(true)}
        onDemoGame={DEV_TOOLS ? loadDemoGame : null}
        onDevData={DEV_TOOLS || admin ? () => setDevDataOpen(true) : null}
        difficulty={difficulty}
        colorMode={colorMode}
        onToggleColorMode={handleToggleColorMode}
        onNavigate={navigateTo}
      />
      <AppView
        currentView={currentView}
        navigateBack={navigateBack}
        playerProfile={playerProfile}
        currentAvatar={avatar}
        onProfileUpdate={updateProfile}
        history={history}
        onRefreshHistory={fetchHistory}
      />
      <Container maxWidth="lg" disableGutters sx={{ px: { xs: 1, sm: 2, md: 3 }, py: { xs: 1, md: 2 } }}>
        <Stack spacing={{ xs: 1, md: 2 }} alignItems={{ xs: 'center', md: 'flex-start' }}>
          {isLoading && !currentGrid ? (
            <CircularProgress />
          ) : isPaused ? (
            // @spec FE-UI-021 — pause overlay covers the grid; the timer itself stops in useGameTimer
            <PauseOverlay onResume={resumeGame} />
          ) : currentGrid ? (
            /* Outer box centres the game column on the page */
            <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              {/* Game column — full width on mobile (grid fills it), inline-fit on desktop */}
              <Box
                sx={{
                  display: { xs: 'flex', sm: 'inline-flex' },
                  flexDirection: 'column',
                  alignItems: 'stretch',
                  gap: 1,
                  width: { xs: '100%', sm: 'auto' },
                }}
              >
                {/* Toolbar row: mode toggle left, action buttons right */}
                <NumberPadToolbar
                  inputMode={inputMode}
                  onModeChange={setInputMode}
                  onClearCell={() => selectedCell && clearCell(selectedCell.row, selectedCell.col)}
                  onUndo={undoLastMove}
                  canUndo={canUndo}
                  onValidate={requestValidation}
                  onHint={requestHint}
                  onFillCandidates={fillCandidates}
                  isLoading={isLoading}
                  onHelp={() => setHelpOpen(true)}
                />
                {/* Grid */}
                <SudokuGrid
                  originalGrid={originalGrid}
                  currentGrid={currentGrid}
                  candidateGrid={candidateGrid}
                  errorCells={errorCells}
                  highlightCells={highlightCells}
                  selectedCell={selectedCell}
                  selectedNumber={selectedNumber}
                  onCellClick={updateCell}
                />
                {/* Number buttons below grid — stretch to grid width */}
                <NumberPadInput
                  selectedNumber={selectedNumber}
                  inputMode={inputMode}
                  onNumberSelect={handleNumberSelect}
                  completedNumbers={completedNumbers}
                />
                {/* Validation status */}
                <NumberPadStatus gameStatus={gameStatus} statusMessage={statusMessage} onCloseStatus={clearStatus} />
              </Box>
              {/* Hint panel — full width below */}
              <Box sx={{ width: '100%', mt: 1 }}>
                <HintDialog
                  open={!!activeHint}
                  hint={activeHint}
                  stage={hintStage}
                  onAdvance={advanceHint}
                  onDismiss={dismissHint}
                  onAlternateHint={requestAlternateHint}
                />
              </Box>
            </Box>
          ) : null}
        </Stack>
      </Container>

      <NewGameModal
        key={difficulty}
        open={newGameModalOpen}
        defaultDifficulty={difficulty}
        isLoading={isLoading}
        onConfirm={handleNewGameConfirm}
        onCancel={() => setNewGameModalOpen(false)}
      />
      <ImportModal
        open={importModalOpen}
        isLoading={isLoading}
        importStage={importStage}
        onConfirm={handleImportConfirm}
        onCancel={() => setImportModalOpen(false)}
      />

      <StatusBar gameStatus={gameStatus} statusMessage={statusMessage} onClose={clearStatus} />

      {(DEV_TOOLS || admin) && <DevDataDialog open={devDataOpen} onClose={() => setDevDataOpen(false)} />}

      <TutorialModal open={helpOpen} src="/help/controls.md" title="How to Play" onClose={() => setHelpOpen(false)} />

      {AI_COACH && (
        <CoachWidget
          // @spec SC-UI-061, SC-UI-062 — keying on the puzzle remounts CoachWidget on new game,
          // resetting useCoachSession's history/isOpen state to their initial (empty/closed) values
          key={originalGrid ? originalGrid[0].join(',') : 'no-game'}
          gameId={gameId}
          currentGrid={currentGrid}
          setHighlightCells={setHighlightCells}
          setCurrentGrid={setCurrentGrid}
          setCandidateGrid={setCandidateGrid}
          playerProfile={playerProfile}
        />
      )}

      <Dialog open={gameStatus === 'solved'} data-testid="congrats-dialog">
        <DialogTitle>Congratulations!</DialogTitle>
        <DialogContent>
          <Typography>
            Puzzle solved in {Math.floor(elapsedSeconds / 60)}m {elapsedSeconds % 60}s
          </Typography>
          {hintsUsed > 0 && (
            <Typography variant="body2" color="text.secondary">
              Hints used: {hintsUsed}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={finishGame} data-testid="finish-button">
            Finish
          </Button>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}

// @spec FE-BE-012 — where VITE_SKIP_AUTH is false, wrap the app in the Amplify Authenticator and
// require login before displaying the game (Cognito build only; see FirebaseLoginLayout for GCP)
function LoginLayout() {
  // When unauthenticated, Authenticator renders its own centered login form.
  // When authenticated, children are called with { user, signOut } and we render
  // SudokuApp at the top level — its own Header replaces the login chrome.
  const muiTheme = createTheme({ palette: { mode: 'light' } });
  const authContent = (
    <Authenticator hideSignUp socialProviders={['google']}>
      {({ signOut, user }) => <SudokuApp user={user} signOut={signOut} />}
    </Authenticator>
  );

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      {AmplifyThemeProvider && amplifyTheme ? (
        <AmplifyThemeProvider theme={amplifyTheme}>{authContent}</AmplifyThemeProvider>
      ) : (
        authContent
      )}
    </ThemeProvider>
  );
}

function FirebaseLoginLayout() {
  const muiTheme = createTheme({ palette: { mode: 'light' } });
  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <FirebaseAuthGate>{({ user, signOut }) => <SudokuApp user={user} signOut={signOut} />}</FirebaseAuthGate>
    </ThemeProvider>
  );
}

function App() {
  if (MOCK_API || SKIP_AUTH) {
    return <SudokuApp user={null} signOut={null} />;
  }

  if (AUTH_PROVIDER === 'firebase') {
    return <FirebaseLoginLayout />;
  }

  return <LoginLayout />;
}

export default App;
