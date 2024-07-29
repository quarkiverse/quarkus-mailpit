package io.quarkiverse.mailpit.deployment;

import java.util.Objects;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Allows configuring the Mailpit mail server.
 * <p>
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

    /**
     * Although mailpit can easily handling tens of thousands of emails, it will automatically prune old messages by default
     * keeping the most recent 500 emails. Default is 500, or set to 0 to disable entirely.
     */
    @WithDefault("500")
    int maxMessages();

    /**
     * Statically define the mapped HTTP port that the container user interface exposes
     */
    OptionalInt mappedHttpPort();

    static boolean isEqual(MailpitConfig d1, MailpitConfig d2) {
        if (!Objects.equals(d1.enabled(), d2.enabled())) {
            return false;
        }
        if (!Objects.equals(d1.imageName(), d2.imageName())) {
            return false;
        }
        if (!Objects.equals(d1.verbose(), d2.verbose())) {
            return false;
        }
        if (!Objects.equals(d1.maxMessages(), d2.maxMessages())) {
            return false;
        }
        if (!Objects.equals(d1.mappedHttpPort(), d2.mappedHttpPort())) {
            return false;
        }
        return true;
    }

}
