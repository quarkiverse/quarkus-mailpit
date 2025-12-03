package io.quarkiverse.mailpit.test;

import io.quarkiverse.mailpit.test.model.ChaosTrigger;
import io.quarkiverse.mailpit.test.model.ChaosTriggers;

public class ChaosConfig {
    private final ChaosTrigger authentication;
    private final ChaosTrigger recipient;
    private final ChaosTrigger sender;

    private ChaosConfig(Builder builder) {
        this.authentication = builder.authentication;
        this.recipient = builder.recipient;
        this.sender = builder.sender;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChaosTrigger authentication;
        private ChaosTrigger recipient;
        private ChaosTrigger sender;

        public Builder authentication(Long errorCode, Long probability) {
            this.authentication = new ChaosTrigger();
            this.authentication.probability(probability);
            this.authentication.errorCode(errorCode);
            return this;
        }

        public Builder recipient(Long errorCode, Long probability) {
            this.recipient = new ChaosTrigger();
            this.recipient.probability(probability);
            this.recipient.errorCode(errorCode);
            return this;
        }

        public Builder sender(Long errorCode, Long probability) {
            this.sender = new ChaosTrigger();
            this.sender.probability(probability);
            this.sender.errorCode(errorCode);
            return this;
        }

        public ChaosConfig build() {
            Long defaultErrorCode = 451L;
            Long defaultProbability = 0L;

            if (authentication == null) {
                authentication = new ChaosTrigger();
                authentication.probability(defaultProbability);
                authentication.errorCode(defaultErrorCode);
            }

            if (recipient == null) {
                recipient = new ChaosTrigger();
                recipient.probability(defaultProbability);
                recipient.errorCode(defaultErrorCode);
            }

            if (sender == null) {
                sender = new ChaosTrigger();
                sender.probability(defaultProbability);
                sender.errorCode(defaultErrorCode);
            }

            return new ChaosConfig(this);
        }
    }

    public ChaosTriggers getChaosTriggers() {
        ChaosTriggers triggers = new ChaosTriggers();
        triggers.authentication(authentication);
        triggers.recipient(recipient);
        triggers.sender(sender);
        return triggers;
    }

    @Override
    public String toString() {
        return "ChaosConfig{" +
                "authentication=" + authentication +
                ", recipient=" + recipient +
                ", sender=" + sender +
                '}';
    }
}
