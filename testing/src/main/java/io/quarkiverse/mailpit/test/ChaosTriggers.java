package io.quarkiverse.mailpit.test;

public class ChaosTriggers {
    private final ChaosTrigger authentication;
    private final ChaosTrigger recipient;
    private final ChaosTrigger sender;

    public ChaosTriggers(ChaosTrigger authentication, ChaosTrigger recipient, ChaosTrigger sender) {
        this.authentication = authentication;
        this.recipient = recipient;
        this.sender = sender;
    }

    public ChaosTrigger getAuthentication() {
        return authentication;
    }

    public ChaosTrigger getRecipient() {
        return recipient;
    }

    public ChaosTrigger getSender() {
        return sender;
    }

    @Override
    public String toString() {
        return "ChaosTriggers{" +
                "authentication=" + authentication +
                ", recipient=" + recipient +
                ", sender=" + sender +
                '}';
    }
}
