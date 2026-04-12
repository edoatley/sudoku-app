package com.sudoku.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Lightweight liveness probe for the Sudoku API.
 *
 * <p>Allows load balancers, API Gateway, and monitoring tools to confirm the Lambda
 * process is alive and the Quarkus application context has started successfully,
 * without incurring the cost of a real DynamoDB call.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public HealthResponse health() {
        return new HealthResponse("ok", "sudoku-api");
    }

    public record HealthResponse(String status, String service) {}
}
