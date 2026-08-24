import { useState, useCallback, useEffect } from 'react';
import { saveGame } from '../api/sudokuApi.js';

const LS_KEY_ELAPSED_SECONDS = 'sudoku_elapsedSeconds';
const LS_KEY_HINTS_USED = 'sudoku_hintsUsed';

export function useGameSync({
  gameIdRef,
  currentGridRef,
  candidateGridRef,
  elapsedSecondsRef,
  hintsUsedRef,
  pauseTimer,
  resumeTimerIfActive,
  gameActiveRef,
  takeEventBatch,
  restoreEventBatch,
}) {
  const [isSyncing, setIsSyncing] = useState(false);

  // @spec FE-BE-022 — flush buffered puzzle-play events, restoring them if the save fails
  const syncToBackend = useCallback(() => {
    const gid = gameIdRef.current;
    const grid = currentGridRef.current;
    const candidates = candidateGridRef.current;
    if (!gid || !grid) return;
    try {
      localStorage.setItem(LS_KEY_ELAPSED_SECONDS, String(elapsedSecondsRef.current));
      localStorage.setItem(LS_KEY_HINTS_USED, String(hintsUsedRef.current));
      // eslint-disable-next-line no-unused-vars
    } catch (_) {
      /* storage full */
    }
    setIsSyncing(true);
    const batch = takeEventBatch?.();
    saveGame(gid, {
      currentGrid: grid,
      candidates: candidates ?? [],
      timeSpentSeconds: elapsedSecondsRef.current,
      hintsUsed: hintsUsedRef.current,
      events: batch?.wire,
    })
      .catch(() => restoreEventBatch?.(batch))
      .finally(() => setIsSyncing(false));
  }, [gameIdRef, currentGridRef, candidateGridRef, elapsedSecondsRef, hintsUsedRef, takeEventBatch, restoreEventBatch]);

  // @spec FE-BE-004 — auto-save currentGrid, candidates, timeSpentSeconds, and hintsUsed every 60s
  useEffect(() => {
    const interval = setInterval(syncToBackend, 60_000);
    return () => clearInterval(interval);
  }, [syncToBackend]);

  // @spec FE-BE-005, FE-BE-006 — save and pause the timer when the tab becomes hidden; resume the
  // timer when it becomes visible again
  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        syncToBackend();
        pauseTimer();
      } else if (document.visibilityState === 'visible') {
        resumeTimerIfActive(gameActiveRef.current);
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, [syncToBackend, pauseTimer, resumeTimerIfActive, gameActiveRef]);

  return { isSyncing, syncToBackend };
}
