package io.quarkiverse.mailpit.deployment;

import static io.quarkus.devservices.common.ContainerLocator.locateContainerWithLabels;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.dev.devservices.RunningContainer;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.mailer.MailerName;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;

/**
 * Starts a Mailpit server as dev service if needed.
 */
@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
public class MailpitProcessor {

    private static final Logger log = Logger.getLogger(MailpitProcessor.class);

    public static final String FEATURE = "mailpit";

    private static final int MAILPIT_SMTP_PORT = 1025;
    private static final int MAILPIT_HTTP_PORT = 8025;

    /**
     * Label to add to shared Dev Service for Mailpit running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-mailpit";

    private static final ContainerLocator mailpitContainerLocator = locateContainerWithLabels(MAILPIT_SMTP_PORT,
            DEV_SERVICE_LABEL);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public DevServicesResultBuildItem startMailpitDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            DevServicesComposeProjectBuildItem compose,
            LaunchModeBuildItem launchMode,
            MailpitConfig mailpitConfig,
            DevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> sharedNetwork,
            CombinedIndexBuildItem combinedIndexBuildItem,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {

        if (devServiceDisabled(dockerStatusBuildItem, mailpitConfig)) {
            return null;
        }

        boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                sharedNetwork);
        String path = nonApplicationRootPathBuildItem.resolvePath(FEATURE);
        IndexView index = combinedIndexBuildItem.getIndex();

        return mailpitContainerLocator.locateContainer(FEATURE, false, launchMode.getLaunchMode())
                .or(() -> ComposeLocator.locateContainer(compose,
                        List.of(mailpitConfig.imageName(), "mailpit"),
                        MAILPIT_SMTP_PORT, launchMode.getLaunchMode(), useSharedNetwork))
                .map(containerAddress -> {
                    RunningContainer container = containerAddress.getRunningContainer();
                    if (container == null) {
                        return null;
                    }
                    String host = containerAddress.getHost();
                    int smtpPort = containerAddress.getPort();
                    int httpPort = container.getPortMapping(MAILPIT_HTTP_PORT).orElse(0);
                    Map<String, String> discovered = discoveredConfig(host, smtpPort, httpPort, index);
                    container.tryGetEnv(MailpitContainer.WEBROOT)
                            .ifPresent(webroot -> discovered.put(MailpitContainer.WEBROOT, webroot));
                    return DevServicesResultBuildItem.discovered()
                            .feature(FEATURE)
                            .containerId(containerAddress.getId())
                            .config(discovered)
                            .build();
                })
                .orElseGet(() -> DevServicesResultBuildItem.owned()
                        .feature(FEATURE)
                        .serviceConfig(mailpitConfig)
                        .serviceName(FEATURE)
                        .startable(() -> {
                            MailpitContainer mailpit = new MailpitContainer(mailpitConfig, useSharedNetwork, index, path);
                            devServicesConfig.timeout().ifPresent(mailpit::withStartupTimeout);
                            return mailpit;
                        })
                        .postStartHook(this::logStarted)
                        .configProvider(exposedConfig(mailpitConfigKeys(index, mailpitConfig)))
                        .build());
    }

    private boolean devServiceDisabled(DockerStatusBuildItem dockerStatusBuildItem, MailpitConfig mailpitConfig) {
        if (!mailpitConfig.enabled()) {
            log.debug("Not starting Dev Services for Mailpit, as it has been disabled in the config.");
            return true;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker/Podman isn't working, not starting dev services for Mailpit.");
            return true;
        }

        return false;
    }

    private void logStarted(MailpitContainer container) {
        log.infof("Dev Services for Mailpit started at %s. Mailpit UI and SMTP are configured automatically.",
                container.getConnectionInfo());
    }

    private static Map<String, String> discoveredConfig(String host, int smtpPort, int httpPort, IndexView index) {
        Map<String, String> config = new HashMap<>();
        config.put(MailpitContainer.CONFIG_HTTP_HOST, host);
        config.put(MailpitContainer.CONFIG_HTTP_PORT, String.valueOf(httpPort));
        config.put(MailpitContainer.CONFIG_HTTP_SERVER, String.format("http://%s:%d", host, httpPort));
        config.put(MailpitContainer.CONFIG_SMTP_PORT, String.valueOf(smtpPort));
        config.put("quarkus.mailer.port", String.valueOf(smtpPort));
        config.put("quarkus.mailer.host", host);
        config.put("quarkus.mailer.mock", "false");
        for (AnnotationInstance namedMailer : index.getAnnotations(MailerName.class.getName())) {
            String name = namedMailer.value().asString();
            config.put("quarkus.mailer." + name + ".port", String.valueOf(smtpPort));
            config.put("quarkus.mailer." + name + ".host", host);
            config.put("quarkus.mailer." + name + ".mock", "false");
        }
        return config;
    }

    private static Set<String> mailpitConfigKeys(IndexView index, MailpitConfig mailpitConfig) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(MailpitContainer.CONFIG_HTTP_HOST);
        keys.add(MailpitContainer.CONFIG_HTTP_PORT);
        keys.add(MailpitContainer.CONFIG_HTTP_SERVER);
        keys.add(MailpitContainer.CONFIG_SMTP_PORT);
        keys.add("quarkus.mailer.port");
        keys.add("quarkus.mailer.host");
        keys.add("quarkus.mailer.mock");
        for (AnnotationInstance namedMailer : index.getAnnotations(MailerName.class.getName())) {
            String name = namedMailer.value().asString();
            keys.add("quarkus.mailer." + name + ".port");
            keys.add("quarkus.mailer." + name + ".host");
            keys.add("quarkus.mailer." + name + ".mock");
        }
        addMailpitEnvConfigKeys(keys, mailpitConfig);
        return keys;
    }

    private static void addMailpitEnvConfigKeys(Set<String> keys, MailpitConfig config) {
        keys.add(MailpitContainer.WEBROOT);
        keys.add("MP_MAX_MESSAGES");
        keys.add("MP_SMTP_RELAY_PORT");
        keys.add("MP_SMTP_RELAY_STARTTLS");
        keys.add("MP_SMTP_RELAY_TLS");
        keys.add("MP_SMTP_RELAY_ALLOW_INSECURE");
        keys.add("MP_SMTP_RELAY_AUTH");
        keys.add("MP_SMTP_RELAY_ALL");
        if (config.verbose()) {
            keys.add("MP_VERBOSE");
        }
        if (config.enableChaos()) {
            keys.add("MP_ENABLE_CHAOS");
        }
        config.smtpRelayHost().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_HOST"));
        config.smtpRelayUsername().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_USERNAME"));
        config.smtpRelayPassword().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_PASSWORD"));
        config.smtpRelaySecret().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_SECRET"));
        config.smtpRelayReturnPath().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_RETURN_PATH"));
        config.smtpRelayOverrideFrom().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_OVERRIDE_FROM"));
        config.smtpRelayAllowedRecipients().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_ALLOWED_RECIPIENTS"));
        config.smtpRelayBlockedRecipients().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_BLOCKED_RECIPIENTS"));
        config.smtpRelayMatching().ifPresent(ignored -> keys.add("MP_SMTP_RELAY_MATCHING"));
    }

    /**
     * Resolves the given keys from the started container's exposed config, skipping keys without a value.
     */
    private static Function<MailpitContainer, Map<String, String>> exposedConfig(Set<String> keys) {
        return container -> {
            Map<String, String> exposed = container.getExposedConfig();
            Map<String, String> result = new HashMap<>();
            for (String key : keys) {
                String value = exposed.get(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
            return result;
        };
    }

}
