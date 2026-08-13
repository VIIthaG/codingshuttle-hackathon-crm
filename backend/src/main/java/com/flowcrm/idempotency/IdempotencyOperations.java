package com.flowcrm.idempotency;

/**
 * Stable operation identifiers used in idempotency scoping.
 */
public final class IdempotencyOperations {

    public static final String LEADS_CREATE = "LEADS_CREATE";
    public static final String TASKS_CREATE = "TASKS_CREATE";
    public static final String ACCOUNTS_CREATE = "ACCOUNTS_CREATE";
    public static final String CONTACTS_CREATE = "CONTACTS_CREATE";
    public static final String DEALS_CREATE = "DEALS_CREATE";

    private IdempotencyOperations() {
    }
}
