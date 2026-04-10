import { useState, useCallback, useEffect } from 'react';
import { getPlayerProfile, getEmailFromSession, ForbiddenError } from '../api/sudokuApi.js';

const LS_KEY_AVATAR = 'sudoku_avatar';
const LS_KEY_HISTORY = 'sudoku_gameHistory';

export function usePlayerProfile(user, { onForbidden } = {}) {
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
  const [playerProfile, setPlayerProfile] = useState(null);
  const [sessionEmail, setSessionEmail] = useState(null);

  useEffect(() => {
    if (!user) return;
    getEmailFromSession().then(setSessionEmail).catch(() => null);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    getPlayerProfile().then(setPlayerProfile).catch(async (err) => {
      if (err instanceof ForbiddenError) {
        const email = await getEmailFromSession().catch(() => null);
        onForbidden?.(email);
      }
    });
  }, [user, onForbidden]);

  const setAvatar = useCallback((iconName) => {
    localStorage.setItem(LS_KEY_AVATAR, iconName);
    setAvatarState(iconName);
  }, []);

  const recordGame = useCallback(({ gameId, difficulty, outcome, elapsedSeconds, hintsUsed }) => {
    if (!gameId) return;
    const entry = {
      id: gameId,
      difficulty,
      outcome,
      elapsedSeconds,
      hintsUsed: hintsUsed ?? 0,
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

  return { avatar, setAvatar, history, recordGame, playerProfile, sessionEmail };
}
