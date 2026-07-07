package com.sudoku.web.exception;

import com.sudoku.domain.InvalidGridException;
import com.sudoku.web.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

// @spec AEH-MAP-001, AEH-MAP-002, AEH-WIRE-001

/**
 * Translates {@link InvalidGridException} to HTTP 400 Bad Request.
 *
 * <p>Thrown by {@code Board.fromGrid()} when the supplied grid has wrong dimensions
 * or out-of-range cell values. These are structural errors in the request payload.
 */
@Provider
public class InvalidGridExceptionMapper implements ExceptionMapper<InvalidGridException> {

    @Override
    public Response toResponse(InvalidGridException exception) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ErrorResponse.INVALID_GRID, exception.getMessage()))
                .build();
    }
}
