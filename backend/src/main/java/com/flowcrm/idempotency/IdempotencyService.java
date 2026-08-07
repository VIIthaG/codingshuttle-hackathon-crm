package com.flowcrm.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.common.exception.ConflictException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable, multi-instance request idempotency for selected create APIs.
 *
 * <p>Claim ownership with {@code INSERT ... ON CONFLICT DO NOTHING} so the database unique
 * constraint is authoritative. Business work runs only for the claim owner. Failures release
 * the STARTED claim so the client can retry with the same key.
 */
@Service
public class IdempotencyService {

    private static final int AWAIT_ATTEMPTS = 40;
    private static final long AWAIT_SLEEP_MS = 50L;

    private final IdempotencyClaimStore claimStore;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final RequestFingerprint requestFingerprint;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public IdempotencyService(
            IdempotencyClaimStore claimStore,
            IdempotencyRecordRepository idempotencyRecordRepository,
            RequestFingerprint requestFingerprint,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            EntityManager entityManager) {
        this.claimStore = claimStore;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.requestFingerprint = requestFingerprint;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    /**
     * Executes {@code action} idempotently when {@code idempotencyKey} is present.
     * When the key is {@code null}, delegates to {@code action} with no side effects.
     */
    public <T> T execute(
            UUID userId,
            String operation,
            String idempotencyKey,
            Object requestPayload,
            Class<T> responseType,
            int successHttpStatus,
            Supplier<T> action) {
        if (idempotencyKey == null) {
            return action.get();
        }

        String requestHash = requestFingerprint.sha256Hex(requestPayload);

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                        userId, operation, idempotencyKey);
        if (existing.isPresent()) {
            return handleExisting(existing.get(), requestHash, responseType);
        }

        UUID claimId = UUID.randomUUID();
        boolean claimed = claimStore.tryClaim(claimId, userId, operation, idempotencyKey, requestHash);
        if (!claimed) {
            entityManager.clear();
            IdempotencyRecord raced = idempotencyRecordRepository
                    .findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency claim race lost but row missing"));
            return handleExisting(raced, requestHash, responseType);
        }

        try {
            return transactionTemplate.execute(status -> {
                T result = action.get();
                String body;
                try {
                    body = objectMapper.writeValueAsString(result);
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Failed to serialize idempotent response", ex);
                }
                claimStore.markCompleted(userId, operation, idempotencyKey, successHttpStatus, body);
                return result;
            });
        } catch (RuntimeException ex) {
            claimStore.releaseClaim(userId, operation, idempotencyKey);
            throw ex;
        }
    }

    private <T> T handleExisting(IdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key already used with a different request");
        }
        if (record.getStatus() == IdempotencyRecordStatus.COMPLETED) {
            return readResponse(record, responseType);
        }
        return awaitCompletion(record.getUserId(), record.getOperation(), record.getIdempotencyKey(), responseType);
    }

    private <T> T awaitCompletion(UUID userId, String operation, String key, Class<T> responseType) {
        for (int attempt = 0; attempt < AWAIT_ATTEMPTS; attempt++) {
            entityManager.clear();
            Optional<IdempotencyRecord> current =
                    idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, key);
            if (current.isEmpty()) {
                throw new ConflictException("Idempotency key is no longer available; retry the request");
            }
            IdempotencyRecord record = current.get();
            if (record.getStatus() == IdempotencyRecordStatus.COMPLETED) {
                return readResponse(record, responseType);
            }
            try {
                Thread.sleep(AWAIT_SLEEP_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for idempotent request", ie);
            }
        }
        throw new ConflictException("Idempotent request is still in progress; retry shortly");
    }

    private <T> T readResponse(IdempotencyRecord record, Class<T> responseType) {
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize stored idempotent response", ex);
        }
    }
}
