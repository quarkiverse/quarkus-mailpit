package io.quarkiverse.mailpit.runtime;

import java.util.function.Supplier;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

@Recorder
public class MailpitUiProxy {

    private static final Logger log = Logger.getLogger(MailpitUiProxy.class);

    public Handler<RoutingContext> handler(Supplier<Vertx> vertx) {
        final var portOptional = ConfigProvider.getConfig().getOptionalValue("mailpit.http.port", Integer.class);
        final var client = WebClient.create(vertx.get());

        return event -> {
            if (portOptional.isEmpty()) {
                event.response().setStatusCode(404).end();
                return;
            }

            final Integer port = portOptional.get();
            final HttpRequest<Buffer> r = client.request(event.request().method(), port, "localhost",
                    event.request().uri());

            // copy all headers
            event.request().headers().forEach(h -> r.putHeader(h.getKey(), h.getValue()));

            if ("websocket".equals(event.request().getHeader("upgrade"))) {
                // handle WebSocket request
                event.request().toWebSocket().onComplete(ws -> {
                    if (ws.succeeded()) {
                        event.request().resume();
                        ws.result().handler(buff -> event.response().write(buff));
                    } else {
                        log.error("WebSocket failed", ws.cause());
                    }
                });
            } else {
                // handle normal request
                r.sendBuffer(event.body().buffer()).andThen(resp -> {
                    event.response().setStatusCode(resp.result().statusCode());
                    resp.result().headers().forEach(h -> event.response().putHeader(h.getKey(), h.getValue()));
                    event.response().end(resp.result().bodyAsBuffer());
                });
            }
        };
    }
}