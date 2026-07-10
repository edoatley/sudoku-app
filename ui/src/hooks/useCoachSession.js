// @spec SC-UI-001, SC-UI-002, SC-UI-003, SC-UI-004
import { useState, useCallback } from 'react';
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

export function useCoachSession({ gameId, currentGrid, setHighlightCells }) {
  const [isOpen, setIsOpen] = useState(false);
  const [history, setHistory] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

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
        setHighlightCells?.(response.hint?.highlightCells ?? []);
      } catch {
        setHistory((prev) => [...prev, ERROR_MESSAGE]);
      } finally {
        setIsLoading(false);
      }
    },
    [gameId, currentGrid, history, isLoading, setHighlightCells]
  );

  return { isOpen, open, close, history, isLoading, sendMessage };
}
