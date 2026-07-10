package com.sudoku.game.web;

import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;

import java.util.List;

// @spec DT-DTO-004, GL-API-005

/**
 * Payload sent by the frontend when saving the player's in-progress state for a game.
 *
 * <p>Contains the full current grid (including any digits the player has entered),
 * the pencil-mark candidates grid, the total time spent so far, and an optional flag
 * indicating the player believes the puzzle is complete. All fields are persisted to
 * DynamoDB via the game service.
 *
 * <p>{@code events} is an optional buffer of puzzle-play actions ({@link PuzzleEvent}) the
 * client observed since the last save. It is observability-only — never persisted, never part
 * of the response — and a null or malformed {@code events} array never fails the save.
 */
public record GameUpdateRequest(
        Grid currentGrid,
        CandidatesGrid candidates,
        int timeSpentSeconds,
        Boolean isComplete,
        Integer hintsUsed,
        List<PuzzleEvent> events
) {
    /** Convenience constructor for callers with no puzzle-play events to report. */
    public GameUpdateRequest(Grid currentGrid, CandidatesGrid candidates, int timeSpentSeconds,
                             Boolean isComplete, Integer hintsUsed) {
        this(currentGrid, candidates, timeSpentSeconds, isComplete, hintsUsed, null);
    }
}
