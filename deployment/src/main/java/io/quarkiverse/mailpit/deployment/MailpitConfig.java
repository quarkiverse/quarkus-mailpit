package io.quarkiverse.mailpit.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Allows configuring the Mailpit mail server.
 *
 * Find more info about Mailpit on <a href="https://github.com/axllent/mailpit">https://github.com/axllent/mailpit</a>.
 */
@ConfigMapping(prefix = "quarkus.mailpit")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MailpitConfig {

    /**
     * Default docker image name.
     */
    String DEFAULT_IMAGE = "axllent/mailpit";

    /**
     * If Dev Services for Mailpit has been explicitly enabled or disabled. Dev Services are generally enabled
     * by default, unless there is an existing configuration present.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The Mailpit container image to use.
     */
    @WithDefault(DEFAULT_IMAGE)
    String imageName();

    /**
     * Flag to control if verbose logging of Mailpit container is requested.
     */
    @WithDefault("true")
    boolean verbose();

}
