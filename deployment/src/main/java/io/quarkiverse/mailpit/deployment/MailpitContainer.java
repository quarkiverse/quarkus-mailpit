package io.quarkiverse.mailpit.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.devservices.common.ConfigureUtil;

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
     * Runtime mail port from either "quarkus.mailer.port" or 1025 if not found
     */
    private final Integer runtimeMailPort;
    /**
     * Flag whether to use shared networking
     */
    private final boolean useSharedNetwork;
    /**
     * The dynamic host name determined from TestContainers.
     */
    private String hostName;

    MailpitContainer(MailpitConfig config, boolean useSharedNetwork) {
        super(DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(MailpitConfig.DEFAULT_IMAGE));
        this.runtimeMailPort = getMailPort();
        this.useSharedNetwork = useSharedNetwork;

        withLabel(MailpitProcessor.DEV_SERVICE_LABEL, MailpitProcessor.FEATURE);
        withExposedPorts(PORT_HTTP);
        waitingFor(Wait.forHttp("/").forPort(PORT_HTTP));

        // configure verbose container logging
        if (config.verbose()) {
            withEnv("MP_VERBOSE", "true");
        }

        // forward the container logs
        withLogConsumer(new JbossContainerLogConsumer(log).withPrefix(MailpitProcessor.FEATURE));
    }

    @Override
    protected void configure() {
        super.configure();

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, MailpitProcessor.FEATURE);
            return;
        }

        // this forces the SMTP port to match what the user has configured for quarkus.mailer.port
        addFixedExposedPort(this.runtimeMailPort, PORT_SMTP);
    }

    /**
     * Info about the DevService used in the DevUI.
     *
     * @return the map of as running configuration of the dev service
     */
    public Map<String, String> getExposedConfig() {
        Map<String, String> exposed = new HashMap<>(2);
        exposed.put(CONFIG_SMTP_PORT, Objects.toString(getMailPort()));
        exposed.put(CONFIG_HTTP_SERVER, getMailpitHttpServer());
        return exposed;
    }

    /**
     * Get the calculated Mailpit UI location for use in the DevUI.
     *
     * @return the calculated full URL to the Mailpit UI
     */
    public String getMailpitHttpServer() {
        return String.format("http://%s:%d", getMailpitHost(), getMappedPort(PORT_HTTP));
    }

    /**
     * Get the calculated Mailpit host address.
     *
     * @return the host address
     */
    public String getMailpitHost() {
        if (hostName != null && !hostName.isEmpty()) {
            return hostName;
        } else {
            return getHost();
        }
    }

    /**
     * Use "quarkus.mailer.port" to configure Mailpit as its exposed SMTP port.
     *
     * @return the mailer port or -1 if not found which will cause this service not to start
     */
    public static Integer getMailPort() {
        final Optional<Integer> mailPort = ConfigProvider.getConfig().getOptionalValue("quarkus.mailer.port",
                Integer.class);
        return mailPort.orElse(-1);
    }
}