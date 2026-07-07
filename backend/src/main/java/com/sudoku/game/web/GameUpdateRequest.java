package com.sudoku.game.web;

import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;

// @spec DT-DTO-004

/**
 * Payload sent by the frontend when saving the player's in-progress state for a game.
 *
 * <p>Contains the full current grid (including any digits the player has entered),
 * the pencil-mark candidates grid, the total time spent so far, and an optional flag
 * indicating the player believes the puzzle is complete. All fields are persisted to
 * DynamoDB via the game service.
 */
public record GameUpdateRequest(
        Grid currentGrid,
        CandidatesGrid candidates,
        int timeSpentSeconds,
        Boolean isComplete,
        Integer hintsUsed
) {}
