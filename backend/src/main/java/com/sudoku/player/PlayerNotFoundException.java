package com.sudoku.player;

// @spec UM-BE-004

/**
 * Thrown when a player profile lookup finds no record for the given userId.
 *
 * <p>This can only arise on PATCH /players/me if the player has never called
 * GET /players/me — an unusual but technically possible state.
 */
public class PlayerNotFoundException extends RuntimeException {
    public PlayerNotFoundException(String userId) {
        super("Player profile not found: " + userId);
    }
}
