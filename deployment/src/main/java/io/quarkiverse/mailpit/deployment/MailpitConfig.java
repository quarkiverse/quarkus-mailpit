package io.quarkiverse.mailpit.deployment;

import java.util.Objects;
import java.util.Optional;
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
    String DEFAULT_IMAGE = "axllent/mailpit:v1.28.0";

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
     * Flag to control if chaos testing is requested.
     */
    @WithDefault("false")
    boolean enableChaos();

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

    /**
     * SMTP relay host to forward emails to.
     * Example: smtp.gmail.com
     */
    Optional<String> smtpRelayHost();

    /**
     * SMTP relay port to forward emails to.
     * Default: 25
     */
    @WithDefault("25")
    int smtpRelayPort();

    /**
     * Connect using STARTTLS.
     * Default: false
     */
    @WithDefault("false")
    boolean smtpRelayStarttls();

    /**
     * Connect using TLS.
     * Default: false
     */
    @WithDefault("false")
    boolean smtpRelayTls();

    /**
     * Do not validate TLS certificate.
     * Default: false
     */
    @WithDefault("false")
    boolean smtpRelayAllowInsecure();

    /**
     * SMTP relay authentication mechanism.
     * Options: none, plain, login, cram-md5
     * Default: none
     */
    @WithDefault("none")
    String smtpRelayAuth();

    /**
     * SMTP relay username for authentication.
     * Required for plain, login and cram-md5 auth.
     */
    Optional<String> smtpRelayUsername();

    /**
     * SMTP relay password for authentication.
     * Required for plain & login auth.
     */
    Optional<String> smtpRelayPassword();

    /**
     * SMTP relay secret for CRAM-MD5 authentication.
     * Required for cram-md5 auth.
     */
    Optional<String> smtpRelaySecret();

    /**
     * Overrides Return-Path for all released emails.
     */
    Optional<String> smtpRelayReturnPath();

    /**
     * Overrides the From email address.
     */
    Optional<String> smtpRelayOverrideFrom();

    /**
     * Regex to limit allowed relay addresses or domains via web UI & API.
     * Example: "@example\\.com$"
     */
    Optional<String> smtpRelayAllowedRecipients();

    /**
     * Regex to prevent relaying to addresses or domains via web UI & API.
     * Example: "@example2\\.com$"
     */
    Optional<String> smtpRelayBlockedRecipients();

    /**
     * Automatically relay all incoming messages via the configured SMTP relay server.
     * When enabled, Mailpit acts like a caching proxy server, automatically sending any incoming email
     * to all original recipients and storing a local copy. The incoming email is not modified.
     *
     * This option cannot be used in conjunction with smtp-relay-matching, and ignores the
     * allowed-recipients option in your SMTP relay configuration.
     *
     * Any addresses matching blocked-recipients (if set) are silently ignored, however other
     * remaining addresses (if any) will still be sent the message.
     *
     * Default: false
     */
    @WithDefault("false")
    boolean smtpRelayAll();

    /**
     * Selectively relay messages to a pre-configured regular expression.
     * Example: '(user1@host1\\.com|user2@host2\\.com|@host3\\.com)$'
     *
     * This option cannot be used in conjunction with smtp-relay-all, and ignores the
     * allowed-recipients option in your SMTP relay configuration.
     *
     * Any addresses matching blocked-recipients (if set) are silently ignored, however other
     * remaining addresses will still be sent the message provided they match the pattern.
     */
    Optional<String> smtpRelayMatching();

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
        if (!Objects.equals(d1.enableChaos(), d2.enableChaos())) {
            return false;
        }
        if (!Objects.equals(d1.maxMessages(), d2.maxMessages())) {
            return false;
        }
        if (!Objects.equals(d1.mappedHttpPort(), d2.mappedHttpPort())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayHost(), d2.smtpRelayHost())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayPort(), d2.smtpRelayPort())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayStarttls(), d2.smtpRelayStarttls())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayTls(), d2.smtpRelayTls())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayAllowInsecure(), d2.smtpRelayAllowInsecure())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayAuth(), d2.smtpRelayAuth())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayUsername(), d2.smtpRelayUsername())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayPassword(), d2.smtpRelayPassword())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelaySecret(), d2.smtpRelaySecret())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayReturnPath(), d2.smtpRelayReturnPath())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayOverrideFrom(), d2.smtpRelayOverrideFrom())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayAllowedRecipients(), d2.smtpRelayAllowedRecipients())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayBlockedRecipients(), d2.smtpRelayBlockedRecipients())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayAll(), d2.smtpRelayAll())) {
            return false;
        }
        if (!Objects.equals(d1.smtpRelayMatching(), d2.smtpRelayMatching())) {
            return false;
        }
        return true;
    }

}