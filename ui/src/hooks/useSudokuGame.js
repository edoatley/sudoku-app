import { useState, useCallback, useEffect, useRef } from 'react';
import { generatePuzzle, validatePuzzle, getHint } from '../api/sudokuApi.js';

const emptyCandidate = () => Array(9).fill(null).map(() => Array(9).fill(null).map(() => []));

export function useSudokuGame() {
  const [originalGrid, setOriginalGrid] = useState(null);
  const [currentGrid, setCurrentGrid] = useState(null);
  const [candidateGrid, setCandidateGrid] = useState(null);
  const [difficulty, setDifficulty] = useState('easy');
  const [errorCells, setErrorCells] = useState(new Set());
  const [hintCell, setHintCell] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);
  const [gameStatus, setGameStatus] = useState('idle');
  const [inputMode, setInputMode] = useState('normal');
  const [selectedNumber, setSelectedNumber] = useState(1);
  const hintTimerRef = useRef(null);

  const startNewGame = useCallback(async (diff) => {
    const activeDiff = diff ?? difficulty;
    setIsLoading(true);
    setErrorCells(new Set());
    setHintCell(null);
    setStatusMessage(null);
    setGameStatus('idle');
    try {
      const data = await generatePuzzle(activeDiff);
      setOriginalGrid(data.grid);
      setCurrentGrid(data.grid.map((row) => [...row]));
      setCandidateGrid(emptyCandidate());
    } catch (err) {
      setStatusMessage(`Failed to load puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [difficulty]);

  useEffect(() => {
    startNewGame();
    return () => {
      if (hintTimerRef.current) clearTimeout(hintTimerRef.current);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateCell = useCallback((row, col) => {
    if (selectedNumber === null) return;
    if (inputMode === 'normal') {
      setCurrentGrid((prev) => {
        const next = prev.map((r) => [...r]);
        next[row][col] = selectedNumber;
        return next;
      });
      setCandidateGrid((prev) => {
        const next = prev.map((r) => r.map((c) => [...c]));
        next[row][col] = [];
        return next;
      });
      setErrorCells((prev) => {
        const next = new Set(prev);
        next.delete(`${row},${col}`);
        return next;
      });
    } else {
      setCandidateGrid((prev) => {
        const next = prev.map((r) => r.map((c) => [...c]));
        const cell = next[row][col];
        const idx = cell.indexOf(selectedNumber);
        if (idx === -1) cell.push(selectedNumber);
        else cell.splice(idx, 1);
        return next;
      });
    }
  }, [inputMode, selectedNumber]);

  const requestValidation = useCallback(async () => {
    if (!currentGrid) return;
    setIsLoading(true);
    try {
      const result = await validatePuzzle(currentGrid);
      if (result.valid) {
        const allFilled = currentGrid.every((row) => row.every((v) => v !== 0));
        setGameStatus(allFilled ? 'solved' : 'valid');
        setStatusMessage(allFilled ? null : result.message);
        setErrorCells(new Set());
      } else {
        setGameStatus('invalid');
        setStatusMessage(result.message);
        setErrorCells(new Set((result.errors ?? []).map((e) => `${e.row},${e.col}`)));
      }
    } catch (err) {
      setStatusMessage(`Validation failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid]);

  const requestHint = useCallback(async () => {
    if (!currentGrid) return;
    setIsLoading(true);
    try {
      const hint = await getHint(currentGrid);
      const { row, col } = hint.coordinate;
      const value = hint.value;
      setCurrentGrid((prev) => {
        const next = prev.map((r) => [...r]);
        next[row][col] = value;
        return next;
      });
      setHintCell({ row, col, value });
      if (hintTimerRef.current) clearTimeout(hintTimerRef.current);
      hintTimerRef.current = setTimeout(() => setHintCell(null), 2000);
    } catch (err) {
      setStatusMessage(`Hint failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid]);

  const clearStatus = useCallback(() => {
    setStatusMessage(null);
    setGameStatus('idle');
  }, []);

  const handleSetDifficulty = useCallback((diff) => {
    setDifficulty(diff);
  }, []);

  return {
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
    setDifficulty: handleSetDifficulty,
    startNewGame,
    updateCell,
    requestValidation,
    requestHint,
    clearStatus,
  };
}
