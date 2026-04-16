package com.sudoku.game;

/**
 * Thrown when an imported sudoku grid is structurally invalid or has no unique solution.
 *
 * <p>This exception is raised in the service layer and translated to an HTTP 422 response
 * by {@link InvalidPuzzleExceptionMapper}. Keeping the exception free of JAX-RS types
 * ensures the service layer remains independent of the HTTP boundary.
 */
public class InvalidPuzzleException extends RuntimeException {
    public InvalidPuzzleException(String message) {
        super(message);
    }
}
