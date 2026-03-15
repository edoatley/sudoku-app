import { useState, useCallback, useEffect, useRef } from 'react';
import { generatePuzzle, validatePuzzle, getHint, getCandidates } from '../api/sudokuApi.js';

const emptyCandidate = () => Array(9).fill(null).map(() => Array(9).fill(null).map(() => []));

export function useSudokuGame() {
  const [originalGrid, setOriginalGrid] = useState(null);
  const [currentGrid, setCurrentGrid] = useState(null);
  const [candidateGrid, setCandidateGrid] = useState(null);
  const [autoNotesGrid, setAutoNotesGrid] = useState(null);
  const [autoNotesActive, setAutoNotesActive] = useState(false);
  const [difficulty, setDifficulty] = useState('easy');
  const [errorCells, setErrorCells] = useState(new Set());
  const [hintCell, setHintCell] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);
  const [gameStatus, setGameStatus] = useState('idle');
  const [inputMode, setInputMode] = useState('normal');
  const [selectedNumber, setSelectedNumber] = useState(null);
  const [selectedCell, setSelectedCell] = useState(null);
  const [history, setHistory] = useState([]);
  const hintTimerRef = useRef(null);

  const startNewGame = useCallback(async (diff) => {
    const activeDiff = diff ?? difficulty;
    setIsLoading(true);
    setErrorCells(new Set());
    setHintCell(null);
    setStatusMessage(null);
    setGameStatus('idle');
    setSelectedCell(null);
    setSelectedNumber(null);
    try {
      const data = await generatePuzzle(activeDiff);
      setOriginalGrid(data.originalGrid);
      setCurrentGrid(data.originalGrid.map((row) => [...row]));
      setCandidateGrid(emptyCandidate());
      setAutoNotesGrid(null);
      setAutoNotesActive(false);
      setHistory([]);
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

  const currentGridRef = useRef(null);
  currentGridRef.current = currentGrid;
  const candidateGridRef = useRef(null);
  candidateGridRef.current = candidateGrid;

  const writeCellValue = useCallback((row, col, number) => {
    if (originalGrid && originalGrid[row][col] !== 0) return;
    if (inputMode === 'normal') {
      const prevValue = currentGridRef.current?.[row][col] ?? 0;
      setHistory((h) => [...h, { type: 'normal', row, col, prevValue }]);
      setCurrentGrid((prev) => {
        const next = prev.map((r) => [...r]);
        next[row][col] = number;
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
      const prevCandidates = [...(candidateGridRef.current?.[row][col] ?? [])];
      setHistory((h) => [...h, { type: 'candidate', row, col, prevCandidates }]);
      setCandidateGrid((prev) => {
        const next = prev.map((r) => r.map((c) => [...c]));
        const cell = next[row][col];
        const idx = cell.indexOf(number);
        if (idx === -1) cell.push(number);
        else cell.splice(idx, 1);
        return next;
      });
    }
  }, [inputMode, originalGrid]);

  const updateCell = useCallback((row, col) => {
    setSelectedCell({ row, col });
    if (selectedNumber !== null) {
      writeCellValue(row, col, selectedNumber);
      setSelectedNumber(null);
    }
  }, [selectedNumber, writeCellValue]);

  const handleNumberSelect = useCallback((n) => {
    if (n === null) { setSelectedNumber(null); return; }
    if (selectedCell) {
      writeCellValue(selectedCell.row, selectedCell.col, n);
      setSelectedNumber(null);
    } else {
      setSelectedNumber(n);
    }
  }, [selectedCell, writeCellValue]);

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

  const toggleAutoNotes = useCallback(async () => {
    if (!currentGrid) return;
    if (autoNotesActive) {
      setAutoNotesActive(false);
      return;
    }
    // Activate: fetch if not already loaded
    if (!autoNotesGrid) {
      setIsLoading(true);
      try {
        const result = await getCandidates(currentGrid);
        setAutoNotesGrid(result.candidatesGrid);
      } catch (err) {
        setStatusMessage(`Auto-notes failed: ${err.message}`);
        setGameStatus('error');
        return;
      } finally {
        setIsLoading(false);
      }
    }
    setAutoNotesActive(true);
  }, [currentGrid, autoNotesActive, autoNotesGrid]);

  const undoLastMove = useCallback(() => {
    setHistory((prev) => {
      if (prev.length === 0) return prev;
      const next = [...prev];
      const entry = next.pop();
      if (entry.type === 'normal') {
        setCurrentGrid((g) => {
          const ng = g.map((r) => [...r]);
          ng[entry.row][entry.col] = entry.prevValue;
          return ng;
        });
        setCandidateGrid((g) => {
          const ng = g.map((r) => r.map((c) => [...c]));
          ng[entry.row][entry.col] = [];
          return ng;
        });
      } else {
        setCandidateGrid((g) => {
          const ng = g.map((r) => r.map((c) => [...c]));
          ng[entry.row][entry.col] = entry.prevCandidates;
          return ng;
        });
      }
      return next;
    });
  }, []);

  const clearCell = useCallback(() => {
    if (!selectedCell) return;
    const { row, col } = selectedCell;
    if (originalGrid[row][col] !== 0) return;
    setCurrentGrid(prev => {
      const next = prev.map(r => [...r]);
      next[row][col] = 0;
      return next;
    });
    setCandidateGrid(prev => {
      const next = prev.map(r => [...r]);
      next[row][col] = [];
      return next;
    });
  }, [selectedCell, originalGrid]);

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
    candidateGrid: autoNotesActive ? autoNotesGrid : candidateGrid,
    difficulty,
    errorCells,
    hintCell,
    isLoading,
    statusMessage,
    gameStatus,
    inputMode,
    selectedNumber,
    autoNotesActive,
    selectedCell,
    setInputMode,
    setSelectedNumber,
    handleNumberSelect,
    setDifficulty: handleSetDifficulty,
    startNewGame,
    updateCell,
    clearCell,
    undoLastMove,
    canUndo: history.length > 0,
    requestValidation,
    requestHint,
    toggleAutoNotes,
    clearStatus,
  };
}
