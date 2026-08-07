package com.flowcrm.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    /** Replaced by a newer schedule or cancelled because the task is no longer eligible. */
    SUPERSEDED
}
