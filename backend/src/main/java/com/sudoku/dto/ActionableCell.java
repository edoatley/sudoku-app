package com.sudoku.dto;

/**
 * A cell that can have a definite digit placed in it as the result of applying a hint strategy.
 *
 * <p>Returned inside {@link HintResponse#solvedCells()} so the frontend knows both
 * where to place the value and which digit to fill in.
 */
public record ActionableCell(int row, int col, int value) {}
