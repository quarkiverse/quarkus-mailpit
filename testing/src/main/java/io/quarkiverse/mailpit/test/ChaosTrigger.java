package io.quarkiverse.mailpit.test;

import java.util.Objects;

public class ChaosTrigger {
    private final int errorCode;
    private final int probability;

    public ChaosTrigger(int errorCode, int probability) {
        assertErrorCode(errorCode);
        assertProbability(probability);

        this.errorCode = errorCode;
        this.probability = probability;
    }

    private static void assertProbability(int probability) {
        if (probability < 0 || probability > 100) {
            throw new IllegalArgumentException("Probability must be between 0 and 100.");
        }
    }

    private static void assertErrorCode(int errorCode) {
        if (errorCode < 400 || errorCode > 599) {
            throw new IllegalArgumentException("ErrorCode must be a valid SMTP error code (400-599).");
        }
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getProbability() {
        return probability;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ChaosTrigger that = (ChaosTrigger) o;
        return errorCode == that.errorCode && probability == that.probability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, probability);
    }

    @Override
    public String toString() {
        return "ChaosTrigger{" +
                "errorCode=" + errorCode +
                ", probability=" + probability +
                '}';
    }
}
