package com.sudoku.game;

// @spec AEH-EX-004, AEH-EX-005

/**
 * Thrown when an imported grid has more than one valid solution.
 */
public class PuzzleHasMultipleSolutionsException extends InvalidPuzzleException {
    public PuzzleHasMultipleSolutionsException() {
        super("Puzzle has multiple solutions — a valid sudoku must have exactly one solution");
    }
}
