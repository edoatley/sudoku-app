package com.sudoku.dto;

import com.sudoku.domain.Grid;

import java.util.List;

// @spec DT-DTO-001

/**
 * Request body carrying a board state to hint, validate, or solve endpoints.
 *
 * <p>{@code currentGrid} is the player's current 9×9 grid (zeros for empty cells).
 * {@code solutionGrid} is the puzzle's unique solution, used by the validate endpoint
 * for precise cell-by-cell error detection. When absent, validation falls back to
 * duplicate-detection only.
 * {@code minRank} optionally constrains the hint engine to only consider strategies
 * at or above this difficulty rank, preventing trivial hints on harder puzzles.
 * {@code excludedRanks} lists specific strategy ranks to skip (e.g. because the
 * frontend has already shown that technique and wants the next distinct one).
 */
public record BoardRequest(Grid currentGrid, Grid solutionGrid, Integer minRank, List<Integer> excludedRanks) {

    public BoardRequest(Grid currentGrid) {
        this(currentGrid, null, null, null);
    }

    public BoardRequest(Grid currentGrid, Integer minRank) {
        this(currentGrid, null, minRank, null);
    }

    public BoardRequest(Grid currentGrid, Integer minRank, List<Integer> excludedRanks) {
        this(currentGrid, null, minRank, excludedRanks);
    }
}
