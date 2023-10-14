package io.quarkiverse.mailpit.deployment;

import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

/**
 * Starts a Mailpit server as dev service if needed.
 */
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class MailpitProcessor {

    private static final Logger log = Logger.getLogger(MailpitProcessor.class);

    public static final String FEATURE = "mailpit";

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
            GlobalDevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            BuildProducer<MailpitDevServicesConfigBuildItem> mailpitBuildItemBuildProducer) {

        if (devService != null) {
            boolean shouldShutdownTheBroker = !mailpitConfig.equals(cfg);
            if (!shouldShutdownTheBroker) {
                return devService.toBuildItem();
            }
            shutdown();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Mailpit Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);
        try {
            devService = startMailpit(dockerStatusBuildItem, mailpitConfig, devServicesConfig,
                    !devServicesSharedNetworkBuildItem.isEmpty());
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
            MailpitConfig mailpitConfig, GlobalDevServicesConfig devServicesConfig, boolean useSharedNetwork) {
        if (mailpitConfig.enabled.isPresent() && mailpitConfig.enabled.get()) {
            // explicitly disabled
            log.warn("Not starting dev services for Mailpit, as it has been disabled in the config.");
            return null;
        }

        if (MailpitContainer.getMailPort() <= 0) {
            // no mailer configured
            log.warn("Not starting dev services for Mailpit, as no 'quarkus.mailer.port' has been configured.");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, not starting dev services for Mailpit.");
            return null;
        }

        final MailpitContainer mailpit = new MailpitContainer(mailpitConfig, useSharedNetwork);
        devServicesConfig.timeout.ifPresent(mailpit::withStartupTimeout);
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
