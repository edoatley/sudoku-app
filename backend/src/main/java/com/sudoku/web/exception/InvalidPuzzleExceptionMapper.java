package com.sudoku.web.exception;

import com.sudoku.web.ErrorResponse;
import com.sudoku.game.InvalidPuzzleException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

// @spec AEH-MAP-001, AEH-MAP-003, AEH-WIRE-001

/**
 * Translates {@link InvalidPuzzleException} (and all subclasses) to HTTP 422 Unprocessable Entity.
 *
 * <p>The 422 status is the correct semantic choice: the JSON was parsed successfully but the
 * puzzle it describes violates domain rules. Covers {@code DuplicateDigitsException},
 * {@code PuzzleHasNoSolutionException}, and {@code PuzzleHasMultipleSolutionsException}.
 */
@Provider
public class InvalidPuzzleExceptionMapper implements ExceptionMapper<InvalidPuzzleException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(InvalidPuzzleException exception) {
        return Response
                .status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ErrorResponse.INVALID_PUZZLE, exception.getMessage()))
                .build();
    }
}
