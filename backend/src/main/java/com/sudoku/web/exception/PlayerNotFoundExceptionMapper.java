package com.sudoku.web.exception;

import com.sudoku.web.ErrorResponse;
import com.sudoku.player.PlayerNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

// @spec AEH-MAP-001, AEH-WIRE-001, UM-BE-004

/**
 * Translates {@link PlayerNotFoundException} to HTTP 404 Not Found.
 */
@Provider
public class PlayerNotFoundExceptionMapper implements ExceptionMapper<PlayerNotFoundException> {

    @Override
    public Response toResponse(PlayerNotFoundException exception) {
        return Response
                .status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ErrorResponse.PLAYER_NOT_FOUND, exception.getMessage()))
                .build();
    }
}
