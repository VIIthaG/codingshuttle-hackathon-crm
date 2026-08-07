package com.flowcrm.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Explicit wiring for the outbox distributed lock.
 *
 * <p>Same rationale as login rate limiting: {@code @ConditionalOnBean(StringRedisTemplate)}
 * on a scanned {@code @Component} evaluates before Redis auto-configuration and would
 * leave production on the always-acquire fallback.
 */
@Configuration
public class DistributedLockConfig {

    @Bean
    @ConditionalOnProperty(name = "app.locks.outbox.enabled", havingValue = "true", matchIfMissing = true)
    DistributedLock redisDistributedLock(StringRedisTemplate stringRedisTemplate) {
        return new RedisDistributedLock(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "app.locks.outbox.enabled", havingValue = "false")
    DistributedLock alwaysAcquireDistributedLock() {
        return new AlwaysAcquireDistributedLock();
    }
}
