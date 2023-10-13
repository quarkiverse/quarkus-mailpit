package io.quarkiverse.mailpit.deployment;

import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item used to carry running values to Dev UI.
 */
public final class MailpitDevServicesConfigBuildItem extends SimpleBuildItem {

    private final Map<String, String> config;

    public MailpitDevServicesConfigBuildItem(Map<String, String> config) {
        this.config = config;
    }

    public Map<String, String> getConfig() {
        return config;
    }

}