package io.quarkiverse.mailpit.deployment.devui;

import java.util.Map;
import java.util.Optional;

import io.quarkiverse.mailpit.deployment.MailpitContainer;
import io.quarkiverse.mailpit.deployment.MailpitDevServicesConfigBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.FooterPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.PageBuilder;
import io.quarkus.devui.spi.page.WebComponentPageBuilder;

/**
 * Dev UI card for displaying important details such Mailpit embedded UI.
 */
public class MailpitDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void createVersion(BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer,
            Optional<MailpitDevServicesConfigBuildItem> configProps,
            BuildProducer<FooterPageBuildItem> footerProducer) {
        if (configProps.isPresent()) {
            Map<String, String> config = configProps.get().getConfig();
            final CardPageBuildItem card = new CardPageBuildItem();

            // SMTP
            if (config.containsKey(MailpitContainer.CONFIG_SMTP_PORT)) {
                final PageBuilder versionPage = Page.externalPageBuilder("SMTP Port")
                        .icon("font-awesome-solid:envelope")
                        .url("https://github.com/axllent/mailpit")
                        .doNotEmbed()
                        .staticLabel(config.getOrDefault(MailpitContainer.CONFIG_SMTP_PORT, "0"));
                card.addPage(versionPage);
            }

            // UI
            if (config.containsKey(MailpitContainer.CONFIG_HTTP_SERVER)) {
                String uiPath = config.get(MailpitContainer.CONFIG_HTTP_SERVER);
                card.addPage(Page.externalPageBuilder("Mailpit UI")
                        .url(uiPath, uiPath)
                        .isHtmlContent()
                        .icon("font-awesome-solid:envelopes-bulk"));
            }

            card.setCustomCard("qwc-mailpit-card.js");
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