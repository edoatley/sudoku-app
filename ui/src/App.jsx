import { useState } from 'react';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import CircularProgress from '@mui/material/CircularProgress';
import Paper from '@mui/material/Paper';

import { useSudokuGame } from './hooks/useSudokuGame.js';
import SudokuGrid from './components/SudokuGrid.jsx';
import GameControls from './components/GameControls.jsx';
import StatusBar from './components/StatusBar.jsx';
import NumberPad from './components/NumberPad.jsx';
import HintDialog from './components/HintDialog.jsx';
import Header from './components/Header.jsx';
import PauseOverlay from './components/PauseOverlay.jsx';
import NewGameModal from './components/NewGameModal.jsx';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';

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

const muiTheme = createTheme();

function SudokuApp({ user, signOut }) {
  const [newGameModalOpen, setNewGameModalOpen] = useState(false);

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
    setInputMode,
    handleNumberSelect,
    startNewGame,
    updateCell,
    clearCell,
    undoLastMove,
    canUndo,
    requestValidation,
    requestHint,
    advanceHint,
    dismissHint,
    autoNotesActive,
    selectedCell,
    toggleAutoNotes,
    clearStatus,
    elapsedSeconds,
    timerRunning,
    isPaused,
    pauseGame,
    resumeGame,
  } = useSudokuGame(user);

  const handleNewGameConfirm = (selectedDifficulty) => {
    setNewGameModalOpen(false);
    startNewGame(selectedDifficulty);
  };

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <Header
        elapsedSeconds={elapsedSeconds}
        timerRunning={timerRunning}
        gameStarted={!!currentGrid}
        user={user}
        onSignOut={signOut}
        isPaused={isPaused}
        onPause={pauseGame}
        onResume={resumeGame}
      />
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Stack spacing={3} alignItems={{ xs: 'center', md: 'flex-start' }}>
          <StatusBar gameStatus={gameStatus} statusMessage={statusMessage} onClose={clearStatus} />

          <GameControls onOpenNewGame={() => setNewGameModalOpen(true)} />

          {isLoading && !currentGrid ? (
            <CircularProgress />
          ) : isPaused ? (
            <PauseOverlay onResume={resumeGame} />
          ) : (
            <Box sx={{
              display: 'flex',
              flexDirection: { xs: 'column', md: 'row' },
              gap: 3,
              alignItems: { xs: 'center', md: 'flex-start' },
            }}>
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
              <NumberPad
                selectedNumber={selectedNumber}
                inputMode={inputMode}
                onNumberSelect={handleNumberSelect}
                onModeChange={setInputMode}
                onClearCell={clearCell}
                onUndo={undoLastMove}
                canUndo={canUndo}
                onValidate={requestValidation}
                onHint={requestHint}
                autoNotesActive={autoNotesActive}
                onAutoNotes={toggleAutoNotes}
                isLoading={isLoading}
              />
            </Box>
          )}
          <HintDialog
            open={!!activeHint}
            hint={activeHint}
            stage={hintStage}
            onAdvance={advanceHint}
            onDismiss={dismissHint}
          />
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
    </ThemeProvider>
  );
}

function LoginLayout() {
  const authContent = (
    <Authenticator hideSignUp socialProviders={['google']}>
      {({ signOut, user }) => <SudokuApp user={user} signOut={signOut} />}
    </Authenticator>
  );

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <Header minimal />
      <Box
        sx={{
          minHeight: 'calc(100vh - 64px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'grey.50',
          p: 2,
        }}
      >
        <Paper
          elevation={3}
          sx={{
            p: 4,
            borderRadius: 3,
            width: '100%',
            maxWidth: 460,
          }}
        >
          {AmplifyThemeProvider && amplifyTheme ? (
            <AmplifyThemeProvider theme={amplifyTheme}>
              {authContent}
            </AmplifyThemeProvider>
          ) : authContent}
        </Paper>
      </Box>
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
