package com.sudoku.player;

// @spec UM-BE-005, UM-BE-006

/**
 * Thrown when a PATCH /players/me request fails validation.
 *
 * <p>Covers two cases: the request body contains no updatable fields (both null),
 * and the displayName field fails length or blank constraints.
 */
public class InvalidPlayerUpdateException extends RuntimeException {
    public InvalidPlayerUpdateException(String message) {
        super(message);
    }
}
