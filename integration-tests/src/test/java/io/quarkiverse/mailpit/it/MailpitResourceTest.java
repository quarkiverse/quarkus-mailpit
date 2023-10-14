package io.quarkiverse.mailpit.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

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

    @Test
    public void testNullFrom() {
        Response response = given()
                .when().get("/mailpit/nullfrom")
                .then()
                .statusCode(500)
                .and()
                .body("$", notNullValue())
                .extract().response();
        JsonPath json = new JsonPath(response.asString());
        String stack = json.get("stack").toString();
        if (StringUtils.isNotBlank((stack))) {
            // native mode does not have the stack trace
            assertThat(stack, containsStringIgnoringCase("sender address is not present"));
        }
    }
}
