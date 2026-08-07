package com.flowcrm.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC claim helpers. Separate bean so {@code REQUIRES_NEW} proxying works.
 */
@Repository
public class IdempotencyClaimStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyClaimStore.class);

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyClaimStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically claims a key. Commits immediately so concurrent requests can observe STARTED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(UUID id, UUID userId, String operation, String idempotencyKey, String requestHash) {
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO idempotency_records
                            (id, user_id, operation, idempotency_key, request_hash, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                id,
                userId,
                operation,
                idempotencyKey,
                requestHash,
                IdempotencyRecordStatus.STARTED.name(),
                Timestamp.from(Instant.now()));
        return inserted == 1;
    }

    /**
     * Completes a claim inside the caller's transaction (with the business write).
     */
    public void markCompleted(
            UUID userId, String operation, String idempotencyKey, int responseStatus, String responseBody) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE idempotency_records
                        SET status = ?, response_status = ?, response_body = ?, completed_at = ?
                        WHERE user_id = ? AND operation = ? AND idempotency_key = ? AND status = ?
                        """,
                IdempotencyRecordStatus.COMPLETED.name(),
                responseStatus,
                responseBody,
                Timestamp.from(Instant.now()),
                userId,
                operation,
                idempotencyKey,
                IdempotencyRecordStatus.STARTED.name());
        if (updated != 1) {
            log.warn(
                    "Failed to complete idempotency claim userId={} operation={} key={}",
                    userId,
                    operation,
                    idempotencyKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(UUID userId, String operation, String idempotencyKey) {
        jdbcTemplate.update(
                """
                        DELETE FROM idempotency_records
                        WHERE user_id = ? AND operation = ? AND idempotency_key = ? AND status = ?
                        """,
                userId,
                operation,
                idempotencyKey,
                IdempotencyRecordStatus.STARTED.name());
    }
}
