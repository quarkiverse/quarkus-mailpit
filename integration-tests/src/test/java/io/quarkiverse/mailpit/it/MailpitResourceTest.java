package io.quarkiverse.mailpit.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mailpit.test.InjectMailbox;
import io.quarkiverse.mailpit.test.Mailbox;
import io.quarkiverse.mailpit.test.WithMailbox;
import io.quarkiverse.mailpit.test.invoker.ApiException;
import io.quarkiverse.mailpit.test.model.Message;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

@QuarkusTest
@WithMailbox
public class MailpitResourceTest {

    @InjectMailbox
    Mailbox mailbox;

    @AfterEach
    public void afterEach() throws ApiException {
        // clear the mailbox after each test run if you prefer
        mailbox.clear();
    }

    @Test
    public void testAlert() throws ApiException {
        given()
                .when().get("/mailpit/alert")
                .then()
                .statusCode(200)
                .body(is("Superheroes alerted!!"));

        // look up the mail and assert it
        Message message = mailbox.findFirst("admin@hallofjustice.net");
        assertThat(message, notNullValue());
        assertThat(message.getTo().get(0).getAddress(), is("superheroes@quarkus.io"));
        assertThat(message.getSubject(), is("WARNING: Super Villain Alert"));
        assertThat(message.getText(), is("Lex Luthor has been seen in Metropolis!\r\n"));
    }

    @Test
    public void testSpam() throws ApiException {
        final int count = 600;
        given()
                .when().get("/mailpit/spam/" + count)
                .then()
                .statusCode(200)
                .body(is(count + " emails sent!!"));

        // look up the mail and assert it
        List<Message> message = mailbox.find("from:\"spam\"", 0, count);
        assertThat(message, notNullValue());
        assertThat(message.size(), is(count));
    }

    @Test
    public void testVertxAlert() throws ApiException, InterruptedException {
        given()
                .when().get("/mailpit/vertx-alert")
                .then()
                .statusCode(200)
                .body(is("Vert.x Superheroes alerted!!"));

        Thread.sleep(2000);
        // look up the mail and assert it
        Message message = mailbox.findFirst("vert.x@hallofjustice.net");
        assertThat(message, notNullValue());
        assertThat(message.getTo().get(0).getAddress(), is("vert.x@quarkus.io"));
        assertThat(message.getSubject(), is("WARNING: Vert.x Super Villain Alert"));
        assertThat(message.getText(), is("Vert.x Lex Luthor has been seen in Metropolis!\r\n"));
    }

    @Test
    public void testCustomFrom() throws ApiException {
        given()
                .when().get("/mailpit/from")
                .then()
                .statusCode(200)
                .body(is("Sent from info@melloware.com"));

        // look up the mail and assert it
        Message message = mailbox.findFirst("Ahoy");
        assertThat(message, notNullValue());
        assertThat(message.getFrom().getAddress(), is("info@melloware.com"));
        assertThat(message.getTo().get(0).getAddress(), is("quarkus@quarkus.io"));
        assertThat(message.getSubject(), is("Ahoy from Quarkus"));
        assertThat(message.getText(), is("A simple email sent from a Quarkus application.\r\n"));
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