package io.quarkiverse.mailpit.deployment;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.mailer.MailerName;

/**
 * Testcontainers implementation for Mailpit mail server.
 * <p>
 * Supported image: {@code axllent/mailpit}
 * Find more info about Mailpit on <a href="https://github.com/axllent/mailpit">https://github.com/axllent/mailpit</a>.
 * <p>
 * Exposed ports: 1025 (smtp)
 * Exposed ports: 8025 (http user interface)
 */
public final class MailpitContainer extends GenericContainer<MailpitContainer> {

    public static final String CONFIG_SMTP_PORT = MailpitProcessor.FEATURE + ".smtp.port";
    public static final String CONFIG_HTTP_SERVER = MailpitProcessor.FEATURE + ".http.server";
    public static final String CONFIG_HTTP_HOST = MailpitProcessor.FEATURE + ".http.host";
    public static final String CONFIG_HTTP_PORT = MailpitProcessor.FEATURE + ".http.port";

    /**
     * Logger which will be used to capture container STDOUT and STDERR.
     */
    private static final Logger log = Logger.getLogger(MailpitContainer.class);
    /**
     * Default Mailpit SMTP Port.
     */
    private static final Integer PORT_SMTP = 1025;
    /**
     * Default Mailpit HTTP Port for UI.
     */
    private static final Integer PORT_HTTP = 8025;
    /**
     * Flag whether to use shared networking
     */
    private final boolean useSharedNetwork;
    /**
     * The dynamic host name determined from TestContainers.
     */
    private String hostName;
    private final IndexView index;
    private final OptionalInt mappedFixedPort;

    MailpitContainer(MailpitConfig config, boolean useSharedNetwork, IndexView index, String path) {
        super(DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(MailpitConfig.DEFAULT_IMAGE));
        this.useSharedNetwork = useSharedNetwork;
        this.index = index;

        super.withLabel(MailpitProcessor.DEV_SERVICE_LABEL, MailpitProcessor.FEATURE);
        super.withEnv("MP_WEBROOT", path);
        super.withNetwork(Network.SHARED);
        super.waitingFor(Wait.forLogMessage(".*\\[http\\] accessible via.*\n", 1));

        // Mapped http port
        this.mappedFixedPort = config.mappedHttpPort();

        // configure verbose container logging
        if (config.verbose()) {
            super.withEnv("MP_VERBOSE", "true");
        }

        // configure chaos testing
        if (config.enableChaos()) {
            super.withEnv("MP_ENABLE_CHAOS", "true");
        }

        // max messages
        super.withEnv("MP_MAX_MESSAGES", Objects.toString(config.maxMessages()));

        // SMTP relay configuration
        config.smtpRelayHost().ifPresent(host -> super.withEnv("MP_SMTP_RELAY_HOST", host));
        super.withEnv("MP_SMTP_RELAY_PORT", Objects.toString(config.smtpRelayPort()));
        super.withEnv("MP_SMTP_RELAY_STARTTLS", Objects.toString(config.smtpRelayStarttls()));
        super.withEnv("MP_SMTP_RELAY_TLS", Objects.toString(config.smtpRelayTls()));
        super.withEnv("MP_SMTP_RELAY_ALLOW_INSECURE", Objects.toString(config.smtpRelayAllowInsecure()));
        super.withEnv("MP_SMTP_RELAY_AUTH", config.smtpRelayAuth());
        config.smtpRelayUsername().ifPresent(username -> super.withEnv("MP_SMTP_RELAY_USERNAME", username));
        config.smtpRelayPassword().ifPresent(password -> super.withEnv("MP_SMTP_RELAY_PASSWORD", password));
        config.smtpRelaySecret().ifPresent(secret -> super.withEnv("MP_SMTP_RELAY_SECRET", secret));
        config.smtpRelayReturnPath().ifPresent(relayPath -> super.withEnv("MP_SMTP_RELAY_RETURN_PATH", relayPath));
        config.smtpRelayOverrideFrom().ifPresent(from -> super.withEnv("MP_SMTP_RELAY_OVERRIDE_FROM", from));
        config.smtpRelayAllowedRecipients()
                .ifPresent(recipients -> super.withEnv("MP_SMTP_RELAY_ALLOWED_RECIPIENTS", recipients));
        config.smtpRelayBlockedRecipients()
                .ifPresent(recipients -> super.withEnv("MP_SMTP_RELAY_BLOCKED_RECIPIENTS", recipients));
        super.withEnv("MP_SMTP_RELAY_ALL", Objects.toString(config.smtpRelayAll()));
        config.smtpRelayMatching().ifPresent(matching -> super.withEnv("MP_SMTP_RELAY_MATCHING", matching));

        // forward the container logs
        super.withLogConsumer(new JbossContainerLogConsumer(log).withPrefix(MailpitProcessor.FEATURE));
    }

    @Override
    protected void configure() {
        super.configure();

        // this forces the HTTP port to a fixed port if configured by the user
        if (this.mappedFixedPort.isPresent()) {
            addFixedExposedPort(this.mappedFixedPort.getAsInt(), PORT_HTTP);
        } else {
            addExposedPorts(PORT_HTTP);
        }

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, MailpitProcessor.FEATURE);
            return;
        }

        // this forces the SMTP port to match what the user has configured for quarkus.mailer.port
        // and the HTTP port for the DevUI
        Optional<Integer> mailerPortConfig = ConfigProvider.getConfig().getOptionalValue("quarkus.mailer.port", Integer.class);
        if (mailerPortConfig.isPresent()) {
            addFixedExposedPort(mailerPortConfig.get(), PORT_SMTP);
        } else {
            addExposedPorts(PORT_SMTP);
        }
    }

    /**
     * Info about the DevService used in the DevUI.
     *
     * @return the map of as running configuration of the dev service
     */
    public Map<String, String> getExposedConfig() {
        Map<String, String> exposed = new HashMap<>();

        // host and ports for configuration
        final String host = getHost();
        final String mailerHost = useSharedNetwork ? hostName : host;
        final String mailpitHttpServer = String.format("http://%s:%d", host, getMappedPort(PORT_HTTP));
        final Integer smtpPort = useSharedNetwork ? PORT_SMTP : getMappedPort(PORT_SMTP);
        final Integer httpPort = getMappedPort(PORT_HTTP);

        // mailpit specific
        exposed.put(CONFIG_HTTP_HOST, host);
        exposed.put(CONFIG_HTTP_PORT, String.valueOf(httpPort));
        exposed.put(CONFIG_HTTP_SERVER, mailpitHttpServer);
        exposed.put(CONFIG_SMTP_PORT, String.valueOf(smtpPort));
        exposed.putAll(super.getEnvMap());

        // quarkus mailer default
        exposed.put("quarkus.mailer.port", String.valueOf(smtpPort));
        exposed.put("quarkus.mailer.host", mailerHost);
        exposed.put("quarkus.mailer.mock", "false");

        // quarkus mailer named mailers
        Collection<AnnotationInstance> namedMailers = index.getAnnotations(MailerName.class.getName());
        for (AnnotationInstance namedMailer : namedMailers) {
            String name = namedMailer.value().asString();
            exposed.put("quarkus.mailer." + name + ".port", String.valueOf(smtpPort));
            exposed.put("quarkus.mailer." + name + ".host", mailerHost);
            exposed.put("quarkus.mailer." + name + ".mock", "false");
        }

        return exposed;
    }
}
