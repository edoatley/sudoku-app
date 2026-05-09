import { useState, useMemo, useCallback } from 'react';
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
import { usePlayerProfile } from './hooks/usePlayerProfile.js';
import SudokuGrid from './components/SudokuGrid.jsx';
import StatusBar from './components/StatusBar.jsx';
import { NumberPadToolbar, CandidateRow, NumberPadInput, NumberPadStatus } from './components/NumberPad.jsx';
import HintDialog from './components/HintDialog.jsx';
import Header from './components/Header.jsx';
import PauseOverlay from './components/PauseOverlay.jsx';
import NewGameModal from './components/NewGameModal.jsx';
import ImportModal from './components/ImportModal.jsx';
import DevDataDialog from './components/DevDataDialog.jsx';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';
const DEV_TOOLS = import.meta.env.VITE_DEV_TOOLS === 'true';

// Only import Authenticator when auth is enabled to avoid loading Amplify in mock mode
let Authenticator = null;
let AmplifyThemeProvider = null;
let amplifyTheme = null;
if (!MOCK_API && !SKIP_AUTH) {
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

function SudokuApp({ user, signOut }) {
  const [forbidden, setForbidden] = useState(false);
  const [forbiddenEmail, setForbiddenEmail] = useState(null);
  const handleForbidden = useCallback((email = null) => { setForbidden(true); setForbiddenEmail(email); }, []);
  const { avatar, history, recordGame, playerProfile, sessionEmail, updateProfile } = usePlayerProfile(user, { onForbidden: handleForbidden });

  const [colorMode, setColorMode] = useState(
    () => localStorage.getItem('sudoku_colorMode') ?? 'light'
  );
  const handleToggleColorMode = () => {
    setColorMode((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem('sudoku_colorMode', next);
      return next;
    });
  };
  const muiTheme = useMemo(
    () => createTheme({
      palette: {
        mode: colorMode,
        primary: { main: colorMode === 'dark' ? '#9c27b0' : '#3949ab' },
        background: {
          default: colorMode === 'light' ? '#f5f5f0' : '#121212',
          paper:   colorMode === 'light' ? '#ffffff' : '#1e1e1e',
        },
      },
      shape: { borderRadius: 6 },
    }),
    [colorMode]
  );

  const [newGameModalOpen, setNewGameModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [devDataOpen, setDevDataOpen] = useState(false);

  const {
    originalGrid,
    currentGrid,
    candidateGrid,
    difficulty,
    errorCells,
    activeHint,
    hintStage,
    highlightCells,
    isLoading,
    statusMessage,
    gameStatus,
    inputMode,
    selectedNumber,
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
    fillCandidates,
    clearStatus,
    finishGame,
    elapsedSeconds,
    timerRunning,
    isPaused,
    pauseGame,
    resumeGame,
    importStage,
  } = useSudokuGame(user, { onGameComplete: recordGame, onForbidden: handleForbidden });

  const completedNumbers = useMemo(() => {
    if (!currentGrid) return new Set();
    const counts = {};
    for (const row of currentGrid) {
      for (const val of row) {
        if (val !== 0) counts[val] = (counts[val] || 0) + 1;
      }
    }
    return new Set(Object.entries(counts).filter(([, c]) => c === 9).map(([n]) => Number(n)));
  }, [currentGrid]);

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
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', gap: 3, p: 3 }}>
          <Typography variant="h5">Access Denied</Typography>
          <Typography color="text.secondary" align="center">
            {forbiddenEmail
              ? <><strong>{forbiddenEmail}</strong> is not authorised to access this application.</>
              : 'You are not authorised to use this application.'}
          </Typography>
          {signOut && (
            <Button variant="contained" onClick={signOut}>Sign Out</Button>
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
        onProfileUpdate={updateProfile}
        playerProfile={playerProfile}
        sessionEmail={sessionEmail}
        history={history}
        isPaused={isPaused}
        onPause={pauseGame}
        onResume={resumeGame}
        onNewGame={() => setNewGameModalOpen(true)}
        onImport={() => setImportModalOpen(true)}
        onDemoGame={DEV_TOOLS ? loadDemoGame : null}
        onDevData={DEV_TOOLS ? () => setDevDataOpen(true) : null}
        colorMode={colorMode}
        onToggleColorMode={handleToggleColorMode}
      />
      <Container maxWidth="lg" disableGutters sx={{ px: { xs: 1, sm: 2, md: 3 }, py: { xs: 1, md: 2 } }}>
        <Stack spacing={{ xs: 1, md: 2 }} alignItems={{ xs: 'center', md: 'flex-start' }}>
          {isLoading && !currentGrid ? (
            <CircularProgress />
          ) : isPaused ? (
            <PauseOverlay onResume={resumeGame} />
          ) : currentGrid ? (
            /* Outer box centres the game column on the page */
            <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              {/* Game column — width driven by the grid, everything else matches it */}
              <Box sx={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'stretch', gap: 1 }}>
                {/* Toolbar row: action buttons only */}
                <NumberPadToolbar
                  onClearCell={clearCell}
                  onUndo={undoLastMove}
                  canUndo={canUndo}
                  onValidate={requestValidation}
                  onHint={requestHint}
                  onFillCandidates={fillCandidates}
                  isLoading={isLoading}
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
                {/* Candidate digit row — tapping switches to candidate mode */}
                <CandidateRow
                  selectedNumber={selectedNumber}
                  inputMode={inputMode}
                  onNumberSelect={handleNumberSelect}
                />
                {/* Normal digit row(s) — tapping switches to normal mode */}
                <NumberPadInput
                  selectedNumber={selectedNumber}
                  inputMode={inputMode}
                  onNumberSelect={handleNumberSelect}
                  completedNumbers={completedNumbers}
                />
                {/* Validation status */}
                <NumberPadStatus
                  gameStatus={gameStatus}
                  statusMessage={statusMessage}
                  onCloseStatus={clearStatus}
                />
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

      {DEV_TOOLS && (
        <DevDataDialog open={devDataOpen} onClose={() => setDevDataOpen(false)} />
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
        <AmplifyThemeProvider theme={amplifyTheme}>
          {authContent}
        </AmplifyThemeProvider>
      ) : authContent}
    </ThemeProvider>
  );
}

function App() {
  if (MOCK_API || SKIP_AUTH) {
    return <SudokuApp user={null} signOut={null} />;
  }

  return <LoginLayout />;
}

export default App;
