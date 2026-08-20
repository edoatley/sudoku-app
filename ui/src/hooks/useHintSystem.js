import { useState, useCallback } from 'react';
import { getHint, ForbiddenError } from '../api/sudokuApi.js';
import { newCid } from './useEventLog.js';

export function useHintSystem({
  currentGridRef,
  hintMinRankRef,
  onForbidden,
  setStatusMessage,
  setGameStatus,
  setIsLoading,
  setCandidateGrid,
  setCurrentGrid,
  recordEvent,
}) {
  // Records a HINT_REQUEST / HINT_RESPONSE pair sharing a cid. Called by both the primary and
  // alternate hint flows. On a transport error no HINT_RESPONSE is recorded (see FE-BE-021).
  // @spec FE-BE-020, FE-BE-021
  const recordHintResponse = useCallback(
    (cid, hint) => {
      recordEvent?.({
        type: 'HINT_RESPONSE',
        cid,
        techniqueName: hint?.techniqueName ?? null,
        strategyRank: hint?.strategyRank ?? null,
        difficulty: hint?.difficulty ?? null,
        found: !!hint,
        nudge: hint?.nudge ?? null,
        focus: hint?.focus ?? null,
        reveal: hint?.reveal ?? null,
      });
    },
    [recordEvent]
  );
  const [activeHint, setActiveHint] = useState(null);
  const [hintStage, setHintStage] = useState('nudge');
  const [highlightCells, setHighlightCells] = useState([]);
  const [hintsUsed, setHintsUsed] = useState(0);
  const [excludedHintRanks, setExcludedHintRanks] = useState([]);

  const resetHintState = useCallback(() => {
    setActiveHint(null);
    setHintStage('nudge');
    setHighlightCells([]);
    setHintsUsed(0);
    setExcludedHintRanks([]);
  }, []);

  // @spec HE-UI-011, HE-UI-012, HE-UI-013, HE-UI-014 — fetch a hint, retrying with no exclusions if the first call finds nothing.
  const fetchHintWithFallback = useCallback(
    async (excluded) => {
      const hint = await getHint(currentGridRef.current, hintMinRankRef.current, excluded);
      if (hint !== null) return { hint, didReset: false };
      if (excluded.length === 0) return { hint: null, didReset: false };
      const retryHint = await getHint(currentGridRef.current, hintMinRankRef.current, []);
      return { hint: retryHint, didReset: true };
    },
    [currentGridRef, hintMinRankRef]
  );

  const requestHint = useCallback(async () => {
    if (!currentGridRef.current) return;
    setIsLoading(true);
    const cid = newCid();
    recordEvent?.({ type: 'HINT_REQUEST', cid, minRank: hintMinRankRef.current, excludedRanks: excludedHintRanks });
    try {
      const { hint, didReset } = await fetchHintWithFallback(excludedHintRanks);
      recordHintResponse(cid, hint);
      if (!hint) {
        setExcludedHintRanks([]);
        setStatusMessage('No more hints available.');
        return;
      }
      setHintsUsed((n) => n + 1);
      if (hint.strategyRank != null) {
        const nextExcluded = didReset
          ? [hint.strategyRank]
          : excludedHintRanks.includes(hint.strategyRank)
            ? excludedHintRanks
            : [...excludedHintRanks, hint.strategyRank];
        setExcludedHintRanks(nextExcluded);
      }
      setActiveHint(hint);
      setHintStage('nudge');
      setHighlightCells([]);
    } catch (err) {
      if (err instanceof ForbiddenError) {
        onForbidden?.();
        return;
      }
      setStatusMessage(`Hint failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [
    currentGridRef,
    fetchHintWithFallback,
    excludedHintRanks,
    onForbidden,
    setIsLoading,
    setStatusMessage,
    setGameStatus,
    recordEvent,
    recordHintResponse,
    hintMinRankRef,
  ]);

  const requestAlternateHint = useCallback(async () => {
    if (!currentGridRef.current) return;
    setIsLoading(true);
    const cid = newCid();
    recordEvent?.({ type: 'HINT_REQUEST', cid, minRank: hintMinRankRef.current, excludedRanks: excludedHintRanks });
    try {
      const { hint, didReset } = await fetchHintWithFallback(excludedHintRanks);
      recordHintResponse(cid, hint);
      if (!hint) {
        setExcludedHintRanks([]);
        setStatusMessage('No more alternate hints. Starting over from the easiest.');
        return;
      }
      if (hint.strategyRank != null) {
        const nextExcluded = didReset
          ? [hint.strategyRank]
          : excludedHintRanks.includes(hint.strategyRank)
            ? excludedHintRanks
            : [...excludedHintRanks, hint.strategyRank];
        setExcludedHintRanks(nextExcluded);
      }
      setActiveHint(hint);
      setHintStage('nudge');
      setHighlightCells([]);
    } catch (err) {
      if (err instanceof ForbiddenError) {
        onForbidden?.();
        return;
      }
      setStatusMessage(`Hint failed: ${err.message}`);
      setGameStatus('error');
    } finally {
      setIsLoading(false);
    }
  }, [
    currentGridRef,
    fetchHintWithFallback,
    excludedHintRanks,
    onForbidden,
    setIsLoading,
    setStatusMessage,
    setGameStatus,
    recordEvent,
    recordHintResponse,
    hintMinRankRef,
  ]);

  const advanceHint = useCallback(() => {
    if (!activeHint) return;
    if (hintStage === 'nudge') {
      setHintStage('focus');
      setHighlightCells(activeHint.highlightCells);
    } else if (hintStage === 'focus') {
      setHintStage('reveal');
      if (activeHint.focusCandidates?.length > 0) {
        setCandidateGrid((prev) => {
          const next = prev.map((r) => r.map((c) => [...c]));
          for (const { row, col, value } of activeHint.focusCandidates) {
            if (!next[row][col].includes(value)) {
              next[row][col].push(value);
            }
          }
          return next;
        });
      }
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
  }, [activeHint, hintStage, setCandidateGrid, setCurrentGrid]);

  const dismissHint = useCallback(() => {
    setActiveHint(null);
    setHintStage('nudge');
    setHighlightCells([]);
  }, []);

  return {
    activeHint,
    hintStage,
    highlightCells,
    setHighlightCells,
    hintsUsed,
    setHintsUsed,
    excludedHintRanks,
    setExcludedHintRanks,
    resetHintState,
    requestHint,
    requestAlternateHint,
    advanceHint,
    dismissHint,
  };
}
