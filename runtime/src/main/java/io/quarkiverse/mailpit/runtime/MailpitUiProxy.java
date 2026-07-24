package io.quarkiverse.mailpit.runtime;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

@Recorder
public class MailpitUiProxy {

    private static final Logger log = Logger.getLogger(MailpitUiProxy.class);

    private static final String WEBSOCKET = "websocket";
    private static final String LOCALHOST = "localhost";

    /**
     * Headers negotiated per connection: the Vert.x WebSocket client generates its own, so forwarding the
     * browser's values would corrupt the handshake with Mailpit. Any header prefixed with {@code sec-websocket-}
     * is skipped along with these.
     */
    private static final Set<String> PER_CONNECTION_HEADERS = Set.of(
            HttpHeaders.HOST.toString(),
            HttpHeaders.CONNECTION.toString(),
            HttpHeaders.UPGRADE.toString(),
            HttpHeaders.ORIGIN.toString());

    public Handler<RoutingContext> handler(Supplier<Vertx> vertx) {
        final var portOptional = ConfigProvider.getConfig().getOptionalValue("mailpit.http.port", Integer.class);
        final var client = WebClient.create(vertx.get());
        final var webSocketClient = vertx.get().createWebSocketClient();

        return event -> {
            if (portOptional.isEmpty()) {
                event.response().setStatusCode(404).end();
                return;
            }

            final Integer port = portOptional.get();

            if (event.request().headers().contains(HttpHeaders.UPGRADE, WEBSOCKET, true)) {
                proxyWebSocket(webSocketClient, event, port);
                return;
            }

            final HttpRequest<Buffer> r = client.request(event.request().method(), port, LOCALHOST,
                    event.request().uri());

            // copy all headers
            event.request().headers().forEach(h -> r.putHeader(h.getKey(), h.getValue()));

            // handle normal request
            event.request().resume();
            event.request().body().onComplete(body -> {
                r.sendBuffer(body.result()).onComplete(resp -> {
                    if (resp.succeeded()) {
                        event.response().setStatusCode(resp.result().statusCode());
                        resp.result().headers().forEach(h -> event.response().putHeader(h.getKey(), h.getValue()));
                        event.response().end(resp.result().body());
                    } else {
                        // Occurs if the connection to the docker container fails
                        // Does not overwrite API error status codes from client which are handled in succeeded path
                        // If end() omitted leaves connections hanging
                        event.response().setStatusCode(500).end();
                    }
                });
            });
        };
    }

    /**
     * Open a second WebSocket towards Mailpit and relay both directions, so the live events Mailpit pushes on
     * {@code /api/events} reach the browser without a manual refresh.
     */
    private void proxyWebSocket(WebSocketClient webSocketClient, RoutingContext event, Integer port) {
        final WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setHost(LOCALHOST)
                .setPort(port)
                .setURI(event.request().uri())
                .setAllowOriginHeader(false);

        event.request().headers().forEach(h -> {
            if (!isPerConnectionHeader(h.getKey())) {
                options.addHeader(h.getKey(), h.getValue());
            }
        });

        // hold the client handshake until Mailpit accepted ours
        event.request().pause();

        webSocketClient.connect(options).onComplete(upstream -> {
            if (upstream.failed()) {
                log.error("Unable to open a WebSocket connection to Mailpit", upstream.cause());
                event.response().setStatusCode(502).end();
                return;
            }

            final WebSocket mailpit = upstream.result();
            event.request().resume();
            event.request().toWebSocket().onComplete(browser -> {
                if (browser.failed()) {
                    log.error("WebSocket upgrade failed", browser.cause());
                    close(mailpit);
                    return;
                }
                relay(browser.result(), mailpit);
            });
        });
    }

    private void relay(ServerWebSocket browser, WebSocket mailpit) {
        mailpit.textMessageHandler(browser::writeTextMessage);
        mailpit.binaryMessageHandler(browser::writeBinaryMessage);
        browser.textMessageHandler(mailpit::writeTextMessage);
        browser.binaryMessageHandler(mailpit::writeBinaryMessage);

        mailpit.closeHandler(v -> close(browser));
        browser.closeHandler(v -> close(mailpit));

        mailpit.exceptionHandler(t -> {
            log.debug("Mailpit WebSocket failed", t);
            close(browser);
        });
        browser.exceptionHandler(t -> {
            log.debug("Browser WebSocket failed", t);
            close(mailpit);
        });
    }

    private static boolean isPerConnectionHeader(String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return PER_CONNECTION_HEADERS.contains(lower) || lower.startsWith("sec-websocket-");
    }

    private static void close(WebSocketBase socket) {
        if (!socket.isClosed()) {
            socket.close();
        }
    }
}
