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
        super.waitingFor(Wait.forHttp(path).forPort(PORT_HTTP));

        // Mapped http port
        this.mappedFixedPort = config.mappedHttpPort();
        config.mappedHttpPort().ifPresent(fixedPort -> super.addFixedExposedPort(fixedPort, PORT_HTTP));

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

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, MailpitProcessor.FEATURE);
            return;
        }

        // this forces the SMTP port to match what the user has configured for quarkus.mailer.port
        // and the HTTP port for the DevUI
        Optional<Integer> mailerPortConfig = ConfigProvider.getConfig().getOptionalValue("quarkus.mailer.port", Integer.class);
        if (mailerPortConfig.isPresent()) {
            addFixedExposedPort(mailerPortConfig.get(), PORT_SMTP);
            addExposedPort(PORT_HTTP);
        } else {
            addExposedPorts(PORT_SMTP, PORT_HTTP);
        }
    }

    @Override
    public String getHost() {
        return useSharedNetwork ? this.hostName : super.getHost();
    }

    /**
     * Info about the DevService used in the DevUI.
     *
     * @return the map of as running configuration of the dev service
     */
    public Map<String, String> getExposedConfig() {
        Map<String, String> exposed = new HashMap<>();

        final String port = Objects.toString(getMappedPort(PORT_SMTP));

        // mailpit specific
        exposed.put(CONFIG_SMTP_PORT, port);
        exposed.put(CONFIG_HTTP_HOST, getHost());
        exposed.put(CONFIG_HTTP_PORT, String.valueOf(getMappedPort(PORT_HTTP)));
        exposed.put(CONFIG_HTTP_SERVER, getMailpitHttpServer());
        exposed.putAll(super.getEnvMap());

        // quarkus mailer default
        exposed.put("quarkus.mailer.port", port);
        exposed.put("quarkus.mailer.host", getHost());
        exposed.put("quarkus.mailer.mock", "false");

        // quarkus mailer named mailers
        Collection<AnnotationInstance> namedMailers = index.getAnnotations(MailerName.class.getName());
        for (AnnotationInstance namedMailer : namedMailers) {
            String name = namedMailer.value().asString();
            exposed.put("quarkus.mailer." + name + ".port", port);
            exposed.put("quarkus.mailer." + name + ".host", getHost());
            exposed.put("quarkus.mailer." + name + ".mock", "false");
        }

        return exposed;
    }

    /**
     * Get the calculated Mailpit UI location for use in the DevUI.
     *
     * @return the calculated full URL to the Mailpit UI
     */

    public String getMailpitHttpServer() {
        return String.format("http://%s:%d", getHost(), this.mappedFixedPort.orElseGet(() -> getMappedPort(PORT_HTTP)));
    }
}