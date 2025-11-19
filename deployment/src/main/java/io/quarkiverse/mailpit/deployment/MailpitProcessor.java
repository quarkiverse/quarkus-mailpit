package io.quarkiverse.mailpit.deployment;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;

/**
 * Starts a Mailpit server as dev service if needed.
 */
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = DevServicesConfig.Enabled.class)
public class MailpitProcessor {

    private static final Logger log = Logger.getLogger(MailpitProcessor.class);

    public static final String FEATURE = "mailpit";

    private static final Predicate<Thread> IS_DOCKER_JAVA_STREAM_THREAD_PREDICATE = (thread) -> thread.getName()
            .startsWith("docker-java-stream");

    /**
     * Label to add to shared Dev Service for Mailpit running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-mailpit";

    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile MailpitConfig cfg;
    static volatile boolean first = true;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public DevServicesResultBuildItem startMailpitDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            MailpitConfig mailpitConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            DevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            BuildProducer<MailpitDevServicesConfigBuildItem> mailpitBuildItemBuildProducer,
            CombinedIndexBuildItem combinedIndexBuildItem,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {
        if (devService != null) {
            boolean shouldShutdownTheBroker = !MailpitConfig.isEqual(cfg, mailpitConfig);
            if (!shouldShutdownTheBroker) {
                if (devService.isOwner()) {
                    mailpitBuildItemBuildProducer.produce(new MailpitDevServicesConfigBuildItem(devService.getConfig()));
                }
                return devService.toBuildItem();
            }
            shutdown();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Mailpit Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem, IS_DOCKER_JAVA_STREAM_THREAD_PREDICATE);
        try {
            var path = nonApplicationRootPathBuildItem.resolvePath(MailpitProcessor.FEATURE);

            log.infof("Mailpit Dev Services Starting at: %s", path);

            boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                    devServicesSharedNetworkBuildItem);
            devService = startMailpit(dockerStatusBuildItem, mailpitConfig, devServicesConfig,
                    useSharedNetwork, combinedIndexBuildItem.getIndex(), path);
            if (devService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        if (devService.isOwner()) {
            log.info("Dev Services for Mailpit started.");
            mailpitBuildItemBuildProducer.produce(new MailpitDevServicesConfigBuildItem(devService.getConfig()));
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdown();

                    log.info("Dev Services for Mailpit shut down.");
                }
                first = true;
                devService = null;
                cfg = null;
            };
            QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread().getContextClassLoader();
            ((QuarkusClassLoader) cl.parent()).addCloseTask(closeTask);
        }
        cfg = mailpitConfig;
        return devService.toBuildItem();
    }

    private DevServicesResultBuildItem.RunningDevService startMailpit(DockerStatusBuildItem dockerStatusBuildItem,
            MailpitConfig mailpitConfig, DevServicesConfig devServicesConfig, boolean useSharedNetwork,
            IndexView index, String path) {
        if (!mailpitConfig.enabled()) {
            // explicitly disabled
            log.warn("Not starting dev services for Mailpit, as it has been disabled in the config.");
            return null;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker/Podman isn't working, not starting dev services for Mailpit.");
            return null;
        }

        final MailpitContainer mailpit = new MailpitContainer(mailpitConfig, useSharedNetwork, index, path);
        devServicesConfig.timeout().ifPresent(mailpit::withStartupTimeout);
        mailpit.start();

        return new DevServicesResultBuildItem.RunningDevService(FEATURE,
                mailpit.getContainerId(),
                mailpit::close,
                mailpit.getExposedConfig());
    }

    private void shutdown() {
        if (devService != null) {
            try {
                log.info("Dev Services for Mailpit shutting down...");
                devService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Mailpit server", e);
            } finally {
                devService = null;
            }
        }
    }

}