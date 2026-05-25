package io.quarkiverse.mailpit.deployment.devui;

import io.quarkiverse.mailpit.deployment.MailpitConfig;
import io.quarkiverse.mailpit.deployment.MailpitProcessor;
import io.quarkiverse.mailpit.runtime.MailpitDevUiJsonRpc;
import io.quarkiverse.mailpit.runtime.MailpitUiProxy;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.*;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

/**
 * Dev UI card for displaying important details such Mailpit embedded UI.
 */
public class MailpitDevUIProcessor {

    @BuildStep
    JsonRPCProvidersBuildItem registerJsonRpc() {
        return new JsonRPCProvidersBuildItem(MailpitDevUiJsonRpc.class);
    }

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
            BuildProducer<FooterPageBuildItem> footerProducer,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchModeBuildItem,
            MailpitConfig config) {
        if (!config.enabled()) {
            return;
        }

        final CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.externalPageBuilder("SMTP Port")
                .icon("font-awesome-solid:envelope")
                .doNotEmbed()
                .url("https://github.com/axllent/mailpit")
                .dynamicLabelJsonRPCMethodName("getMailpitSmtpPort"));

        var externalPath = nonApplicationRootPathBuildItem.resolveManagementPath(
                MailpitProcessor.FEATURE,
                managementInterfaceBuildTimeConfig,
                launchModeBuildItem);

        card.addPage(Page.externalPageBuilder("Mailpit UI")
                .url(externalPath, externalPath)
                .isHtmlContent()
                .icon("font-awesome-solid:envelopes-bulk"));

        cardPageBuildItemBuildProducer.produce(card);

        WebComponentPageBuilder mailLogPageBuilder = Page.webComponentPageBuilder()
                .icon("font-awesome-solid:envelope")
                .title("Mailer")
                .componentLink("qwc-mailpit-log.js");

        footerProducer.produce(new FooterPageBuildItem(mailLogPageBuilder));
    }
}