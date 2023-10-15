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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

@Path("/mailpit")
@ApplicationScoped
public class MailpitResource {

    @Inject
    Mailer mailer;

    @Path("/alert")
    @GET
    public String villainAlert() {
        Mail m = new Mail();
        m.setFrom("admin@hallofjustice.net");
        m.setTo(List.of("superheroes@quarkus.io"));
        m.setText("Lex Luthor has been seen in Gotham City!");
        m.setSubject("WARNING: Super Villain Alert");
        mailer.send(m);

        return "Superheroes alerted!!";
    }

    @Path("/from")
    @GET
    public String from() {
        Mail m = new Mail();
        m.setFrom("info@melloware.com");
        m.setTo(List.of("quarkus@quarkus.io"));
        m.setText("A simple email sent from a Quarkus application.");
        m.setSubject("Ahoy from Quarkus");
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

        return String.format("Sent from nobody", m.getFrom());
    }
}