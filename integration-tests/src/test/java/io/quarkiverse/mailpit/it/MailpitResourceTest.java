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
                .when().get("/mailpit")
                .then()
                .statusCode(200)
                .body(is("Email sent!"));
    }
}