package io.quarkiverse.mailpit.deployment.devui;

import java.util.Map;
import java.util.Optional;

import io.quarkiverse.mailpit.deployment.MailpitConfig;
import io.quarkiverse.mailpit.deployment.MailpitContainer;
import io.quarkiverse.mailpit.deployment.MailpitDevServicesConfigBuildItem;
import io.quarkiverse.mailpit.deployment.MailpitProcessor;
import io.quarkiverse.mailpit.runtime.MailpitUiProxy;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.devui.spi.page.*;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

/**
 * Dev UI card for displaying important details such Mailpit embedded UI.
 */
public class MailpitDevUIProcessor {

    // proxy to Mailpit UI
    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerProxy(
            MailpitUiProxy recorder,
            BuildProducer<RouteBuildItem> routes,
            MailpitConfig config,
            NonApplicationRootPathBuildItem frameworkRoot,
            CoreVertxBuildItem coreVertxBuildItem) {
        if (!config.enabled()) {
            return;
        }

        routes.produce(frameworkRoot.routeBuilder()
                .management()
                .route(MailpitProcessor.FEATURE + "/*")
                .displayOnNotFoundPage("Mailpit UI")
                .handler(recorder.handler(coreVertxBuildItem.getVertx()))
                .build());
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void createVersion(BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer,
            Optional<MailpitDevServicesConfigBuildItem> configProps,
            BuildProducer<FooterPageBuildItem> footerProducer,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchModeBuildItem) {
        if (configProps.isPresent()) {
            Map<String, String> config = configProps.get().getConfig();
            final CardPageBuildItem card = new CardPageBuildItem();

            // SMTP
            if (config.containsKey(MailpitContainer.CONFIG_SMTP_PORT)) {
                final PageBuilder<ExternalPageBuilder> portPage = Page.externalPageBuilder("SMTP Port")
                        .icon("font-awesome-solid:envelope")
                        .doNotEmbed()
                        .url("https://github.com/axllent/mailpit")
                        .staticLabel(config.getOrDefault(MailpitContainer.CONFIG_SMTP_PORT, "0"));
                card.addPage(portPage);
            }

            // UI
            if (config.containsKey(MailpitContainer.CONFIG_HTTP_SERVER)) {
                var externalPath = nonApplicationRootPathBuildItem.resolveManagementPath(
                        MailpitProcessor.FEATURE,
                        managementInterfaceBuildTimeConfig,
                        launchModeBuildItem);

                card.addPage(Page.externalPageBuilder("Mailpit UI")
                        .url(externalPath, externalPath)
                        .isHtmlContent()
                        .icon("font-awesome-solid:envelopes-bulk"));
            }

            cardPageBuildItemBuildProducer.produce(card);

            // Mailpit Container Log Console
            WebComponentPageBuilder mailLogPageBuilder = Page.webComponentPageBuilder()
                    .icon("font-awesome-solid:envelope")
                    .title("Mailer")
                    .componentLink("qwc-mailpit-log.js");

            footerProducer.produce(new FooterPageBuildItem(mailLogPageBuilder));
        }
    }
}