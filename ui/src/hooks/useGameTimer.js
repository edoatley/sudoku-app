import { useState, useCallback, useEffect, useRef } from 'react';

// @spec FE-UI-020 — increment elapsed time every second while running; FE-UI-021 — pauseGame stops
// the timer (the pause overlay itself is rendered by App.jsx/PauseOverlay)
export function useGameTimer() {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [timerRunning, setTimerRunning] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const timerRef = useRef(null);
  const isPausedRef = useRef(false);
  isPausedRef.current = isPaused;

  useEffect(
    () => () => {
      if (timerRef.current) clearInterval(timerRef.current);
    },
    []
  );

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

  const resumeTimerIfActive = useCallback((gameActive) => {
    if (gameActive && !isPausedRef.current) {
      if (timerRef.current) clearInterval(timerRef.current);
      setTimerRunning(true);
      timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
    }
  }, []);

  const restoreTimer = useCallback((savedElapsed) => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setIsPaused(false);
    setElapsedSeconds(savedElapsed);
    setTimerRunning(true);
    timerRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
  }, []);

  return {
    elapsedSeconds,
    setElapsedSeconds,
    timerRunning,
    isPaused,
    setIsPaused,
    isPausedRef,
    timerRef,
    startTimer,
    pauseTimer,
    pauseGame,
    resumeGame,
    resumeTimerIfActive,
    restoreTimer,
  };
}
