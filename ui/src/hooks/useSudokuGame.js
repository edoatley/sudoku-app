import { useState, useCallback, useEffect, useRef } from 'react';
import { validatePuzzle, getHint, getCandidates, createGame, loadGame, saveGame } from '../api/sudokuApi.js';

const LS_KEY_GAME_ID = 'sudoku_gameId';
const LS_KEY_CURRENT_GRID = 'sudoku_currentGrid';
const LS_KEY_CANDIDATE_GRID = 'sudoku_candidateGrid';
const LS_KEY_DIFFICULTY = 'sudoku_difficulty';
const LS_KEY_ELAPSED_SECONDS = 'sudoku_elapsedSeconds';

function lsSave(gameId, currentGrid, candidateGrid, difficulty, elapsedSeconds) {
  try {
    localStorage.setItem(LS_KEY_GAME_ID, gameId);
    localStorage.setItem(LS_KEY_CURRENT_GRID, JSON.stringify(currentGrid));
    localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(candidateGrid));
    localStorage.setItem(LS_KEY_DIFFICULTY, difficulty);
    localStorage.setItem(LS_KEY_ELAPSED_SECONDS, String(elapsedSeconds));
  // eslint-disable-next-line no-unused-vars
  } catch (_) { /* storage full — silently ignore */ }
}

function lsClear() {
  [LS_KEY_GAME_ID, LS_KEY_CURRENT_GRID, LS_KEY_CANDIDATE_GRID, LS_KEY_DIFFICULTY, LS_KEY_ELAPSED_SECONDS]
    .forEach((k) => localStorage.removeItem(k));
}

const emptyCandidate = () => Array(9).fill(null).map(() => Array(9).fill(null).map(() => []));

