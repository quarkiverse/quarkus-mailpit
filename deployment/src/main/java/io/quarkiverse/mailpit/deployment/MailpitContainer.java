package io.quarkiverse.mailpit.deployment;

import static io.quarkus.devservices.common.ConfigureUtil.configureSharedServiceLabel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.exception.NotFoundException;

import io.quarkus.deployment.builditem.Startable;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.mailer.MailerName;
import io.quarkus.runtime.LaunchMode;

/**
 * Testcontainers implementation for Mailpit mail server.
 * <p>
 * Supported image: {@code axllent/mailpit}
 * Find more info about Mailpit on <a href="https://github.com/axllent/mailpit">https://github.com/axllent/mailpit</a>.
 * <p>
 * Exposed ports: 1025 (smtp)
 * Exposed ports: 8025 (http user interface)
 */
public final class MailpitContainer extends GenericContainer<MailpitContainer> implements Startable {

    public static final String CONFIG_SMTP_PORT = MailpitProcessor.FEATURE + ".smtp.port";
    public static final String CONFIG_HTTP_SERVER = MailpitProcessor.FEATURE + ".http.server";
    public static final String CONFIG_HTTP_HOST = MailpitProcessor.FEATURE + ".http.host";
    public static final String CONFIG_HTTP_PORT = MailpitProcessor.FEATURE + ".http.port";

    static final int PORT_SMTP = 1025;
    static final int PORT_HTTP = 8025;

    private static final Logger log = Logger.getLogger(MailpitContainer.class);

    private final boolean useSharedNetwork;
    private final String defaultNetworkId;
    private String hostName;
    private final IndexView index;
    private final OptionalInt mappedFixedPort;

    MailpitContainer(MailpitConfig config, String defaultNetworkId, boolean useSharedNetwork, IndexView index,
            String path) {
        super(DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(MailpitConfig.DEFAULT_IMAGE));
        this.useSharedNetwork = useSharedNetwork;
        this.defaultNetworkId = defaultNetworkId;
        this.index = index;

        super.withLabel(MailpitProcessor.DEV_SERVICE_LABEL, MailpitProcessor.FEATURE);
        super.withEnv("MP_WEBROOT", path);
        super.withNetwork(Network.SHARED);
        super.waitingFor(Wait.forLogMessage(".*\\[http\\] accessible via.*\n", 1));

        this.mappedFixedPort = config.mappedHttpPort();

        if (config.verbose()) {
            super.withEnv("MP_VERBOSE", "true");
        }

        if (config.enableChaos()) {
            super.withEnv("MP_ENABLE_CHAOS", "true");
        }

        super.withEnv("MP_MAX_MESSAGES", Objects.toString(config.maxMessages()));

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

        super.withLogConsumer(new JbossContainerLogConsumer(log).withPrefix(MailpitProcessor.FEATURE));
    }

    MailpitContainer withSharedServiceLabel(LaunchMode launchMode, String serviceName) {
        configureSharedServiceLabel(this, launchMode, MailpitProcessor.DEV_SERVICE_LABEL, serviceName);
        return this;
    }

