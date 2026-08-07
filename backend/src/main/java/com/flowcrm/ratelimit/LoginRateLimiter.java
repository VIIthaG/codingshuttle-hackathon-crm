package com.flowcrm.ratelimit;

/**
 * Atomic login rate limiter. Implementations must be safe across app instances.
 */
public interface LoginRateLimiter {

    RateLimitDecision check(String clientKey);
}
