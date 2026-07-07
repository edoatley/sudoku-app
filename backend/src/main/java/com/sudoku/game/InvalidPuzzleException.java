package com.sudoku.game;

// @spec AEH-EX-001

/**
 * Base class for all puzzle validity failures on import.
 *
 * <p>Subclasses carry a fixed, user-readable message set in their own no-arg constructor.
 * The HTTP 422 mapper in {@code com.sudoku.web.exception} handles this type and all subclasses.
 */
public abstract class InvalidPuzzleException extends RuntimeException {
    protected InvalidPuzzleException(String message) {
        super(message);
    }
}
