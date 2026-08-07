package com.flowcrm.idempotency;

/**
 * Stable operation identifiers used in idempotency scoping.
 */
public final class IdempotencyOperations {

    public static final String LEADS_CREATE = "LEADS_CREATE";
    public static final String TASKS_CREATE = "TASKS_CREATE";

    private IdempotencyOperations() {
    }
}
