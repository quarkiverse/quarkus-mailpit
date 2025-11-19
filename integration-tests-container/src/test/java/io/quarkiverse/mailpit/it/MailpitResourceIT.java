package io.quarkiverse.mailpit.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mailpit.test.InjectMailbox;
import io.quarkiverse.mailpit.test.Mailbox;
import io.quarkiverse.mailpit.test.WithMailbox;
import io.quarkiverse.mailpit.test.model.Message;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@WithMailbox
public class MailpitResourceIT {

    @InjectMailbox
    Mailbox mailbox;

    @AfterEach
    public void afterEach() {
        // clear the mailbox after each test run if you prefer
        mailbox.clear();
    }

    @Test
    public void testAlert() {
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
}
