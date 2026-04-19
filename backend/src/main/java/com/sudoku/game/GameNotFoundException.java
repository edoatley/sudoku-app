package com.sudoku.game;

// @spec AEH-EX-006, AEH-EX-007

/**
 * Thrown when a game lookup by (userId, gameId) finds no matching record.
 */
public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String gameId) {
        super("Game not found: " + gameId);
    }
}
