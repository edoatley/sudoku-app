package com.sudoku.dto;

import java.util.List;

/**
 * Result of validating the current state of a player's board.
 *
 * <p>{@code isValid} is {@code true} when no row, column, or block contains a duplicate digit.
 * {@code isSolved} is {@code true} when the board is both valid and completely filled.
 * {@code errors} lists every cell position that participates in a conflict, allowing the
 * frontend to highlight them individually.
 */
public record ValidationResponse(boolean isValid, boolean isSolved, List<Coordinate> errors) {}
