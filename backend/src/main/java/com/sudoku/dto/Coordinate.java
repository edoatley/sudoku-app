package com.sudoku.dto;

/**
 * A zero-based (row, col) position on the 9×9 board, used throughout hint and
 * validation responses to identify which cells the frontend should highlight.
 */
public record Coordinate(int row, int col) {}
