package io.quarkiverse.mailpit.test;

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

        public Builder authentication(int errorCode, int probability) {
            this.authentication = new ChaosTrigger(errorCode, probability);
            return this;
        }

        public Builder recipient(int errorCode, int probability) {
            this.recipient = new ChaosTrigger(errorCode, probability);
            return this;
        }

        public Builder sender(int errorCode, int probability) {
            this.sender = new ChaosTrigger(errorCode, probability);
            return this;
        }

        public ChaosConfig build() {
            int defaultErrorCode = 451;
            int defaultProbability = 0;

            if (authentication == null) {
                authentication = new ChaosTrigger(defaultErrorCode, defaultProbability);
            }

            if (recipient == null) {
                recipient = new ChaosTrigger(defaultErrorCode, defaultProbability);
            }

            if (sender == null) {
                sender = new ChaosTrigger(defaultErrorCode, defaultProbability);
            }

            return new ChaosConfig(this);
        }
    }

    public ChaosTriggers getChaosTriggers() {
        return new ChaosTriggers(authentication, recipient, sender);
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
