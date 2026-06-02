package com.sudoku.game;

// @spec LT-BE-001, LT-BE-002, LT-BE-003
public final class ScoringConstants {

    static final int BASE_EASY     = 100;
    static final int BASE_MEDIUM   = 200;
    static final int BASE_HARD     = 350;
    static final int BASE_IMPORTED = 200;

    private ScoringConstants() {}

    /**
     * Computes the score for a solved game.
     *
     * <p>Formula: {@code timeBonus = max(0, base - floor(elapsedSeconds / 10))},
     * {@code score = round(timeBonus * max(0.0, 1.0 - 0.1 * hintsUsed))}.
     * Minimum score is 0.
     */
    public static int computeScore(String difficulty, int elapsedSeconds, int hintsUsed) {
        int base = baseScore(difficulty);
        int timeBonus = Math.max(0, base - Math.floorDiv(elapsedSeconds, 10));
        double multiplier = Math.max(0.0, 1.0 - 0.1 * hintsUsed);
        return (int) Math.round(timeBonus * multiplier);
    }

    private static int baseScore(String difficulty) {
        return switch (difficulty) {
            case "easy"     -> BASE_EASY;
            case "hard"     -> BASE_HARD;
            case "imported" -> BASE_IMPORTED;
            default         -> BASE_MEDIUM;
        };
    }
}
