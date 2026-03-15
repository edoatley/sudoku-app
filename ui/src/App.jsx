import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';

import { useSudokuGame } from './hooks/useSudokuGame.js';
import SudokuGrid from './components/SudokuGrid.jsx';
import GameControls from './components/GameControls.jsx';
import StatusBar from './components/StatusBar.jsx';
import NumberPad from './components/NumberPad.jsx';

const theme = createTheme();

function App() {
  const {
    originalGrid,
    currentGrid,
    candidateGrid,
    difficulty,
    errorCells,
    hintCell,
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
    autoNotesActive,
    selectedCell,
    toggleAutoNotes,
    clearStatus,
  } = useSudokuGame();

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Stack spacing={3} alignItems={{ xs: 'center', md: 'flex-start' }}>
          <Typography variant="h4" component="h1" fontWeight="bold">
            Sudoku
          </Typography>

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
                hintCell={hintCell}
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
        </Stack>
      </Container>
    </ThemeProvider>
  );
}

export default App;
