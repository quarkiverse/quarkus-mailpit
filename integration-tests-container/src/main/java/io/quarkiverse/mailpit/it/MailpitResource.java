package io.quarkiverse.mailpit.it;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.MailerName;

@Path("/mailpit")
@Produces(MediaType.TEXT_PLAIN)
@ApplicationScoped
public class MailpitResource {

    @Inject
    @MailerName("smtp1")
    Mailer mailer;

    @Path("/alert")
    @GET
    public String villainAlert() {
        final Mail m = new Mail();
        m.setFrom("admin@hallofjustice.net");
        m.setTo(List.of("superheroes@quarkus.io"));
        m.setSubject("WARNING: Super Villain Alert");
        m.setText("Lex Luthor has been seen in Metropolis!");
        m.setHeaders(Map.of("X-Tags", List.of("Quarkus, Superheroes, Alert")));
        mailer.send(m);

        return "Superheroes alerted!!";
    }

}
