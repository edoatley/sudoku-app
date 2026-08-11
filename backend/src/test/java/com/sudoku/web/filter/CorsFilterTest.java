package com.sudoku.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * The in-app {@link CorsFilter} is the CORS mechanism on GCP (Cloud Run has no API Gateway) and on
 * local dev; on AWS API Gateway owns CORS and ignores backend CORS headers. The default test config
 * enables the filter with {@code sudoku.cors.allowed-origins=http://localhost:5173}, so these tests
 * pin its preflight behaviour — the exact thing that must answer the browser before the auth gate.
 *
 * @spec UM-BE-040, UM-BE-041, UM-BE-042, UM-GCP-006
 */
@QuarkusTest
class CorsFilterTest {

    @Test
    void preflight_allowedOrigin_returns200WithCorsHeaders() {
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .when()
                .options("/games/current")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:5173")
                .header("Access-Control-Allow-Methods", equalTo("GET,POST,PATCH,OPTIONS"))
                .header("Access-Control-Allow-Headers", equalTo("Content-Type,Accept,Authorization"));
    }

    @Test
    void preflight_disallowedOrigin_omitsAllowOrigin() {
        given()
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET")
                .when()
                .options("/games/current")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
