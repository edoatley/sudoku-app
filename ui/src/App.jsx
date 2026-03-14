import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Container from '@mui/material/Container';
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
    setSelectedNumber,
    setDifficulty,
    startNewGame,
    updateCell,
    requestValidation,
    requestHint,
    clearStatus,
  } = useSudokuGame();

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <Stack spacing={3} alignItems="center">
          <Typography variant="h4" component="h1" fontWeight="bold">
            Sudoku
          </Typography>

          <StatusBar gameStatus={gameStatus} statusMessage={statusMessage} onClose={clearStatus} />

          <GameControls
            difficulty={difficulty}
            isLoading={isLoading}
            onDifficultyChange={setDifficulty}
            onNewGame={() => startNewGame(difficulty)}
            onValidate={requestValidation}
            onHint={requestHint}
          />

          {isLoading && !currentGrid ? (
            <CircularProgress />
          ) : (
            <>
              <SudokuGrid
                originalGrid={originalGrid}
                currentGrid={currentGrid}
                candidateGrid={candidateGrid}
                errorCells={errorCells}
                hintCell={hintCell}
                onCellClick={updateCell}
              />
              <NumberPad
                selectedNumber={selectedNumber}
                inputMode={inputMode}
                onNumberSelect={setSelectedNumber}
                onModeChange={setInputMode}
              />
            </>
          )}
        </Stack>
      </Container>
    </ThemeProvider>
  );
}

export default App;
