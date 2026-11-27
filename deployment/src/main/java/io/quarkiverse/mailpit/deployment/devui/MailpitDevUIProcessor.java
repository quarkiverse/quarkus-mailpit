package io.quarkiverse.mailpit.deployment.devui;

import io.quarkiverse.mailpit.deployment.MailpitConfig;
import io.quarkiverse.mailpit.deployment.MailpitProcessor;
import io.quarkiverse.mailpit.runtime.MailpitDevUiJsonRpc;
import io.quarkiverse.mailpit.runtime.MailpitUiProxy;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.devjsonrpc.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.*;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.RouteHandler;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;
import io.vertx.core.Vertx;

/**
 * Dev UI card for displaying important details such Mailpit embedded UI.
 */
public class MailpitDevUIProcessor {

    private static final String UI_PROXY_SERVICE = "io.quarkiverse.mailpit.ui-proxy";

    @BuildStep
    JsonRPCProvidersBuildItem registerJsonRpc() {
        return new JsonRPCProvidersBuildItem(MailpitDevUiJsonRpc.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @SuppressWarnings("deprecation") // RouteBuildItem only accepts a recorder proxy until the router itself is a service
    void registerProxy(
            ServiceRegistrar reg,
            BuildProducer<RouteBuildItem> routes,
            MailpitConfig config,
            NonApplicationRootPathBuildItem frameworkRoot) {
        if (!config.enabled()) {
            return;
        }

        reg.forService(RouteHandler.class, UI_PROXY_SERVICE)
                .require(Vertx.class)
                .onStart((ctx, vertx) -> MailpitUiProxy.handler(vertx));

        routes.produce(frameworkRoot.routeBuilder()
                .management()
                .route(MailpitProcessor.FEATURE + "/*")
                .displayOnNotFoundPage("Mailpit UI")
                .handler(reg.getRecorderProxy(RouteHandler.class, UI_PROXY_SERVICE))
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