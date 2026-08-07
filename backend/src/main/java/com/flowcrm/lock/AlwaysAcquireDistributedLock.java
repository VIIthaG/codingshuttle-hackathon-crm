package com.flowcrm.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Always acquires when Redis locking is disabled (single-instance / test profiles).
 * Registered via {@link DistributedLockConfig} when {@code app.locks.outbox.enabled=false}.
 */
public class AlwaysAcquireDistributedLock implements DistributedLock {

    @Override
    public Optional<String> tryAcquire(String key, Duration ttl) {
        return Optional.of(UUID.randomUUID().toString());
    }

    @Override
    public boolean release(String key, String token) {
        return true;
    }
}
