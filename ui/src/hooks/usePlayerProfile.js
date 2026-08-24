import { useState, useCallback, useEffect } from 'react';
import {
  getPlayerProfile,
  updatePlayerProfile,
  getEmailFromSession,
  getGameHistory,
  ForbiddenError,
  warmupImageRecognition,
} from '../api/sudokuApi.js';
import { AVATAR_ICONS } from '../utils/avatarIcons.js';

const DEFAULT_AVATAR = 'Person';

function resolveAvatar(avatarKey) {
  if (!avatarKey) return DEFAULT_AVATAR;
  return AVATAR_ICONS.some((a) => a.name === avatarKey) ? avatarKey : DEFAULT_AVATAR;
}

const LS_KEY_AVATAR = 'sudoku_avatar';
const LS_KEY_HISTORY = 'sudoku_gameHistory';

export function usePlayerProfile(user, { onForbidden } = {}) {
  const [avatar, setAvatarState] = useState(() => localStorage.getItem(LS_KEY_AVATAR) ?? 'Person');
  const [history, setHistory] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(LS_KEY_HISTORY) ?? '[]');
    } catch {
      return [];
    }
  });
  const [playerProfile, setPlayerProfile] = useState(null);
  const [sessionEmail, setSessionEmail] = useState(null);

  // @spec FE-BE-007
  useEffect(() => {
    if (user) return;
    setHistory([]);
    try {
      localStorage.removeItem(LS_KEY_HISTORY);
    } catch {
      /**/
    }
    setSessionEmail(null);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    getEmailFromSession()
      .then(setSessionEmail)
      .catch(() => null);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    getPlayerProfile()
      .then((profile) => {
        setPlayerProfile(profile);
        if (profile?.avatarKey) {
          const resolved = resolveAvatar(profile.avatarKey);
          localStorage.setItem(LS_KEY_AVATAR, resolved);
          setAvatarState(resolved);
        }
        warmupImageRecognition();
      })
      .catch(async (err) => {
        if (err instanceof ForbiddenError) {
          const email = await getEmailFromSession().catch(() => null);
          onForbidden?.(email);
        }
      });
  }, [user, onForbidden]);

  // @spec FE-UI-041 — persist the selected avatar to localStorage under key sudoku_avatar
  const setAvatar = useCallback((iconName) => {
    localStorage.setItem(LS_KEY_AVATAR, iconName);
    setAvatarState(iconName);
  }, []);

  // @spec UM-UI-004, UM-UI-001, UM-UI-002, SC-RL-006
  const updateProfile = useCallback(async ({ displayName, avatarKey, aiCoachEnabled }) => {
    const updated = await updatePlayerProfile({ displayName, avatarKey, aiCoachEnabled });
    setPlayerProfile(updated);
    if (avatarKey != null) {
      const resolved = resolveAvatar(avatarKey);
      localStorage.setItem(LS_KEY_AVATAR, resolved);
      setAvatarState(resolved);
    }
    return updated;
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
      } catch (_) {
        /* storage full */
      }
      return next;
    });
  }, []);

  // @spec GH-UI-003, GH-UI-004, GH-UI-006
  const fetchHistory = useCallback(async () => {
    try {
      const entries = await getGameHistory(20);
      const mapped = entries.map((e) => ({
        id: e.gameId,
        difficulty: e.difficulty,
        outcome: e.outcome,
        elapsedSeconds: e.elapsedSeconds,
        hintsUsed: e.hintsUsed,
        completedAt: e.endedAt,
      }));
      setHistory(mapped);
      try {
        localStorage.setItem(LS_KEY_HISTORY, JSON.stringify(mapped.slice(0, 10)));
        // eslint-disable-next-line no-unused-vars
      } catch (_) {
        /* storage full */
      }
    } catch {
      // GH-UI-004: silently retain existing localStorage history on any failure
    }
  }, []);

  return { avatar, setAvatar, history, recordGame, fetchHistory, playerProfile, sessionEmail, updateProfile };
}
