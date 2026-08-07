package com.flowcrm.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis distributed lock: {@code SET key token NX PX ttl} + Lua compare-and-delete release.
 *
 * <p>On Redis errors during acquire, returns empty (fail-closed for scheduled outbox work)
 * so multiple instances do not all run as if they held the lock.
 *
 * <p>Registered via {@link DistributedLockConfig}.
 */
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                  return redis.call('DEL', KEYS[1])
                end
                return 0
                """);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Failed to acquire distributed lock key={}; skipping protected work", key, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean release(String key, String token) {
        try {
            Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            return result != null && result > 0;
        } catch (RuntimeException ex) {
            log.warn("Failed to release distributed lock key={}", key, ex);
            return false;
        }
    }

    DefaultRedisScript<Long> releaseScript() {
        return RELEASE_SCRIPT;
    }
}
