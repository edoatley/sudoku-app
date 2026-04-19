package com.sudoku.dto;

import com.sudoku.domain.Grid;

// @spec DT-DTO-002

/**
 * A newly generated or retrieved puzzle returned to the frontend.
 *
 * <p>{@code originalGrid} is the starting puzzle (zeros for empty cells).
 * {@code solutionGrid} is the unique complete solution for the puzzle.
 * {@code difficulty} is a human-readable label (e.g. {@code "easy"}, {@code "hard"}).
 * {@code minRank} is the lowest hint-strategy rank required to solve the puzzle,
 * used by the hint system to gate which techniques are offered; it is {@code null}
 * when not yet computed.
 */
public record PuzzleResponse(Grid originalGrid, Grid solutionGrid, String difficulty, Integer minRank) {

    public PuzzleResponse(Grid originalGrid, Grid solutionGrid, String difficulty) {
        this(originalGrid, solutionGrid, difficulty, null);
    }
}
