package com.sudoku.game.web;

import com.sudoku.domain.Grid;

// @spec DT-DTO-003

/**
 * Request body for creating a new game from an externally supplied puzzle grid,
 * used by the image-recognition pipeline and the developer demo endpoint.
 *
 * <p>The {@code originalGrid} is a 9×9 matrix of digits where {@code 0} represents
 * an empty cell. The game service validates the grid and persists it as a new game
 * for the authenticated user.
 */
public record CreateGameFromGridRequest(Grid originalGrid) {}
