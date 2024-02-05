package io.quarkiverse.mailpit.deployment;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    private IndexView index;

    MailpitContainer(MailpitConfig config, boolean useSharedNetwork, IndexView index) {
        super(DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(MailpitConfig.DEFAULT_IMAGE));
        this.useSharedNetwork = useSharedNetwork;
        this.index = index;

        super.withLabel(MailpitProcessor.DEV_SERVICE_LABEL, MailpitProcessor.FEATURE);
        super.withNetwork(Network.SHARED);
        super.waitingFor(Wait.forHttp("/").forPort(PORT_HTTP));

        // configure verbose container logging
        if (config.verbose()) {
            super.withEnv("MP_VERBOSE", "true");
        }

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
        addExposedPorts(PORT_SMTP, PORT_HTTP);
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
        return String.format("http://%s:%d", getHost(), getMappedPort(PORT_HTTP));
    }
}
