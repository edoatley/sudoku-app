package com.sudoku.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the server-side scoring formula applied to a solved game.
 *
 * <p>Scoring lives in {@link ScoringConstants} (formula constants) and is computed
 * in {@link GameItem#applyUpdate} when {@code isComplete=true}.
 */
// @spec LT-BE-001, LT-BE-002, LT-BE-003
class GameScoringTest {

    // ── easy ──────────────────────────────────────────────────────────────────

    // @spec LT-BE-001, LT-BE-002
    @Test
    void easy_perfectGame_scoresBaseMinusTimePenalty() {
        // base=100, elapsed=60s → timeBonus = max(0, 100 - floor(60/10)) = 94
        // hintsUsed=0 → multiplier = max(0, 1 - 0.1*0) = 1.0
        // score = round(94 * 1.0) = 94
        assertEquals(94, ScoringConstants.computeScore("easy", 60, 0));
    }

    @Test
    void easy_noHints_zeroTime_scoresFullBase() {
        // base=100, elapsed=0 → timeBonus=100, multiplier=1.0, score=100
        assertEquals(100, ScoringConstants.computeScore("easy", 0, 0));
    }

    @Test
    void easy_withHints_reducesScore() {
        // base=100, elapsed=0 → timeBonus=100
        // hintsUsed=2 → multiplier = max(0, 1 - 0.2) = 0.8
        // score = round(100 * 0.8) = 80
        assertEquals(80, ScoringConstants.computeScore("easy", 0, 2));
    }

    @Test
    void easy_timePenaltyExceedsBase_scoresZero() {
        // base=100, elapsed=1000s → timeBonus = max(0, 100 - 100) = 0 → score=0
        assertEquals(0, ScoringConstants.computeScore("easy", 1000, 0));
    }

    @Test
    void easy_timeOverBase_scoresZeroNotNegative() {
        // base=100, elapsed=2000s → floor(2000/10)=200 → max(0, 100-200)=0
        assertEquals(0, ScoringConstants.computeScore("easy", 2000, 0));
    }

    // ── medium ────────────────────────────────────────────────────────────────

    @Test
    void medium_perfectGame_scoresFullBase() {
        // base=200, elapsed=0, hintsUsed=0 → score=200
        assertEquals(200, ScoringConstants.computeScore("medium", 0, 0));
    }

    @Test
    void medium_withTimePenalty() {
        // base=200, elapsed=300s → timeBonus = max(0, 200 - 30) = 170, multiplier=1.0 → 170
        assertEquals(170, ScoringConstants.computeScore("medium", 300, 0));
    }

    @Test
    void medium_withHintAndTime() {
        // base=200, elapsed=200s → timeBonus = max(0, 200 - 20) = 180
        // hintsUsed=1 → multiplier = 0.9
        // score = round(180 * 0.9) = 162
        assertEquals(162, ScoringConstants.computeScore("medium", 200, 1));
    }

    // ── hard ──────────────────────────────────────────────────────────────────

    @Test
    void hard_perfectGame_scoresFullBase() {
        // base=350, elapsed=0, hintsUsed=0 → score=350
        assertEquals(350, ScoringConstants.computeScore("hard", 0, 0));
    }

    @Test
    void hard_tenHints_scoresZero() {
        // hintsUsed=10 → multiplier = max(0, 1 - 1.0) = 0.0 → score=0
        assertEquals(0, ScoringConstants.computeScore("hard", 0, 10));
    }

    @Test
    void hard_elevenHints_scoresZeroNotNegative() {
        // hintsUsed=11 → multiplier = max(0, 1 - 1.1) = max(0, -0.1) = 0 → score=0
        assertEquals(0, ScoringConstants.computeScore("hard", 0, 11));
    }

    // ── imported ──────────────────────────────────────────────────────────────

    @Test
    void imported_perfectGame_scoresFullBase() {
        // base=200, elapsed=0, hintsUsed=0 → score=200
        assertEquals(200, ScoringConstants.computeScore("imported", 0, 0));
    }

    @Test
    void imported_treatedSameAsMediumBase() {
        // both use base=200
        assertEquals(
            ScoringConstants.computeScore("medium", 120, 1),
            ScoringConstants.computeScore("imported", 120, 1)
        );
    }

    // ── boundary / rounding ───────────────────────────────────────────────────

    // @spec LT-BE-003
    @Test
    void score_isNeverNegative() {
        // Extreme inputs: huge time, many hints
        assertEquals(0, ScoringConstants.computeScore("easy", 99999, 99));
        assertEquals(0, ScoringConstants.computeScore("hard", 99999, 99));
    }

    @Test
    void score_roundsHalfUp() {
        // base=100, elapsed=0 → timeBonus=100
        // hintsUsed=5 → multiplier = max(0, 1 - 0.5) = 0.5
        // score = round(100 * 0.5) = 50
        assertEquals(50, ScoringConstants.computeScore("easy", 0, 5));
    }

    @Test
    void unknownDifficulty_treatsAsMediumBase() {
        // unexpected difficulty string → falls back to medium base (200)
        assertEquals(200, ScoringConstants.computeScore("extreme", 0, 0));
    }
}
