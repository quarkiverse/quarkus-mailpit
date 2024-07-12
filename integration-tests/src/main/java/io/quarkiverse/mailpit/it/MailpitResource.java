/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.mailpit.it;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.mailer.Attachment;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.MailerName;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;

@Path("/mailpit")
@Produces(MediaType.TEXT_PLAIN)
@ApplicationScoped
public class MailpitResource {

    @Inject
    @MailerName("smtp1")
    Mailer mailer;

    @Inject
    MailClient vertxClient;

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

    @Path("/spam/{count}")
    @GET
    public String spamBot(@PathParam("count") int count) {
        for (int i = 0; i < count; i++) {
            final Mail m = new Mail();
            m.setFrom("blaster@spam.net");
            m.setTo(List.of("superheroes@quarkus.io"));
            m.setSubject("Spam Bot " + i);
            m.setText("You just got spammed " + i + " times.");
            m.setHeaders(Map.of("X-Tags", List.of("Spam")));
            mailer.send(m);
        }

        return count + " emails sent!!";
    }

    @Path("/alert/html")
    @GET
    public String villainAlertHtml() {
        final Mail m = new Mail();
        m.setFrom("admin@hallofjustice.net");
        m.setTo(List.of("superheroes@quarkus.io"));
        m.setSubject("WARNING: Super Villain Alert");
        m.setHtml("<strong>Lex Luthor</strong> has been seen in <i>Metropolis</i>!");
        m.setHeaders(Map.of("X-Tags", List.of("Quarkus, Superheroes, Alert, HTML")));
        mailer.send(m);

        return "Superheroes alerted!!";
    }

    @Path("/vertx-alert")
    @GET
    public String vertxAlert() {
        MailMessage message = new MailMessage();

        message.setFrom("vert.x@hallofjustice.net");
        message.setTo(List.of("vert.x@quarkus.io"));
        message.setSubject("WARNING: Vert.x Super Villain Alert");
        message.setText("Vert.x Lex Luthor has been seen in Metropolis!");

        vertxClient.sendMail(message);

        return "Vert.x Superheroes alerted!!";
    }

    @Path("/send-attachment")
    @GET
    public String sendAttachment() {
        final Mail m = new Mail();
        m.setFrom("admin@hallofjustice.net");
        m.setTo(List.of("superheroes@quarkus.io"));
        m.setSubject("File Attachment");
        m.setText("Please find the attached file.");

        // Create a simple file attachment
        Attachment attachment = new Attachment("simple-file.txt", "This is the content of the file.".getBytes(), "text/plain");

        m.setAttachments(List.of(attachment));
        m.setHeaders(Map.of("X-Tags", List.of("Quarkus, Superheroes, Attachment")));
        mailer.send(m);

        return "Email with attachment sent!";
    }

    @Path("/from")
    @GET
    public String from() {
        Mail m = new Mail();
        m.setFrom("info@melloware.com");
        m.setTo(List.of("quarkus@quarkus.io"));
        m.setText("A simple email sent from a Quarkus application.");
        m.setSubject("Ahoy from Quarkus");
        m.setHeaders(Map.of("X-Tags", List.of("Quarkus")));
        mailer.send(m);

        return String.format("Sent from %s", m.getFrom());
    }

    @Path("/nullfrom")
    @GET
    public String nullFrom() {
        Mail m = new Mail();
        m.setFrom(null);
        m.setTo(List.of("quarkus@quarkus.io"));
        m.setText("A simple email sent from a Quarkus application.");
        m.setSubject("Ahoy from Quarkus");
        mailer.send(m);

        return String.format("Sent from nobody %s", m.getFrom());
    }
}