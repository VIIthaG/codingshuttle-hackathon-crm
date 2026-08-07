package com.flowcrm.ratelimit;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Fixed-window login rate limiter using an atomic Redis Lua script (INCR + PEXPIRE).
 *
 * <p>Key format: {@code ratelimit:login:&lt;clientKey&gt;}
 *
 * <p>Fail-open: when Redis throws and {@code app.rate-limit.login.fail-open=true},
 * log a warning and allow the request so auth remains available.
 *
 * <p>Registered via {@link LoginRateLimitConfig} — not component-scanned with
 * {@code @ConditionalOnBean}, which would miss auto-configured Redis beans.
 */
public class RedisLoginRateLimiter implements LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginRateLimiter.class);

    static final String KEY_PREFIX = "ratelimit:login:";

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setResultType(Long.class);
        SCRIPT.setScriptText("""
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                  redis.call('PEXPIRE', KEYS[1], ARGV[1])
                end
                local ttl = redis.call('PTTL', KEYS[1])
                if current > tonumber(ARGV[2]) then
                  return ttl
                end
                return -1
                """);
    }

    private final StringRedisTemplate redisTemplate;
    private final LoginRateLimitProperties properties;

    public RedisLoginRateLimiter(StringRedisTemplate redisTemplate, LoginRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision check(String clientKey) {
        String redisKey = KEY_PREFIX + clientKey;
        long windowMs = properties.getWindowSeconds() * 1000L;
        try {
            Long result = redisTemplate.execute(
                    SCRIPT,
                    List.of(redisKey),
                    String.valueOf(windowMs),
                    String.valueOf(properties.getMaxRequests()));
            if (result == null || result < 0) {
                return RateLimitDecision.allow();
            }
            long retryAfterSeconds = Math.max(1, (result + 999) / 1000);
            return RateLimitDecision.deny(retryAfterSeconds);
        } catch (RuntimeException ex) {
            if (properties.isFailOpen()) {
                log.warn("Login rate-limit Redis error for key={}; failing open", redisKey, ex);
                return RateLimitDecision.allow();
            }
            log.error("Login rate-limit Redis error for key={}; failing closed", redisKey, ex);
            return RateLimitDecision.deny(properties.getWindowSeconds());
        }
    }
}
