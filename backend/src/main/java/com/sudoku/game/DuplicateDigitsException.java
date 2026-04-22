package com.sudoku.game;

// @spec AEH-EX-002, AEH-EX-005

/**
 * Thrown when an imported grid contains duplicate digits in any row, column, or block.
 */
public class DuplicateDigitsException extends InvalidPuzzleException {
    public DuplicateDigitsException() {
        super("Puzzle contains duplicate digits — check rows, columns, and boxes for conflicts");
    }
}
