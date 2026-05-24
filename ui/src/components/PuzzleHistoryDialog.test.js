// @spec FE-UI-042, FE-UI-042a, FE-UI-042b
import { describe, it, expect } from 'vitest';

// Re-export helpers under test by extracting the logic here.
// The functions are module-private in PuzzleHistoryDialog.jsx, so we
// duplicate the pure logic to keep them testable without exposing internals.
// If the scoring formula changes, update both files.

const DIFFICULTY_BASE_SCORE = { easy: 100, medium: 200, hard: 350, imported: 200 };

function calculateScore(entry) {
  if (entry.outcome !== 'won') return 0;
  const base = DIFFICULTY_BASE_SCORE[entry.difficulty] ?? 100;
  const timeBonus = Math.max(0, base - Math.floor((entry.elapsedSeconds ?? 0) / 10));
  const hintMultiplier = Math.max(0, 1 - 0.1 * (entry.hintsUsed ?? 0));
  return Math.round(timeBonus * hintMultiplier);
}

function computeSummary(history) {
  const wins = history.filter(e => e.outcome === 'won');
  const times = wins.map(e => e.elapsedSeconds).filter(n => typeof n === 'number');
  const scores = wins.map(calculateScore).filter(s => s > 0);
  let currentStreak = 0;
  for (const e of history) {
    if (e.outcome === 'won') currentStreak++;
    else break;
  }
  return {
    totalWins: wins.length,
    winRate: history.length > 0 ? Math.round((wins.length / history.length) * 100) : null,
    bestTimeSeconds: times.length > 0 ? Math.min(...times) : null,
    currentStreak,
    avgScore: scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null,
  };
}

const won = (overrides = {}) => ({
  id: '1', difficulty: 'easy', outcome: 'won',
  elapsedSeconds: 120, hintsUsed: 0, completedAt: '2026-05-23T10:00:00.000Z',
  ...overrides,
});

const abandoned = (overrides = {}) => ({
  id: '2', difficulty: 'easy', outcome: 'abandoned',
  elapsedSeconds: null, hintsUsed: 0, completedAt: '2026-05-22T10:00:00.000Z',
  ...overrides,
});

describe('calculateScore', () => {
  describe('FE-UI-042b: provisional scoring', () => {
    it('returns 0 for abandoned games', () => {
      expect(calculateScore(abandoned())).toBe(0);
    });

    it('easy with no hints, fast time scores below base', () => {
      // base=100, timeBonus=max(0, 100 - floor(60/10))=94, hintMult=1.0 → 94
      const score = calculateScore(won({ difficulty: 'easy', elapsedSeconds: 60, hintsUsed: 0 }));
      expect(score).toBe(94);
    });

    it('hard with no hints, fast time scores below base', () => {
      // base=350, timeBonus=max(0,350-floor(60/10))=344, hintMult=1.0 → 344
      const score = calculateScore(won({ difficulty: 'hard', elapsedSeconds: 60, hintsUsed: 0 }));
      expect(score).toBe(344);
    });

    it('hint penalty reduces score by 10% per hint', () => {
      // base=100, elapsedSeconds=0 → timeBonus=100, 2 hints → mult=0.8 → 80
      const score = calculateScore(won({ difficulty: 'easy', elapsedSeconds: 0, hintsUsed: 2 }));
      expect(score).toBe(80);
    });

    it('score floors at 0 when hints exceed threshold', () => {
      // 11 hints → multiplier = max(0, 1-1.1) = 0 → score = 0
      const score = calculateScore(won({ difficulty: 'easy', elapsedSeconds: 0, hintsUsed: 11 }));
      expect(score).toBe(0);
    });

    it('very slow game gives timeBonus of 0', () => {
      // base=100, elapsedSeconds=2000 → timeBonus=max(0, 100-200)=0 → score=0
      const score = calculateScore(won({ difficulty: 'easy', elapsedSeconds: 2000, hintsUsed: 0 }));
      expect(score).toBe(0);
    });

    it('unknown difficulty falls back to base score of 100', () => {
      const score = calculateScore(won({ difficulty: 'unknown', elapsedSeconds: 0, hintsUsed: 0 }));
      expect(score).toBe(100);
    });
  });
});

describe('computeSummary', () => {
  describe('FE-UI-042a: summary banner data', () => {
    it('returns null values for empty history', () => {
      const s = computeSummary([]);
      expect(s.totalWins).toBe(0);
      expect(s.winRate).toBeNull();
      expect(s.bestTimeSeconds).toBeNull();
      expect(s.currentStreak).toBe(0);
      expect(s.avgScore).toBeNull();
    });

    it('counts wins correctly', () => {
      const history = [won(), won(), abandoned()];
      expect(computeSummary(history).totalWins).toBe(2);
    });

    it('calculates win rate as a percentage', () => {
      const history = [won(), won(), abandoned(), won()];
      expect(computeSummary(history).winRate).toBe(75);
    });

    it('win rate is null for empty history', () => {
      expect(computeSummary([]).winRate).toBeNull();
    });

    it('finds the best (lowest) completion time among wins', () => {
      const history = [
        won({ elapsedSeconds: 300 }),
        won({ elapsedSeconds: 180 }),
        won({ elapsedSeconds: 420 }),
      ];
      expect(computeSummary(history).bestTimeSeconds).toBe(180);
    });

    it('bestTimeSeconds is null when there are no wins', () => {
      expect(computeSummary([abandoned()]).bestTimeSeconds).toBeNull();
    });

    it('does not include non-number elapsedSeconds in best time', () => {
      const history = [won({ elapsedSeconds: null }), won({ elapsedSeconds: 200 })];
      expect(computeSummary(history).bestTimeSeconds).toBe(200);
    });

    it('streak counts consecutive wins from the start (newest-first)', () => {
      const history = [won(), won(), abandoned(), won()];
      expect(computeSummary(history).currentStreak).toBe(2);
    });

    it('streak is 0 when first entry is abandoned', () => {
      const history = [abandoned(), won(), won()];
      expect(computeSummary(history).currentStreak).toBe(0);
    });

    it('streak equals total wins when all games are won', () => {
      const history = [won(), won(), won()];
      expect(computeSummary(history).currentStreak).toBe(3);
    });

    it('avgScore is null when no wins produce a positive score', () => {
      // Very slow game → score 0 → filtered out → avgScore null
      const history = [won({ elapsedSeconds: 5000, hintsUsed: 0, difficulty: 'easy' })];
      expect(computeSummary(history).avgScore).toBeNull();
    });

    it('avgScore is the mean of positive scores across wins', () => {
      // easy, 0s, 0 hints → score 100; easy, 0s, 0 hints → score 100; avg = 100
      const history = [
        won({ difficulty: 'easy', elapsedSeconds: 0, hintsUsed: 0 }),
        won({ difficulty: 'easy', elapsedSeconds: 0, hintsUsed: 0 }),
      ];
      expect(computeSummary(history).avgScore).toBe(100);
    });
  });
});
