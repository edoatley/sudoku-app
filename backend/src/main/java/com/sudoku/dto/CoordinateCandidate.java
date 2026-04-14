package com.sudoku.dto;

/**
 * Identifies a pencil-mark candidate at a specific cell coordinate.
 *
 * <p>Used in {@link HintResponse#eliminatedCandidates()} and {@link HintResponse#focusCandidates()}
 * so the frontend can visually distinguish candidates that are being removed from those
 * that are the logical focus of the technique.
 */
public record CoordinateCandidate(int row, int col, int value) {}
