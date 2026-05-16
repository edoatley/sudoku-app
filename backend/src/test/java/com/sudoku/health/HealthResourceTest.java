package com.sudoku.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class HealthResourceTest {

    @Test
    void health_returns200WithOkStatus_afterWarmingWindow() {
        // The test JVM starts well before the 30 s window in a normal test run,
        // but we cannot guarantee that here — test the unit directly instead.
        HealthResource resource = new HealthResource(Instant.now().minus(HealthResource.WARMING_WINDOW).minusSeconds(1));
        var resp = resource.health();
        assertEquals(200, resp.getStatus());
        assertEquals("ok", ((HealthResource.HealthResponse) resp.getEntity()).status());
        assertEquals("sudoku-api", ((HealthResource.HealthResponse) resp.getEntity()).service());
    }

    @Test
    void health_returns503WithWarmingStatus_duringWarmingWindow() {
        HealthResource resource = new HealthResource(Instant.now());
        var resp = resource.health();
        assertEquals(503, resp.getStatus());
        assertEquals("warming", ((HealthResource.HealthResponse) resp.getEntity()).status());
        assertEquals("sudoku-api", ((HealthResource.HealthResponse) resp.getEntity()).service());
    }

    @Test
    void health_endpoint_responds() {
        // Integration test: confirms the endpoint is reachable and returns JSON with the expected fields.
        // Status may be 200 or 503 depending on how long the test JVM has been running.
        given()
            .when().get("/health")
            .then()
                .contentType(ContentType.JSON)
                .body("service", equalTo("sudoku-api"));
    }
}
