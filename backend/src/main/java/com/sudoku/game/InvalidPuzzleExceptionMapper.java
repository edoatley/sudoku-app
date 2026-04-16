package com.sudoku.game;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Translates {@link InvalidPuzzleException} to an HTTP 422 Unprocessable Entity response.
 *
 * <p>The 422 status is the correct semantic choice for a well-formed request whose content
 * violates domain rules — the JSON was parsed successfully but the puzzle it describes is invalid.
 * The response body carries a single {@code error} field with a human-readable message so
 * the frontend can surface it directly to the user without parsing internal details.
 */
@Provider
public class InvalidPuzzleExceptionMapper implements ExceptionMapper<InvalidPuzzleException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(InvalidPuzzleException exception) {
        return Response
                .status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
