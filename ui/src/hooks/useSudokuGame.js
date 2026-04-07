import { useState, useCallback, useEffect, useRef } from 'react';
import { validatePuzzle, getHint, getCandidates, createGame, loadGame, saveGame, getCurrentGame, importPuzzle, createGameFromGrid, getDemoGrid, ForbiddenError } from '../api/sudokuApi.js';

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

function getPeers(row, col) {
  const set = new Set();
  for (let c = 0; c < 9; c++) { if (c !== col) set.add(`${row},${c}`); }
  for (let r = 0; r < 9; r++) { if (r !== row) set.add(`${r},${col}`); }
  const br = Math.floor(row / 3) * 3, bc = Math.floor(col / 3) * 3;
  for (let r = br; r < br + 3; r++)
    for (let c = bc; c < bc + 3; c++)
      if (r !== row || c !== col) set.add(`${r},${c}`);
  return [...set].map((k) => k.split(',').map(Number));
}

export function useSudokuGame(user, { onGameComplete, onForbidden } = {}) {
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
  const [hintMinRank, setHintMinRank] = useState(null);
  const prevUserIdRef = useRef(user?.username ?? null);
  const [isSyncing, setIsSyncing] = useState(false);
  const gameIdRef = useRef(null);
  gameIdRef.current = gameId;
  const elapsedSecondsRef = useRef(0);
  elapsedSecondsRef.current = elapsedSeconds;

  const [isPaused, setIsPaused] = useState(false);
  const isPausedRef = useRef(false);
  isPausedRef.current = isPaused;

  const startTimer = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    setElapsedSeconds(0);
    setTimerRunning(true);
    setIsPaused(false);
    timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
  }, []);

  const pauseTimer = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setTimerRunning(false);
  }, []);

  const pauseGame = useCallback(() => {
    pauseTimer();
    setIsPaused(true);
  }, [pauseTimer]);

  const resumeGame = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    setIsPaused(false);
    setTimerRunning(true);
    timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
  }, []);

  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current); }, []);

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
      setHintMinRank(null);
      lsSave(data.gameId, data.currentGrid, emptyGrid, activeDiff, 0);
      startTimer();
    } catch (err) {
      if (err.name === 'AbortError') return;
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Failed to load puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [difficulty, startTimer, onForbidden]);

  useEffect(() => {
    const controller = new AbortController();

    const applyLoadedGame = (data, savedCandidates, savedElapsed) => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      setIsPaused(false);
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
    };

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
        const lsElapsed = parseInt(localStorage.getItem(LS_KEY_ELAPSED_SECONDS) || '0', 10);
        const savedElapsed = Math.max(lsElapsed, data.timeSpentSeconds ?? 0);
        applyLoadedGame(data, savedCandidates, savedElapsed);
        lsSave(data.gameId, data.currentGrid, savedCandidates, data.difficulty, savedElapsed);
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
          applyLoadedGame(data, restoredCandidates, data.timeSpentSeconds ?? 0);
          lsSave(data.gameId, data.currentGrid, restoredCandidates, data.difficulty, data.timeSpentSeconds ?? 0);
        } else {
          startNewGame(undefined, controller.signal);
        }
      }).catch(() => {
        if (controller.signal.aborted) return;
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
  const autoNotesGridRef = useRef(null);
  autoNotesGridRef.current = autoNotesGrid;

  const difficultyRef = useRef(difficulty);
  difficultyRef.current = difficulty;

  const syncToBackend = useCallback(() => {
    const gid = gameIdRef.current;
    const grid = currentGridRef.current;
    const candidates = candidateGridRef.current;
    if (!gid || !grid) return;
    // eslint-disable-next-line no-unused-vars
    try { localStorage.setItem(LS_KEY_ELAPSED_SECONDS, String(elapsedSecondsRef.current)); } catch (_) { /* storage full */ }
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
      } else if (document.visibilityState === 'visible') {
        if (gameActiveRef.current && !isPausedRef.current) {
          if (timerRef.current) clearInterval(timerRef.current);
          setTimerRunning(true);
          timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
        }
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [syncToBackend, pauseTimer]);

  const inactivityRef = useRef(null);
  const gameActiveRef = useRef(false);
  gameActiveRef.current = !!currentGrid && !isPaused;

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
      const prevAutoNotes = [...(autoNotesGridRef.current?.[row][col] ?? [])];
      const peers = getPeers(row, col);
      const prevPeerCandidates = {};
      const prevPeerAutoNotes = {};
      for (const [pr, pc] of peers) {
        const pCands = candidateGridRef.current?.[pr][pc] ?? [];
        if (pCands.includes(number)) prevPeerCandidates[`${pr},${pc}`] = [...pCands];
        const pAuto = autoNotesGridRef.current?.[pr][pc] ?? [];
        if (pAuto.includes(number)) prevPeerAutoNotes[`${pr},${pc}`] = [...pAuto];
      }
      setHistory((h) => [...h, { type: 'normal', row, col, prevValue, prevCandidates, prevAutoNotes, prevPeerCandidates, prevPeerAutoNotes }]);
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
        for (const [pr, pc] of peers) {
          const idx = next[pr][pc].indexOf(number);
          if (idx !== -1) next[pr][pc].splice(idx, 1);
        }
        if (gameIdRef.current) {
          // eslint-disable-next-line no-unused-vars
          try { localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
        }
        return next;
      });
      setAutoNotesGrid((prev) => {
        if (!prev) return prev;
        const next = prev.map((r) => r.map((c) => [...c]));
        next[row][col] = [];
        for (const [pr, pc] of peers) {
          const idx = next[pr][pc].indexOf(number);
          if (idx !== -1) next[pr][pc].splice(idx, 1);
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
        validatePuzzle(nextGrid).then((res) => {
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
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Validation failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid, pauseTimer, onForbidden]);

  const requestHint = useCallback(async () => {
    if (!currentGrid) return;
    setIsLoading(true);
    try {
      const hint = await getHint(currentGrid, hintMinRank);
      setActiveHint(hint);
      setHintStage('nudge');
      setHighlightCells([]);
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Hint failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [currentGrid, hintMinRank, onForbidden]);

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
        if (err instanceof ForbiddenError) { onForbidden?.(); return; }
        setStatusMessage(`Auto-notes failed: ${err.message}`);
        setGameStatus('error');
        return;
      } finally {
        setIsLoading(false);
      }
    }
    setAutoNotesActive(true);
  }, [currentGrid, autoNotesActive, autoNotesGrid, onForbidden]);

  const populateUserCandidates = useCallback(() => {
    if (!autoNotesGrid || !currentGrid) return;
    const snapshot = candidateGridRef.current.map((r) => r.map((c) => [...c]));
    setHistory((h) => [...h, { type: 'populateCandidates', prevCandidateGrid: snapshot }]);
    setCandidateGrid((prev) => {
      const next = prev.map((r) => r.map((c) => [...c]));
      for (let r = 0; r < 9; r++) {
        for (let c = 0; c < 9; c++) {
          if (currentGrid[r][c] === 0) next[r][c] = [...(autoNotesGrid[r][c] ?? [])];
        }
      }
      if (gameIdRef.current) {
        // eslint-disable-next-line no-unused-vars
        try { localStorage.setItem(LS_KEY_CANDIDATE_GRID, JSON.stringify(next)); } catch (_) { /* storage full */ }
      }
      return next;
    });
  }, [autoNotesGrid, currentGrid]);

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
          if (entry.prevPeerCandidates) {
            for (const [key, cands] of Object.entries(entry.prevPeerCandidates)) {
              const [pr, pc] = key.split(',').map(Number);
              ng[pr][pc] = [...cands];
            }
          }
          return ng;
        });
        setAutoNotesGrid((g) => {
          if (!g) return g;
          const ng = g.map((r) => r.map((c) => [...c]));
          ng[entry.row][entry.col] = entry.prevAutoNotes ?? [];
          if (entry.prevPeerAutoNotes) {
            for (const [key, notes] of Object.entries(entry.prevPeerAutoNotes)) {
              const [pr, pc] = key.split(',').map(Number);
              ng[pr][pc] = [...notes];
            }
          }
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

  const finishGame = useCallback(() => {
    const outcome = gameStatus === 'solved' ? 'won' : 'abandoned';
    onGameComplete?.({
      gameId: gameIdRef.current,
      difficulty: difficultyRef.current,
      outcome,
      elapsedSeconds: outcome === 'won' ? elapsedSecondsRef.current : null,
    });
    pauseTimer();
    setIsPaused(false);
    lsClear();
    setGameId(null);
    setOriginalGrid(null);
    setCurrentGrid(null);
    setCandidateGrid(null);
    setAutoNotesGrid(null);
    setHistory([]);
    setErrorCells(new Set());
    setElapsedSeconds(0);
    setGameStatus('idle');
    setStatusMessage(null);
    setSelectedCell(null);
    setSelectedNumber(null);
  }, [pauseTimer, gameStatus, onGameComplete]);

  const startNewGameFromImage = useCallback(async (imageFile) => {
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
      const { originalGrid: importedGrid } = await importPuzzle(imageFile);
      const data = await createGameFromGrid(importedGrid);
      const emptyGrid = emptyCandidate();
      setGameId(data.gameId);
      setOriginalGrid(data.originalGrid);
      setCurrentGrid(data.currentGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setAutoNotesGrid(null);
      setAutoNotesActive(false);
      setHistory([]);
      lsSave(data.gameId, data.currentGrid, emptyGrid, 'imported', 0);
      startTimer();
    } catch (err) {
      if (err instanceof ForbiddenError) { onForbidden?.(); return; }
      setStatusMessage(`Failed to import puzzle: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [startTimer, onForbidden]);

  const loadDemoGame = useCallback(async (technique) => {
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
      const data = await getDemoGrid(technique);
      const emptyGrid = emptyCandidate();
      // Demo games have no game ID — they are not saved to the backend
      setGameId(null);
      setOriginalGrid(data.originalGrid);
      setCurrentGrid(data.originalGrid.map((row) => [...row]));
      setCandidateGrid(emptyGrid);
      setAutoNotesGrid(null);
      setAutoNotesActive(false);
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
  }, [startTimer, onForbidden]);

  const handleSetDifficulty = useCallback((diff) => {
    setDifficulty(diff);
  }, []);

  return {
    originalGrid,
    currentGrid,
    candidateGrid: autoNotesActive && autoNotesGrid && currentGrid
      ? autoNotesGrid.map((row, r) => row.map((cell, c) => currentGrid[r][c] !== 0 ? [] : cell))
      : candidateGrid,
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
    startNewGameFromImage,
    loadDemoGame,
    updateCell,
    clearCell,
    undoLastMove,
    canUndo: history.length > 0,
    requestValidation,
    requestHint,
    advanceHint,
    dismissHint,
    toggleAutoNotes,
    populateUserCandidates,
    canPopulateCandidates: autoNotesActive && !!autoNotesGrid,
    clearStatus,
    finishGame,
    elapsedSeconds,
    timerRunning,
    pauseTimer,
    isPaused,
    pauseGame,
    resumeGame,
  };
}
