package io.quarkiverse.mailpit.deployment;

import static io.quarkus.devservices.common.ContainerLocator.locateContainerWithLabels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.deployment.Feature;
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
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;

/**
 * Starts a Mailpit server as dev service if needed.
 */
@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
public class MailpitProcessor {

    private static final Logger log = Logger.getLogger(MailpitProcessor.class);

    public static final String FEATURE = "mailpit";

    /**
     * Label to add to shared Dev Service for Mailpit running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-mailpit";

    private static final ContainerLocator mailpitContainerLocator = locateContainerWithLabels(MailpitContainer.PORT_SMTP,
            DEV_SERVICE_LABEL);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public DevServicesResultBuildItem startMailpitDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            MailpitConfig mailpitConfig,
            DevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            CombinedIndexBuildItem combinedIndexBuildItem,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            DevServicesComposeProjectBuildItem composeProjectBuildItem) {
        if (devServiceDisabled(dockerStatusBuildItem, mailpitConfig)) {
            return null;
        }

        boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                devServicesSharedNetworkBuildItem);
        IndexView index = combinedIndexBuildItem.getIndex();
        String path = nonApplicationRootPathBuildItem.resolvePath(FEATURE);

        DevServicesResultBuildItem discovered = discoverRunningService(composeProjectBuildItem, mailpitConfig,
                launchMode.getLaunchMode(), useSharedNetwork, index, path);
        if (discovered != null) {
            return discovered;
        }

        return DevServicesResultBuildItem.owned()
                .feature(Feature.MAILER)
                .serviceConfig(mailpitConfig)
                .startable(() -> createContainer(composeProjectBuildItem, mailpitConfig, devServicesConfig,
                        useSharedNetwork, launchMode.getLaunchMode(), index, path))
                .configProvider(toStartableConfigProvider(index))
                .postStartHook(container -> log.infof(
                        "Dev Services for Mailpit started. Mailpit UI is available at %s",
                        container.getConnectionInfo()))
                .build();
    }

    private Startable createContainer(DevServicesComposeProjectBuildItem composeProjectBuildItem,
            MailpitConfig mailpitConfig, DevServicesConfig devServicesConfig, boolean useSharedNetwork,
            LaunchMode launchMode, IndexView index, String path) {
        MailpitContainer container = new MailpitContainer(mailpitConfig, composeProjectBuildItem.getDefaultNetworkId(),
                useSharedNetwork, index, path)
                .withSharedServiceLabel(launchMode, mailpitConfig.serviceName());
        devServicesConfig.timeout().ifPresent(container::withStartupTimeout);
        return container;
    }

    private DevServicesResultBuildItem discoverRunningService(DevServicesComposeProjectBuildItem composeProjectBuildItem,
            MailpitConfig mailpitConfig, LaunchMode launchMode, boolean useSharedNetwork, IndexView index, String path) {
        Map<Integer, Integer> publicPorts = new HashMap<>();
        AtomicReference<String> containerId = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();

        Optional<DevServicesResultBuildItem> fromLocator = mailpitContainerLocator
                .locateContainer(mailpitConfig.serviceName(), mailpitConfig.shared(), launchMode,
                        (privatePort, address) -> {
                            publicPorts.put(privatePort, address.getPort());
                            containerId.set(address.getId());
                            host.set(address.getHost());
                        })
                .flatMap(
                        id -> buildDiscoveredResult(publicPorts, containerId.get(), host.get(), useSharedNetwork, index, path));

        if (fromLocator.isPresent()) {
            return fromLocator.get();
        }

        return ComposeLocator.locateContainer(composeProjectBuildItem,
                List.of(mailpitConfig.imageName(), "mailpit", "axllent/mailpit"),
                MailpitContainer.PORT_SMTP, launchMode, useSharedNetwork)
                .flatMap(address -> {
                    publicPorts.put(MailpitContainer.PORT_SMTP, address.getPort());
                    host.set(address.getHost());
                    containerId.set(address.getId());
                    return mailpitContainerLocator.locateContainer(mailpitConfig.serviceName(), mailpitConfig.shared(),
                            launchMode,
                            (privatePort, addr) -> publicPorts.put(privatePort, addr.getPort()))
                            .flatMap(ignored -> buildDiscoveredResult(publicPorts, containerId.get(), host.get(),
                                    useSharedNetwork, index, path));
                })
                .orElse(null);
    }

    private Optional<DevServicesResultBuildItem> buildDiscoveredResult(Map<Integer, Integer> publicPorts, String containerId,
            String host, boolean useSharedNetwork, IndexView index, String path) {
        Integer smtpPort = publicPorts.get(MailpitContainer.PORT_SMTP);
        Integer httpPort = publicPorts.get(MailpitContainer.PORT_HTTP);
        if (smtpPort == null || httpPort == null || host == null || containerId == null) {
            return Optional.empty();
        }
        return Optional.of(DevServicesResultBuildItem.discovered()
                .feature(Feature.MAILER)
                .containerId(containerId)
                .config(MailpitContainer.discoveredConfig(host, smtpPort, httpPort, path, index, useSharedNetwork))
                .build());
    }

    private static Map<String, Function<Startable, String>> toStartableConfigProvider(IndexView index) {
        Map<String, Function<Startable, String>> providers = new LinkedHashMap<>();
        MailpitContainer.applicationConfigProvider(index)
                .forEach((key, value) -> providers.put(key, startable -> value.apply((MailpitContainer) startable)));
        return providers;
    }

    private boolean devServiceDisabled(DockerStatusBuildItem dockerStatusBuildItem, MailpitConfig mailpitConfig) {
        if (!mailpitConfig.enabled()) {
            log.debug("Not starting dev services for Mailpit, as it has been disabled in the config.");
            return true;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker/Podman isn't working, not starting dev services for Mailpit.");
            return true;
        }
        return false;
    }
}
