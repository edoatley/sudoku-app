package com.sudoku.dto;

/**
 * Identifies a pencil-mark candidate that can be eliminated from a cell as a consequence
 * of applying a hint strategy.
 *
 * <p>Used in {@link HintResponse#eliminatedCandidates()} and {@link HintResponse#focusCandidates()}
 * so the frontend can visually distinguish candidates that are being removed from those
 * that are the logical focus of the technique.
 */
public record CandidateElimination(int row, int col, int value) {}
