package com.sudoku.dto;

import java.util.List;

/**
 * Request body carrying a board state to hint, validate, or solve endpoints.
 *
 * <p>{@code currentGrid} is the player's current 9×9 grid (zeros for empty cells).
 * {@code minRank} optionally constrains the hint engine to only consider strategies
 * at or above this difficulty rank, preventing trivial hints on harder puzzles.
 * {@code excludedRanks} lists specific strategy ranks to skip (e.g. because the
 * frontend has already shown that technique and wants the next distinct one).
 */
public record BoardRequest(List<List<Integer>> currentGrid, Integer minRank, List<Integer> excludedRanks) {

    public BoardRequest(List<List<Integer>> currentGrid) {
        this(currentGrid, null, null);
    }

    public BoardRequest(List<List<Integer>> currentGrid, Integer minRank) {
        this(currentGrid, minRank, null);
    }
}
