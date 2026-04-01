import { useState, useCallback } from 'react';

const LS_KEY_AVATAR = 'sudoku_avatar';
const LS_KEY_HISTORY = 'sudoku_gameHistory';

export function usePlayerProfile() {
  const [avatar, setAvatarState] = useState(
    () => localStorage.getItem(LS_KEY_AVATAR) ?? 'Person'
  );
  const [history, setHistory] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(LS_KEY_HISTORY) ?? '[]');
    } catch {
      return [];
    }
  });

  const setAvatar = useCallback((iconName) => {
    localStorage.setItem(LS_KEY_AVATAR, iconName);
    setAvatarState(iconName);
  }, []);

  const recordGame = useCallback(({ gameId, difficulty, outcome, elapsedSeconds }) => {
    if (!gameId) return;
    const entry = {
      id: gameId,
      difficulty,
      outcome,
      elapsedSeconds,
      completedAt: new Date().toISOString(),
    };
    setHistory((prev) => {
      const next = [entry, ...prev].slice(0, 10);
      try {
        localStorage.setItem(LS_KEY_HISTORY, JSON.stringify(next));
      // eslint-disable-next-line no-unused-vars
      } catch (_) { /* storage full */ }
      return next;
    });
  }, []);

  return { avatar, setAvatar, history, recordGame };
}
