import { useState, useCallback, useEffect, useRef } from 'react';
import { validatePuzzle, getCandidates, createGame, loadGame, saveGame, getCurrentGame, importPuzzle, createGameFromGrid, getDemoGrid, ForbiddenError } from '../api/sudokuApi.js';
import { useGameTimer } from './useGameTimer.js';
import { useHintSystem } from './useHintSystem.js';
import { useGameSync } from './useGameSync.js';

const LS_KEY_GAME_ID = 'sudoku_gameId';
const LS_KEY_CURRENT_GRID = 'sudoku_currentGrid';
const LS_KEY_CANDIDATE_GRID = 'sudoku_candidateGrid';
const LS_KEY_DIFFICULTY = 'sudoku_difficulty';
const LS_KEY_ELAPSED_SECONDS = 'sudoku_elapsedSeconds';
const LS_KEY_HINTS_USED = 'sudoku_hintsUsed';

function lsSave(gameId, currentGrid, candidateGrid, difficulty, elapsedSeconds, hintsUsed) {
  try {
    localStorage.setItem(LS_KEY_GAME_ID, gameId);
    localStorage.setItem(LS_KEY_CURRENT_GRID, JSON.stringify(currentGrid));
    localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(candidateGrid));
    localStorage.setItem(LS_KEY_DIFFICULTY, difficulty);
    localStorage.setItem(LS_KEY_ELAPSED_SECONDS, String(elapsedSeconds));
    localStorage.setItem(LS_KEY_HINTS_USED, String(hintsUsed ?? 0));
  // eslint-disable-next-line no-unused-vars
  } catch (_) { /* storage full — silently ignore */ }
}

function lsClear() {
  [LS_KEY_GAME_ID, LS_KEY_CURRENT_GRID, LS_KEY_CANDIDATE_GRID, LS_KEY_DIFFICULTY, LS_KEY_ELAPSED_SECONDS, LS_KEY_HINTS_USED]
    .forEach((k) => localStorage.removeItem(k));
}

const emptyCandidate = () => Array(9).fill(null).map(() => Array(9).fill(null).map(() => []));


