package io.quarkiverse.mailpit.deployment;

import static io.quarkus.devservices.common.ContainerLocator.locateContainerWithLabels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                        .configProvider(mailpitConfigProvider(index, mailpitConfig))
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

    private static Map<String, Function<MailpitContainer, String>> mailpitConfigProvider(IndexView index,
            MailpitConfig mailpitConfig) {
        Map<String, Function<MailpitContainer, String>> providers = new HashMap<>();
        providers.put(MailpitContainer.CONFIG_HTTP_HOST, configValue(MailpitContainer.CONFIG_HTTP_HOST));
        providers.put(MailpitContainer.CONFIG_HTTP_PORT, configValue(MailpitContainer.CONFIG_HTTP_PORT));
        providers.put(MailpitContainer.CONFIG_HTTP_SERVER, configValue(MailpitContainer.CONFIG_HTTP_SERVER));
        providers.put(MailpitContainer.CONFIG_SMTP_PORT, configValue(MailpitContainer.CONFIG_SMTP_PORT));
        providers.put("quarkus.mailer.port", configValue("quarkus.mailer.port"));
        providers.put("quarkus.mailer.host", configValue("quarkus.mailer.host"));
        providers.put("quarkus.mailer.mock", configValue("quarkus.mailer.mock"));
        for (AnnotationInstance namedMailer : index.getAnnotations(MailerName.class.getName())) {
            String name = namedMailer.value().asString();
            providers.put("quarkus.mailer." + name + ".port", configValue("quarkus.mailer." + name + ".port"));
            providers.put("quarkus.mailer." + name + ".host", configValue("quarkus.mailer." + name + ".host"));
            providers.put("quarkus.mailer." + name + ".mock", configValue("quarkus.mailer." + name + ".mock"));
        }
        addMailpitEnvConfigProviders(providers, mailpitConfig);
        return providers;
    }

    private static void addMailpitEnvConfigProviders(Map<String, Function<MailpitContainer, String>> providers,
            MailpitConfig config) {
        providers.put(MailpitContainer.WEBROOT, configValue(MailpitContainer.WEBROOT));
        providers.put("MP_MAX_MESSAGES", configValue("MP_MAX_MESSAGES"));
        providers.put("MP_SMTP_RELAY_PORT", configValue("MP_SMTP_RELAY_PORT"));
        providers.put("MP_SMTP_RELAY_STARTTLS", configValue("MP_SMTP_RELAY_STARTTLS"));
        providers.put("MP_SMTP_RELAY_TLS", configValue("MP_SMTP_RELAY_TLS"));
        providers.put("MP_SMTP_RELAY_ALLOW_INSECURE", configValue("MP_SMTP_RELAY_ALLOW_INSECURE"));
        providers.put("MP_SMTP_RELAY_AUTH", configValue("MP_SMTP_RELAY_AUTH"));
        providers.put("MP_SMTP_RELAY_ALL", configValue("MP_SMTP_RELAY_ALL"));
        if (config.verbose()) {
            providers.put("MP_VERBOSE", configValue("MP_VERBOSE"));
        }
        if (config.enableChaos()) {
            providers.put("MP_ENABLE_CHAOS", configValue("MP_ENABLE_CHAOS"));
        }
        config.smtpRelayHost().ifPresent(ignored -> providers.put("MP_SMTP_RELAY_HOST", configValue("MP_SMTP_RELAY_HOST")));
        config.smtpRelayUsername()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_USERNAME", configValue("MP_SMTP_RELAY_USERNAME")));
        config.smtpRelayPassword()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_PASSWORD", configValue("MP_SMTP_RELAY_PASSWORD")));
        config.smtpRelaySecret()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_SECRET", configValue("MP_SMTP_RELAY_SECRET")));
        config.smtpRelayReturnPath()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_RETURN_PATH", configValue("MP_SMTP_RELAY_RETURN_PATH")));
        config.smtpRelayOverrideFrom()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_OVERRIDE_FROM", configValue("MP_SMTP_RELAY_OVERRIDE_FROM")));
        config.smtpRelayAllowedRecipients().ifPresent(
                ignored -> providers.put("MP_SMTP_RELAY_ALLOWED_RECIPIENTS", configValue("MP_SMTP_RELAY_ALLOWED_RECIPIENTS")));
        config.smtpRelayBlockedRecipients().ifPresent(
                ignored -> providers.put("MP_SMTP_RELAY_BLOCKED_RECIPIENTS", configValue("MP_SMTP_RELAY_BLOCKED_RECIPIENTS")));
        config.smtpRelayMatching()
                .ifPresent(ignored -> providers.put("MP_SMTP_RELAY_MATCHING", configValue("MP_SMTP_RELAY_MATCHING")));
    }

    private static Function<MailpitContainer, String> configValue(String key) {
        return container -> container.getExposedConfig().get(key);
    }

}
