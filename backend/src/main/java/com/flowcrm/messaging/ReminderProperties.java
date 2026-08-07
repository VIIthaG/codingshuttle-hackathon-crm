package com.flowcrm.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reminders")
public class ReminderProperties {

    /**
     * When true, delivery throws so retry/DLQ can be demonstrated (dev/test only).
     */
    private boolean failDelivery;

    private int maxAttempts = 3;

    private long retryDelayMs = 5000L;

    public boolean isFailDelivery() {
        return failDelivery;
    }

    public void setFailDelivery(boolean failDelivery) {
        this.failDelivery = failDelivery;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
}