export function useSudokuGame(user, { onGameComplete, onForbidden } = {}) {
  const [originalGrid, setOriginalGrid] = useState(null);
  const [currentGrid, setCurrentGrid] = useState(null);
  const [candidateGrid, setCandidateGrid] = useState(null);
  const [difficulty, setDifficulty] = useState('easy');
  const [errorCells, setErrorCells] = useState(new Set());
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);
  const [gameStatus, setGameStatus] = useState('idle');
  const [inputMode, setInputMode] = useState('normal');
  const [selectedNumber, setSelectedNumber] = useState(null);
  const [selectedCell, setSelectedCell] = useState(null);
  const [history, setHistory] = useState([]);
  const [gameId, setGameId] = useState(null);
  const [solutionGrid, setSolutionGrid] = useState(null);
  const [hintMinRank, setHintMinRank] = useState(null);
  const [importStage, setImportStage] = useState(null);
  const prevUserIdRef = useRef(user?.username ?? null);

  const gameIdRef = useRef(null);
  gameIdRef.current = gameId;
  const currentGridRef = useRef(null);
  currentGridRef.current = currentGrid;
  const candidateGridRef = useRef(null);
  candidateGridRef.current = candidateGrid;
  const solutionGridRef = useRef(null);
  solutionGridRef.current = solutionGrid;
  const difficultyRef = useRef(difficulty);
  difficultyRef.current = difficulty;
  const hintMinRankRef = useRef(null);
  hintMinRankRef.current = hintMinRank;

  const {
    elapsedSeconds,
    timerRunning,
    isPaused,
    setIsPaused,
    startTimer,
    pauseTimer,
    pauseGame,
    resumeGame,
    resumeTimerIfActive,
    restoreTimer,
  } = useGameTimer();

  const elapsedSecondsRef = useRef(0);
  elapsedSecondsRef.current = elapsedSeconds;

  const {
    activeHint,
    hintStage,
    highlightCells,
    setHighlightCells,
    hintsUsed,
    setHintsUsed,
    resetHintState,
    requestHint,
    requestAlternateHint,
    advanceHint,
    dismissHint,
  } = useHintSystem({
    currentGridRef,
    hintMinRankRef,
    onForbidden,
    setStatusMessage,
    setGameStatus,
    setIsLoading,
    setCandidateGrid,
    setCurrentGrid,
  });

  const hintsUsedRef = useRef(0);
  hintsUsedRef.current = hintsUsed;

  const gameActiveRef = useRef(false);
  gameActiveRef.current = !!currentGrid && !isPaused;

  const { isSyncing } = useGameSync({
    gameIdRef,
    currentGridRef,
    candidateGridRef,
    elapsedSecondsRef,
    hintsUsedRef,
    pauseTimer,
    resumeTimerIfActive,
    gameActiveRef,
  });

  // Clear localStorage game state when the authenticated user changes
  useEffect(() => {
    const currentUserId = user?.username ?? null;
    if (prevUserIdRef.current !== currentUserId) {
      prevUserIdRef.current = currentUserId;
      lsClear();
    }
  }, [user]);

  const startNewGame = useCallback(async (diff, signal) => {
    const activeDiff = diff ?? difficulty;
    setIsLoading(true);
    setErrorCells(new Set());
    setStatusMessage(null);
    setGameStatus('idle');
    setSelectedCell(null);
    setSelectedNumber(null);
    resetHintState();
    try {
      const data = await createGame(activeDiff, signal);
      const emptyGrid = emptyCandidate();
      setGameId(data.gameId);
      setOriginalGrid(data.originalGrid);
      setSolutionGrid(data.solutionGrid);
      setCurrentGrid(data.currentGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setHistory([]);
      setHintMinRank(null);
      setDifficulty(activeDiff);
      lsSave(data.gameId, data.currentGrid, emptyGrid, activeDiff, 0, 0);
      startTimer();
    } catch (err) {
      if (err.name === 'AbortError') return;
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Failed to load puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [difficulty, startTimer, onForbidden, resetHintState]);

  useEffect(() => {
    const controller = new AbortController();

    const applyLoadedGame = (data, savedCandidates, savedElapsed, savedHints) => {
      setGameId(data.gameId);
      setOriginalGrid(data.originalGrid);
      setSolutionGrid(data.solutionGrid);
      setCurrentGrid(data.currentGrid.map((row) => [...row]));
      setCandidateGrid(savedCandidates);
      setDifficulty(data.difficulty);
      setHistory([]);
      setHintsUsed(savedHints);
      restoreTimer(savedElapsed);
      setIsLoading(false);
    };

    const savedGameId = localStorage.getItem(LS_KEY_GAME_ID);
    if (savedGameId) {
      setIsLoading(true);
      loadGame(savedGameId).then((data) => {
        if (controller.signal.aborted) return;
        if (data.status !== 'IN_PROGRESS') {
          // Game was abandoned or completed on another device — discard the stale
          // localStorage entry and let the authenticated-user path find the real
          // active game, or start a new one if none exists.
          lsClear();
          if (user) {
            return getCurrentGame().then((current) => {
              if (controller.signal.aborted) return;
              if (current) {
                const restoredCandidates = Array.isArray(current.candidates) && current.candidates.length === 9
                  ? current.candidates
                  : emptyCandidate();
                applyLoadedGame(current, restoredCandidates, current.timeSpentSeconds ?? 0, current.hintsUsed ?? 0);
                lsSave(current.gameId, current.currentGrid, restoredCandidates, current.difficulty, current.timeSpentSeconds ?? 0, current.hintsUsed ?? 0);
              } else {
                setIsLoading(false);
                startNewGame(undefined, controller.signal);
              }
            }).catch(() => {
              if (controller.signal.aborted) return;
              setIsLoading(false);
            });
          }
          setIsLoading(false);
          startNewGame(undefined, controller.signal);
          return;
        }
        const savedCandidates = (() => {
          try { return JSON.parse(localStorage.getItem(LS_KEY_CANDIDATE_GRID)) || emptyCandidate(); }
          // eslint-disable-next-line no-unused-vars
          catch (_) { return emptyCandidate(); }
        })();
        const lsElapsed = parseInt(localStorage.getItem(LS_KEY_ELAPSED_SECONDS) || '0', 10);
        const savedElapsed = Math.max(lsElapsed, data.timeSpentSeconds ?? 0);
        const lsHints = parseInt(localStorage.getItem(LS_KEY_HINTS_USED) || '0', 10);
        const savedHints = Math.max(lsHints, data.hintsUsed ?? 0);
        applyLoadedGame(data, savedCandidates, savedElapsed, savedHints);
        lsSave(data.gameId, data.currentGrid, savedCandidates, data.difficulty, savedElapsed, savedHints);
      }).catch(() => {
        if (controller.signal.aborted) return;
        lsClear();
        setIsLoading(false);
      });
    } else if (user) {
      setIsLoading(true);
      getCurrentGame().then((data) => {
        if (controller.signal.aborted) return;
        if (data) {
          const restoredCandidates = Array.isArray(data.candidates) && data.candidates.length === 9
            ? data.candidates
            : emptyCandidate();
          const restoredHints = data.hintsUsed ?? 0;
          applyLoadedGame(data, restoredCandidates, data.timeSpentSeconds ?? 0, restoredHints);
          lsSave(data.gameId, data.currentGrid, restoredCandidates, data.difficulty, data.timeSpentSeconds ?? 0, restoredHints);
        } else {
          setIsLoading(false);
          startNewGame(undefined, controller.signal);
        }
      }).catch(() => {
        if (controller.signal.aborted) return;
        setIsLoading(false);
      });
    } else {
      // No saved game and no authenticated user (e.g. VITE_SKIP_AUTH=true in integration tests).
      // Auto-start a new game so the grid is immediately available.
      startNewGame(undefined, controller.signal);
    }
    return () => controller.abort();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inactivityRef = useRef(null);

  useEffect(() => {
    const INACTIVITY_MS = 3 * 60 * 1000;
    const resetTimer = () => {
      if (inactivityRef.current) clearTimeout(inactivityRef.current);
      inactivityRef.current = setTimeout(() => {
        if (gameActiveRef.current) {
          pauseGame();
        }
      }, INACTIVITY_MS);
    };
    resetTimer();
    document.addEventListener('mousemove', resetTimer);
    document.addEventListener('keydown', resetTimer);
    return () => {
      if (inactivityRef.current) clearTimeout(inactivityRef.current);
      document.removeEventListener('mousemove', resetTimer);
      document.removeEventListener('keydown', resetTimer);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const writeCellValue = useCallback((row, col, number) => {
    if (originalGrid && originalGrid[row][col] !== 0) return;
    if (inputMode === 'normal') {
      const prevValue = currentGridRef.current?.[row][col] ?? 0;
      const prevCandidates = [...(candidateGridRef.current?.[row][col] ?? [])];
      setHistory((h) => [...h, { type: 'normal', row, col, prevValue, prevCandidates }]);
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
      const nextGrid = currentGridRef.current.map((r) => [...r]);
      nextGrid[row][col] = number;
      const allFilled = nextGrid.every((r) => r.every((v) => v !== 0));
      if (allFilled) {
        validatePuzzle(nextGrid, solutionGridRef.current).then((res) => {
          if (!res.isValid) return;
          pauseTimer();
          setGameStatus('solved');
          setStatusMessage(null);
          setErrorCells(new Set());
          if (gameIdRef.current) {
            saveGame(gameIdRef.current, {
              currentGrid: nextGrid,
              candidates: candidateGridRef.current ?? [],
              timeSpentSeconds: elapsedSecondsRef.current,
              isComplete: true,
            });
          }
        // eslint-disable-next-line no-unused-vars
        }).catch((_) => { /* silent — user can still manually check */ });
      }
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
  }, [inputMode, originalGrid, pauseTimer, elapsedSecondsRef]);

  const updateCell = useCallback((row, col) => {
    if (selectedCell?.row === row && selectedCell?.col === col && selectedNumber === null) {
      setSelectedCell(null);
      return;
    }
    setSelectedCell({ row, col });
    if (selectedNumber !== null) {
      writeCellValue(row, col, selectedNumber);
      setSelectedNumber(null);
    }
  }, [selectedCell, selectedNumber, writeCellValue]);

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
      const result = await validatePuzzle(currentGrid, solutionGrid);
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
        const errorCount = (result.errors ?? []).length;
        const errorWord = errorCount === 1 ? 'error' : 'errors';
        setGameStatus('invalid');
        setStatusMessage(`The board has ${errorCount} ${errorWord}. Check the highlighted cells.`);
        setErrorCells(new Set((result.errors ?? []).map((e) => `${e.row},${e.col}`)));
      }
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Validation failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid, solutionGrid, pauseTimer, onForbidden, elapsedSecondsRef]);

  const fillCandidates = useCallback(async () => {
    if (!currentGrid) return;
    setIsLoading(true);
    try {
      const result = await getCandidates(currentGrid);
      const fetchedGrid = result.candidatesGrid;
      const snapshot = candidateGridRef.current.map((r) => r.map((c) => [...c]));
      setHistory((h) => [...h, { type: 'populateCandidates', prevCandidateGrid: snapshot }]);
      setCandidateGrid(() => {
        const next = fetchedGrid.map((row, r) =>
          row.map((cell, c) => (currentGrid[r][c] === 0 ? [...cell] : []))
        );
        if (gameIdRef.current) {
          // eslint-disable-next-line no-unused-vars
          try { localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
        }
        return next;
      });
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Fill candidates failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid, onForbidden]);

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
          ng[entry.row][entry.col] = entry.prevCandidates ?? [];
          return ng;
        });
        setErrorCells(prev => {
          const next = new Set(prev);
          next.delete(`${entry.row},${entry.col}`);
          return next;
        });
      } else if (entry.type === 'populateCandidates') {
        setCandidateGrid(() => entry.prevCandidateGrid.map((r) => r.map((c) => [...c])));
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

  const clearCell = useCallback((row, col) => {
    if (row == null || col == null) return;
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
  }, [originalGrid]);

  const clearStatus = useCallback(() => {
    setStatusMessage(null);
    setGameStatus('idle');
  }, []);

  const finishGame = useCallback(() => {
    const outcome = gameStatus === 'solved' ? 'won' : 'abandoned';
    onGameComplete?.({
      gameId: gameIdRef.current,
      difficulty: difficultyRef.current,
      outcome,
      elapsedSeconds: outcome === 'won' ? elapsedSecondsRef.current : null,
      hintsUsed,
    });
    pauseTimer();
    setIsPaused(false);
    lsClear();
    setGameId(null);
    setOriginalGrid(null);
    setCurrentGrid(null);
    setCandidateGrid(null);
    setHistory([]);
    setErrorCells(new Set());
    setGameStatus('idle');
    setStatusMessage(null);
    setSelectedCell(null);
    setSelectedNumber(null);
  }, [pauseTimer, setIsPaused, gameStatus, onGameComplete, hintsUsed, elapsedSecondsRef]);

  const startNewGameFromImage = useCallback(async (imageFile) => {
    setIsLoading(true);
    setImportStage('uploading');
    setErrorCells(new Set());
    setStatusMessage(null);
    setGameStatus('idle');
    setSelectedCell(null);
    setSelectedNumber(null);
    resetHintState();
    try {
      const { originalGrid: importedGrid } = await importPuzzle(imageFile);
      setImportStage('analysing');
      const data = await createGameFromGrid(importedGrid);
      const emptyGrid = emptyCandidate();
      setGameId(data.gameId);
      setOriginalGrid(data.originalGrid);
      setSolutionGrid(data.solutionGrid ?? null);
      setCurrentGrid(data.currentGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setHistory([]);
      setHintMinRank(null);
      lsSave(data.gameId, data.currentGrid, emptyGrid, 'imported', 0, 0);
      startTimer();
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Failed to import puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
      setImportStage(null);
    }
  }, [startTimer, onForbidden, resetHintState]);

  const loadDemoGame = useCallback(async (technique) => {
    setIsLoading(true);
    setErrorCells(new Set());
    setStatusMessage(null);
    setGameStatus('idle');
    setSelectedCell(null);
    setSelectedNumber(null);
    resetHintState();
    try {
      const data = await getDemoGrid(technique);
      const emptyGrid = emptyCandidate();
      // Demo games have no game ID — they are not saved to the backend
      setGameId(null);
      setOriginalGrid(data.originalGrid);
      setCurrentGrid(data.originalGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setHistory([]);
      setHintMinRank(data.minRank ?? null);
      lsClear();
      startTimer();
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Failed to load demo: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [startTimer, onForbidden, resetHintState]);

  const handleSetDifficulty = useCallback((diff) => {
    setDifficulty(diff);
  }, []);

  return {
    originalGrid,
    currentGrid,
    candidateGrid,
    difficulty,
    gameId,
    isSyncing,
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
    selectedCell,
    setSelectedCell,
    writeCellValue,
    setInputMode,
    setSelectedNumber,
    handleNumberSelect,
    setDifficulty: handleSetDifficulty,
    startNewGame,
    startNewGameFromImage,
    loadDemoGame,
    updateCell,
    clearCell,
    undoLastMove,
    canUndo: history.length > 0,
    requestValidation,
    hintsUsed,
    requestHint,
    requestAlternateHint,
    advanceHint,
    dismissHint,
    fillCandidates,
    clearStatus,
    finishGame,
    elapsedSeconds,
    timerRunning,
    pauseTimer,
    isPaused,
    pauseGame,
    resumeGame,
    importStage,
  };
}
