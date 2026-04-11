package com.sudoku.dto;

import java.util.List;

/**
 * Payload sent by the frontend when saving the player's in-progress state for a game.
 *
 * <p>Contains the full current grid (including any digits the player has entered),
 * the pencil-mark candidates grid, the total time spent so far, and an optional flag
 * indicating the player believes the puzzle is complete. All fields are persisted to
 * DynamoDB via the game service.
 */
public record GameUpdateRequest(
        List<List<Integer>> currentGrid,
        List<List<List<Integer>>> candidates,
        int timeSpentSeconds,
        Boolean isComplete,
        Integer hintsUsed
) {}
