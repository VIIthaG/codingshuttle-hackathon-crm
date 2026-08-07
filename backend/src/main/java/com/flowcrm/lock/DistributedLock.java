package com.flowcrm.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Distributed lock with ownership-safe release.
 */
public interface DistributedLock {

    /**
     * @return ownership token if acquired; empty if not acquired or Redis unavailable
     */
    Optional<String> tryAcquire(String key, Duration ttl);

    /**
     * Releases only if the token still owns the lock (compare-and-delete).
     *
     * @return true if this token deleted the lock
     */
    boolean release(String key, String token);
}
