package io.quarkiverse.mailpit.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 * Allows configuring the Mailpit mail server.
 *
 * Find more info about Mailpit on <a href="https://github.com/axllent/mailpit">https://github.com/axllent/mailpit</a>.
 */
@ConfigRoot(name = "mailpit", phase = ConfigPhase.BUILD_TIME)
public class MailpitConfig {

    /**
     * Default docker image name.
     */
    public static final String DEFAULT_IMAGE = "axllent/mailpit";

    /**
     * If Dev Services for Mailpit has been explicitly enabled or disabled. Dev Services are generally enabled
     * by default, unless there is an existing configuration present.
     */
    @ConfigItem
    public Optional<Boolean> enabled = Optional.empty();

    /**
     * The Mailpit container image to use.
     */
    @ConfigItem(defaultValue = DEFAULT_IMAGE)
    public String imageName;

    /**
     * Flag to control if verbose logging of Mailpit container is requested.
     */
    @ConfigItem(defaultValue = "true")
    public boolean verbose;

}