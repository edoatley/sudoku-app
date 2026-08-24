// @spec SC-UI-001, SC-UI-002, SC-UI-003, SC-UI-004, SC-UI-010, SC-UI-011, SC-UI-012, SC-UI-014, SC-UI-040, SC-UI-041, SC-UI-042, SC-UI-050, SC-UI-051, SC-UI-060, SC-UI-063, SC-UI-064
import { useState, useCallback, useEffect, useRef } from 'react';
import { postCoachMessage } from '../api/sudokuApi.js';

const WELCOME_MESSAGE = {
  role: 'assistant',
  content:
    "Hello! I'm your Sudoku coach. I can see your current board — ask me anything, or tap a quick reply below to get started.",
};

const ERROR_MESSAGE = {
  role: 'assistant',
  content: "Sorry, I'm having trouble connecting right now. Please try again in a moment.",
};

const SOLVED_MESSAGE = {
  role: 'assistant',
  content: 'Congratulations — the puzzle is complete! No more moves needed.',
};

export function useCoachSession({
  gameId,
  currentGrid,
  setHighlightCells,
  setCurrentGrid,
  setCandidateGrid,
  initialTokensUsedThisMonth = 0,
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [history, setHistory] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  // @spec SC-RL-010 — seeded from the profile fetched at load, then kept current from each
  // coach response's cumulative total so the counter moves within the session without refetching.
  const [tokensUsedThisMonth, setTokensUsedThisMonth] = useState(initialTokensUsedThisMonth);
  const hasLiveTokenUpdate = useRef(false);

  // playerProfile loads asynchronously and may arrive after this hook's first render, so keep
  // following it until a real coach response supersedes it with a live count.
  useEffect(() => {
    if (!hasLiveTokenUpdate.current) {
      setTokensUsedThisMonth(initialTokensUsedThisMonth);
    }
  }, [initialTokensUsedThisMonth]);

  const open = useCallback(() => {
    setIsOpen(true);
    setHistory((prev) => (prev.length === 0 ? [WELCOME_MESSAGE] : prev));
  }, []);

  const close = useCallback(() => {
    setIsOpen(false);
    setHighlightCells?.([]);
  }, [setHighlightCells]);

  const sendMessage = useCallback(
    async (text) => {
      if (!text.trim() || isLoading) return;

      // Snapshot current history (excluding the welcome message) for the API call
      const apiHistory = history
        .filter((m) => m.role === 'user' || m.role === 'assistant')
        .slice(-6)
        .map((m) => ({ role: m.role, content: m.content }));

      setHistory((prev) => [...prev, { role: 'user', content: text }]);
      setIsLoading(true);

      try {
        const response = await postCoachMessage(currentGrid, apiHistory, text, gameId);

        if (response === null) {
          setHistory((prev) => [...prev, SOLVED_MESSAGE]);
          return;
        }

        setHistory((prev) => [...prev, { role: 'assistant', content: response.aiMessage }]);
        // @spec SC-UI-041 — coach and useHintSystem share the same highlightCells state; writing
        // here on every coach response supersedes any stale hint-sourced highlight while the
        // panel is open, giving coach highlights precedence.
        setHighlightCells?.(response.hint?.highlightCells ?? []);

        // @spec SC-UI-050, SC-UI-051 — solvedCells/eliminatedCandidates only take effect on the
        // board when revealHint is true, matching the deterministic Hint button's reveal stage
        // (useHintSystem.js's advanceHint). Without this, the coach could state the answer in
        // its message while the board only highlighted the cell, leaving the player to type a
        // digit the coach had already told them — inconsistent with the Hint button's behaviour.
        if (response.revealHint) {
          if (response.hint?.solvedCells?.length > 0) {
            setCurrentGrid?.((prev) => {
              const next = prev.map((r) => [...r]);
              for (const { row, col, value } of response.hint.solvedCells) {
                next[row][col] = value;
              }
              return next;
            });
            setCandidateGrid?.((prev) => {
              const next = prev.map((r) => r.map((c) => [...c]));
              for (const { row, col } of response.hint.solvedCells) {
                next[row][col] = [];
              }
              return next;
            });
          }
          if (response.hint?.eliminatedCandidates?.length > 0) {
            setCandidateGrid?.((prev) => {
              const next = prev.map((r) => r.map((c) => [...c]));
              for (const { row, col, value } of response.hint.eliminatedCandidates) {
                const idx = next[row][col].indexOf(value);
                if (idx !== -1) next[row][col].splice(idx, 1);
              }
              return next;
            });
          }
        }

        if (response.tokensUsedThisMonth != null) {
          hasLiveTokenUpdate.current = true;
          setTokensUsedThisMonth(response.tokensUsedThisMonth);
        }
      } catch {
        setHistory((prev) => [...prev, ERROR_MESSAGE]);
      } finally {
        setIsLoading(false);
      }
    },
    [gameId, currentGrid, history, isLoading, setHighlightCells, setCurrentGrid, setCandidateGrid]
  );

  return { isOpen, open, close, history, isLoading, sendMessage, tokensUsedThisMonth };
}
