// @spec LT-UI-003, LT-UI-004, LT-UI-005
import { useState, useEffect, useCallback } from 'react';
import { getLeaderboard } from '../api/sudokuApi.js';

export function useLeaderboard() {
  const [leaderboard, setLeaderboard] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchLeaderboard = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getLeaderboard();
      setLeaderboard(data.entries);
      setError(null);
    } catch (err) {
      setError(err?.message ?? 'Failed to load leaderboard');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLeaderboard();
  }, [fetchLeaderboard]);

  return { leaderboard, loading, error, refresh: fetchLeaderboard };
}
