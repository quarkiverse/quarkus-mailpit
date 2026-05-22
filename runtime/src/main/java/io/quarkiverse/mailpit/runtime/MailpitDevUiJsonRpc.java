package io.quarkiverse.mailpit.runtime;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class MailpitDevUiJsonRpc {

    public String getMailpitSmtpPort() {
        return ConfigProvider.getConfig()
                .getOptionalValue("mailpit.smtp.port", String.class)
                .orElse("n/a");
    }
}
