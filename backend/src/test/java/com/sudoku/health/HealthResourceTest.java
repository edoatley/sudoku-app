package com.sudoku.health;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthResourceTest {

    @Test
    void health_returns200WithOkStatus() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("ok"))
                .body("service", equalTo("sudoku-api"));
    }
}