    @Override
    protected void configure() {
        super.configure();

        // HTTP port is always exposed (host or shared network mode) for Mailbox / Dev UI access from the host
        if (mappedFixedPort.isPresent()) {
            addFixedExposedPort(mappedFixedPort.getAsInt(), PORT_HTTP);
        } else {
            addExposedPorts(PORT_HTTP);
        }

        if (useSharedNetwork) {
            String networkId = resolveNetworkId();
            if (networkId != null) {
                createDockerNetworkIfNecessary(networkId);
            }
            hostName = ConfigureUtil.configureNetwork(this, networkId, true, MailpitProcessor.FEATURE);
            return;
        }

        Optional<Integer> mailerPortConfig = ConfigProvider.getConfig().getOptionalValue("quarkus.mailer.port", Integer.class);
        if (mailerPortConfig.isPresent()) {
            addFixedExposedPort(mailerPortConfig.get(), PORT_SMTP);
        } else {
            addExposedPorts(PORT_SMTP);
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public String getConnectionInfo() {
        return getExposedConfig().get(CONFIG_HTTP_SERVER);
    }

    /**
     * Running configuration of the dev service (after the container has started).
     */
    public Map<String, String> getExposedConfig() {
        Map<String, String> exposed = new HashMap<>();

        final String host = getHost();
        final String mailerHost = useSharedNetwork ? hostName : host;
        final String mailpitHttpServer = String.format("http://%s:%d", host, getMappedPort(PORT_HTTP));
        final Integer smtpPort = useSharedNetwork ? PORT_SMTP : getMappedPort(PORT_SMTP);
        final Integer httpPort = getMappedPort(PORT_HTTP);

        exposed.put(CONFIG_HTTP_HOST, host);
        exposed.put(CONFIG_HTTP_PORT, String.valueOf(httpPort));
        exposed.put(CONFIG_HTTP_SERVER, mailpitHttpServer);
        exposed.put(CONFIG_SMTP_PORT, String.valueOf(smtpPort));
        exposed.putAll(super.getEnvMap());

        exposed.put("quarkus.mailer.port", String.valueOf(smtpPort));
        exposed.put("quarkus.mailer.host", mailerHost);
        exposed.put("quarkus.mailer.mock", "false");

        Collection<AnnotationInstance> namedMailers = index.getAnnotations(MailerName.class.getName());
        for (AnnotationInstance namedMailer : namedMailers) {
            String name = namedMailer.value().asString();
            exposed.put("quarkus.mailer." + name + ".port", String.valueOf(smtpPort));
            exposed.put("quarkus.mailer." + name + ".host", mailerHost);
            exposed.put("quarkus.mailer." + name + ".mock", "false");
        }

        return exposed;
    }

    private String resolveNetworkId() {
        if (defaultNetworkId != null) {
            return defaultNetworkId;
        }
        return ConfigProvider.getConfig().getOptionalValue("quarkus.test.container.network", String.class).orElse(null);
    }

    private static void createDockerNetworkIfNecessary(String networkId) {
        try {
            DockerClientFactory.instance().client().inspectNetworkCmd().withNetworkId(networkId).exec();
        } catch (NotFoundException e) {
            DockerClientFactory.instance().client().createNetworkCmd().withName(networkId).exec();
        }
    }

    static Map<String, Function<MailpitContainer, String>> applicationConfigProvider(IndexView index) {
        Map<String, Function<MailpitContainer, String>> providers = new LinkedHashMap<>();
        for (String key : configKeys(index)) {
            providers.put(key, container -> container.getExposedConfig().get(key));
        }
        return providers;
    }

    static Map<String, String> discoveredConfig(String host, int smtpPublicPort, int httpPublicPort, String webroot,
            IndexView index, boolean useSharedNetwork) {
        Map<String, String> exposed = new HashMap<>();
        final String mailerHost = host;
        final String mailpitHttpServer = String.format("http://%s:%d", host, httpPublicPort);

        exposed.put(CONFIG_HTTP_HOST, host);
        exposed.put(CONFIG_HTTP_PORT, String.valueOf(httpPublicPort));
        exposed.put(CONFIG_HTTP_SERVER, mailpitHttpServer);
        exposed.put("MP_WEBROOT", webroot);
        exposed.put(CONFIG_SMTP_PORT, String.valueOf(smtpPublicPort));
        exposed.put("quarkus.mailer.port", String.valueOf(smtpPublicPort));
        exposed.put("quarkus.mailer.host", mailerHost);
        exposed.put("quarkus.mailer.mock", "false");

        Collection<AnnotationInstance> namedMailers = index.getAnnotations(MailerName.class.getName());
        for (AnnotationInstance namedMailer : namedMailers) {
            String name = namedMailer.value().asString();
            exposed.put("quarkus.mailer." + name + ".port", String.valueOf(smtpPublicPort));
            exposed.put("quarkus.mailer." + name + ".host", mailerHost);
            exposed.put("quarkus.mailer." + name + ".mock", "false");
        }
        return exposed;
    }

    private static List<String> configKeys(IndexView index) {
        List<String> keys = new ArrayList<>();
        keys.add(CONFIG_HTTP_HOST);
        keys.add(CONFIG_HTTP_PORT);
        keys.add(CONFIG_HTTP_SERVER);
        keys.add(CONFIG_SMTP_PORT);
        keys.add("MP_WEBROOT");
        keys.add("quarkus.mailer.port");
        keys.add("quarkus.mailer.host");
        keys.add("quarkus.mailer.mock");
        for (AnnotationInstance namedMailer : index.getAnnotations(MailerName.class.getName())) {
            String name = namedMailer.value().asString();
            keys.add("quarkus.mailer." + name + ".port");
            keys.add("quarkus.mailer." + name + ".host");
            keys.add("quarkus.mailer." + name + ".mock");
        }
        return keys;
    }
}
