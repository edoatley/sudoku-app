package com.sudoku.web.exception;

import com.sudoku.web.ErrorResponse;
import com.sudoku.player.InvalidPlayerUpdateException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

// @spec AEH-MAP-001, AEH-WIRE-001, UM-BE-005, UM-BE-006

/**
 * Translates {@link InvalidPlayerUpdateException} to HTTP 400 Bad Request.
 */
@Provider
public class InvalidPlayerUpdateExceptionMapper implements ExceptionMapper<InvalidPlayerUpdateException> {

    @Override
    public Response toResponse(InvalidPlayerUpdateException exception) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ErrorResponse.INVALID_PLAYER_UPDATE, exception.getMessage()))
                .build();
    }
}
