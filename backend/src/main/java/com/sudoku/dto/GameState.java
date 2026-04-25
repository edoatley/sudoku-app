package com.sudoku.dto;

import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;

// @spec DT-DTO-005, DT-DTO-007

/**
 * Complete persisted state of a single player's game, returned from the game API and
 * stored in DynamoDB.
 *
 * <p>Holds the immutable puzzle ({@code originalGrid}), the player's working copy
 * ({@code currentGrid}), their pencil-mark candidates, the elapsed time, and a
 * lifecycle {@code status} (e.g. {@code "IN_PROGRESS"}, {@code "SOLVED"}).
 * The {@code gameId} is used as the DynamoDB sort key under the {@code userId} partition.
 *
 * <p>{@code solutionGrid} is always non-null — every persisted game has a known solution.
 * {@code startedAt} is set when the game is created; {@code endedAt} is set when the
 * game transitions to {@code SOLVED}. Both are ISO-8601 UTC strings.
 */
public record GameState(
        String userId,
        String gameId,
        String difficulty,
        Grid originalGrid,
        Grid solutionGrid,
        Grid currentGrid,
        CandidatesGrid candidates,
        int timeSpentSeconds,
        String status,
        int hintsUsed,
        String startedAt,
        String endedAt
) {}
