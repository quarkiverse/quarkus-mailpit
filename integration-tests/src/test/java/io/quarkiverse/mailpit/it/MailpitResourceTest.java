package io.quarkiverse.mailpit.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MailpitResourceTest {

    @Test
    public void testSendEmailEndpoint() {
        given()
                .when().get("/mailpit/alert")
                .then()
                .statusCode(200)
                .body(is("Email sent!"));
    }

    @Test
    public void testCustomFrom() {
        given()
                .when().get("/mailpit/from")
                .then()
                .statusCode(200)
                .body(is("Sent from info@melloware.com"));
    }
}