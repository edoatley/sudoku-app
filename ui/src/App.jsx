import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import CircularProgress from '@mui/material/CircularProgress';

import { useSudokuGame } from './hooks/useSudokuGame.js';
import SudokuGrid from './components/SudokuGrid.jsx';
import GameControls from './components/GameControls.jsx';
import StatusBar from './components/StatusBar.jsx';
import NumberPad from './components/NumberPad.jsx';
import HintDialog from './components/HintDialog.jsx';
import Header from './components/Header.jsx';

const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';

// Only import Authenticator when auth is enabled to avoid loading Amplify in mock mode
let Authenticator = null;
if (!MOCK_API) {
  const amplifyUi = await import('@aws-amplify/ui-react');
  await import('@aws-amplify/ui-react/styles.css');
  Authenticator = amplifyUi.Authenticator;
}

const theme = createTheme();

function SudokuApp({ user, signOut }) {
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
    setDifficulty,
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
  } = useSudokuGame(user);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Header
        elapsedSeconds={elapsedSeconds}
        timerRunning={timerRunning}
        gameStarted={!!currentGrid}
        user={user}
        onSignOut={signOut}
      />
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Stack spacing={3} alignItems={{ xs: 'center', md: 'flex-start' }}>
          <StatusBar gameStatus={gameStatus} statusMessage={statusMessage} onClose={clearStatus} />

          <GameControls
            difficulty={difficulty}
            isLoading={isLoading}
            onDifficultyChange={setDifficulty}
            onNewGame={() => startNewGame(difficulty)}
          />

          {isLoading && !currentGrid ? (
            <CircularProgress />
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
    </ThemeProvider>
  );
}

function App() {
  if (MOCK_API) {
    return <SudokuApp user={null} signOut={null} />;
  }

  return (
    <Authenticator hideSignUp>
      {({ signOut, user }) => <SudokuApp user={user} signOut={signOut} />}
    </Authenticator>
  );
}

export default App;
