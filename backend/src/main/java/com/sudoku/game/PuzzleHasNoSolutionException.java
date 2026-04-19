package com.sudoku.game;

// @spec AEH-EX-003, AEH-EX-005

/**
 * Thrown when an imported grid has no valid solution.
 */
public class PuzzleHasNoSolutionException extends InvalidPuzzleException {
    public PuzzleHasNoSolutionException() {
        super("Puzzle has no valid solution — it may have been scanned incorrectly");
    }
}