export function useSudokuGame() {
  const [originalGrid, setOriginalGrid] = useState(null);
  const [currentGrid, setCurrentGrid] = useState(null);
  const [candidateGrid, setCandidateGrid] = useState(null);
  const [autoNotesGrid, setAutoNotesGrid] = useState(null);
  const [autoNotesActive, setAutoNotesActive] = useState(false);
  const [difficulty, setDifficulty] = useState('easy');
  const [errorCells, setErrorCells] = useState(new Set());
  const [activeHint, setActiveHint] = useState(null);
  const [hintStage, setHintStage] = useState('nudge');
  const [highlightCells, setHighlightCells] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);
  const [gameStatus, setGameStatus] = useState('idle');
  const [inputMode, setInputMode] = useState('normal');
  const [selectedNumber, setSelectedNumber] = useState(null);
  const [selectedCell, setSelectedCell] = useState(null);
  const [history, setHistory] = useState([]);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [timerRunning, setTimerRunning] = useState(false);
  const timerRef = useRef(null);
  const [gameId, setGameId] = useState(null);
  const [isSyncing, setIsSyncing] = useState(false);
  const gameIdRef = useRef(null);
  gameIdRef.current = gameId;
  const elapsedSecondsRef = useRef(0);
  elapsedSecondsRef.current = elapsedSeconds;

  const startTimer = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    setElapsedSeconds(0);
    setTimerRunning(true);
    timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
  }, []);

  const pauseTimer = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    setTimerRunning(false);
  }, []);

  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current); }, []);

  const startNewGame = useCallback(async (diff, signal) => {
    const activeDiff = diff ?? difficulty;
    setIsLoading(true);
    setErrorCells(new Set());
    setActiveHint(null);
    setHintStage('nudge');
    setHighlightCells([]);
    setStatusMessage(null);
    setGameStatus('idle');
    setSelectedCell(null);
    setSelectedNumber(null);
    try {
      const data = await createGame(activeDiff, signal);
      const emptyGrid = emptyCandidate();
      setGameId(data.gameId);
      setOriginalGrid(data.originalGrid);
      setCurrentGrid(data.currentGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setAutoNotesGrid(null);
      setAutoNotesActive(false);
      setHistory([]);
      lsSave(data.gameId, data.currentGrid, emptyGrid, activeDiff, 0);
      startTimer();
    } catch (err) {
      if (err.name === 'AbortError') return;
      setStatusMessage(`Failed to load puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [difficulty, startTimer]);

  useEffect(() => {
    const controller = new AbortController();
    const savedGameId = localStorage.getItem(LS_KEY_GAME_ID);
    if (savedGameId) {
      setIsLoading(true);
      loadGame(savedGameId).then((data) => {
        if (controller.signal.aborted) return;
        const savedCandidates = (() => {
          try { return JSON.parse(localStorage.getItem(LS_KEY_CANDIDATE_GRID)) || emptyCandidate(); }
          // eslint-disable-next-line no-unused-vars
          catch (_) { return emptyCandidate(); }
        })();
        const savedElapsed = parseInt(localStorage.getItem(LS_KEY_ELAPSED_SECONDS) || '0', 10);
        setGameId(data.gameId);
        setOriginalGrid(data.originalGrid);
        setCurrentGrid(data.currentGrid.map((row) => [...row]));
        setCandidateGrid(savedCandidates);
        setDifficulty(data.difficulty);
        setAutoNotesGrid(null);
        setAutoNotesActive(false);
        setHistory([]);
        setElapsedSeconds(savedElapsed);
        setTimerRunning(true);
        timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
        setIsLoading(false);
      }).catch(() => {
        if (controller.signal.aborted) return;
        lsClear();
        startNewGame(undefined, controller.signal);
      });
    } else {
      startNewGame(undefined, controller.signal);
    }
    return () => controller.abort();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const currentGridRef = useRef(null);
  currentGridRef.current = currentGrid;
  const candidateGridRef = useRef(null);
  candidateGridRef.current = candidateGrid;

  const difficultyRef = useRef(difficulty);
  difficultyRef.current = difficulty;

  const syncToBackend = useCallback(() => {
    const gid = gameIdRef.current;
    const grid = currentGridRef.current;
    const candidates = candidateGridRef.current;
    if (!gid || !grid) return;
    setIsSyncing(true);
    saveGame(gid, { currentGrid: grid, candidates: candidates ?? [], timeSpentSeconds: elapsedSecondsRef.current })
      .finally(() => setIsSyncing(false));
  }, []);

  useEffect(() => {
    const interval = setInterval(syncToBackend, 60_000);
    return () => clearInterval(interval);
  }, [syncToBackend]);

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        syncToBackend();
        pauseTimer();
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [syncToBackend, pauseTimer]);

  const writeCellValue = useCallback((row, col, number) => {
    if (originalGrid && originalGrid[row][col] !== 0) return;
    if (inputMode === 'normal') {
      const prevValue = currentGridRef.current?.[row][col] ?? 0;
      setHistory((h) => [...h, { type: 'normal', row, col, prevValue }]);
      setCurrentGrid((prev) => {
        const next = prev.map((r) => [...r]);
        next[row][col] = number;
        if (gameIdRef.current) {
          // eslint-disable-next-line no-unused-vars
          try { localStorage.setItem(LS_KEY_CURRENT_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
        }
        return next;
      });
      setCandidateGrid((prev) => {
        const next = prev.map((r) => r.map((c) => [...c]));
        next[row][col] = [];
        if (gameIdRef.current) {
          // eslint-disable-next-line no-unused-vars
          try { localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
        }
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
        if (gameIdRef.current) {
          // eslint-disable-next-line no-unused-vars
          try { localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
        }
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
      if (result.isValid) {
        const allFilled = currentGrid.every((row) => row.every((v) => v !== 0));
        if (allFilled) {
          pauseTimer();
          if (gameIdRef.current) {
            saveGame(gameIdRef.current, {
              currentGrid,
              candidates: candidateGridRef.current ?? [],
              timeSpentSeconds: elapsedSecondsRef.current,
              isComplete: true,
            });
          }
        }
        setGameStatus(allFilled ? 'solved' : 'valid');
        setStatusMessage(allFilled ? null : 'Board is valid so far.');
        setErrorCells(new Set());
      } else {
        setGameStatus('invalid');
        setStatusMessage('The board contains errors.');
        setErrorCells(new Set((result.errors ?? []).map((e) => `${e.row},${e.col}`)));
      }
    } catch (err) {
      setStatusMessage(`Validation failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid, pauseTimer]);

  const requestHint = useCallback(async () => {
    if (!currentGrid) return;
    setIsLoading(true);
    try {
      const hint = await getHint(currentGrid);
      setActiveHint(hint);
      setHintStage('nudge');
      setHighlightCells([]);
    } catch (err) {
      setStatusMessage(`Hint failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid]);

  const advanceHint = useCallback(() => {
    if (!activeHint) return;
    if (hintStage === 'nudge') {
      setHintStage('focus');
      setHighlightCells(activeHint.highlightCells);
    } else if (hintStage === 'focus') {
      setHintStage('reveal');
      if (activeHint.eliminatedCandidates?.length > 0) {
        setCandidateGrid((prev) => {
          const next = prev.map((r) => r.map((c) => [...c]));
          for (const { row, col, value } of activeHint.eliminatedCandidates) {
            const idx = next[row][col].indexOf(value);
            if (idx !== -1) next[row][col].splice(idx, 1);
          }
          return next;
        });
      }
      if (activeHint.solvedCells?.length > 0) {
        setCurrentGrid((prev) => {
          const next = prev.map((r) => [...r]);
          for (const { row, col, value } of activeHint.solvedCells) {
            next[row][col] = value;
          }
          return next;
        });
        setCandidateGrid((prev) => {
          const next = prev.map((r) => r.map((c) => [...c]));
          for (const { row, col } of activeHint.solvedCells) {
            next[row][col] = [];
          }
          return next;
        });
      }
    }
  }, [activeHint, hintStage]);

  const dismissHint = useCallback(() => {
    setActiveHint(null);
    setHintStage('nudge');
    setHighlightCells([]);
  }, []);

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
        setErrorCells(prev => {
          const next = new Set(prev);
          next.delete(`${entry.row},${entry.col}`);
          return next;
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
    setErrorCells(prev => {
      const next = new Set(prev);
      next.delete(`${row},${col}`);
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
    gameId,
    isSyncing,
    errorCells,
    activeHint,
    hintStage,
    highlightCells,
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
    advanceHint,
    dismissHint,
    toggleAutoNotes,
    clearStatus,
    elapsedSeconds,
    timerRunning,
    pauseTimer,
  };
}
