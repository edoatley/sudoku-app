package com.sudoku.dto;

import java.util.List;

/**
 * Complete persisted state of a single player's game, returned from the game API and
 * stored in DynamoDB.
 *
 * <p>Holds the immutable puzzle ({@code originalGrid}), the player's working copy
 * ({@code currentGrid}), their pencil-mark candidates, the elapsed time, and a
 * lifecycle {@code status} (e.g. {@code "IN_PROGRESS"}, {@code "COMPLETE"}).
 * The {@code gameId} is used as the DynamoDB sort key under the {@code userId} partition.
 */
public record GameState(
        String userId,
        String gameId,
        String difficulty,
        List<List<Integer>> originalGrid,
        List<List<Integer>> currentGrid,
        List<List<List<Integer>>> candidates,
        int timeSpentSeconds,
        String status
) {}
