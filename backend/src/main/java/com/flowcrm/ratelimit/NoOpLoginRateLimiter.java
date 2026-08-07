package com.flowcrm.ratelimit;

/**
 * Allows all login attempts. Used when {@code app.rate-limit.login.enabled=false}.
 */
public class NoOpLoginRateLimiter implements LoginRateLimiter {

    @Override
    public RateLimitDecision check(String clientKey) {
        return RateLimitDecision.allow();
    }
}
