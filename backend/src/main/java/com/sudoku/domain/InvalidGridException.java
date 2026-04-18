package com.sudoku.domain;

/**
 * Thrown by {@link Board#fromGrid(java.util.List)} when the supplied grid is structurally
 * invalid (wrong dimensions or out-of-range cell values).
 *
 * <p>This is a domain-level exception — it carries no HTTP semantics. Upper layers
 * (service, resource) may catch and re-throw as {@link com.sudoku.game.InvalidPuzzleException}
 * when the invalid grid originates from user input.
 */
public class InvalidGridException extends RuntimeException {
    public InvalidGridException(String message) {
        super(message);
    }
}
