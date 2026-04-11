package com.sudoku.domain;

/**
 * Named constants for the structural parameters of a standard 9×9 Sudoku grid.
 *
 * Centralised here so the rules of the puzzle format — grid size, digit range,
 * box dimension — are declared once and referenced everywhere rather than scattered
 * as unexplained integer literals across board and strategy code.
 */
public final class SudokuConstants {

    private SudokuConstants() {}

    /** Number of rows, columns, and blocks in a standard Sudoku grid. */
    public static final int UNIT_SIZE = 9;

    /** Smallest legal digit value in a Sudoku puzzle. */
    public static final int MIN_DIGIT = 1;

    /** Largest legal digit value in a Sudoku puzzle. */
    public static final int MAX_DIGIT = 9;

    /** Side length of each 3×3 box (block). */
    public static final int BOX_SIZE = 3;
}
