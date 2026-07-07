package com.sudoku.web.exception;

import com.sudoku.web.ErrorResponse;
import com.sudoku.game.GameNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

// @spec AEH-MAP-001, AEH-MAP-004, AEH-WIRE-001

/**
 * Translates {@link GameNotFoundException} to HTTP 404 Not Found.
 */
@Provider
public class GameNotFoundExceptionMapper implements ExceptionMapper<GameNotFoundException> {

    @Override
    public Response toResponse(GameNotFoundException exception) {
        return Response
                .status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ErrorResponse.GAME_NOT_FOUND, exception.getMessage()))
                .build();
    }
}
