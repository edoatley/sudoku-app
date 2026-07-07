package com.sudoku.web.exception;

import com.sudoku.web.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

// @spec AEH-MAP-001, AEH-MAP-005, AEH-MAP-006, AEH-MAP-007, AEH-WIRE-001

/**
 * Catch-all mapper for any unhandled {@link Exception} that reaches the REST boundary.
 *
 * <p>Logs the full exception at ERROR level with a UUID correlation ID, then returns
 * HTTP 500 with a sanitized response body. The original exception message and stack
 * trace are never included in the response — only the correlation ID, so that a user
 * can report it and the operator can locate the matching log line.
 *
 * <p>{@link WebApplicationException} subclasses (e.g. NotFoundException, BadRequestException)
 * carry an intentional HTTP status and are passed through unchanged so the router's 404s
 * and the endpoint's 400s are not silently converted to 500.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        // WebApplicationException already carries the correct HTTP status — don't override it.
        if (exception instanceof WebApplicationException wae) {
            LOG.debugf("WebApplicationException [status=%d]: %s", wae.getResponse().getStatus(), wae.getMessage());
            return wae.getResponse();
        }
        String correlationId = UUID.randomUUID().toString();
        LOG.errorf(exception, "Unhandled exception [correlationId=%s]", correlationId);
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(
                        ErrorResponse.INTERNAL_ERROR,
                        "An unexpected error occurred",
                        "correlationId: " + correlationId
                ))
                .build();
    }
}
