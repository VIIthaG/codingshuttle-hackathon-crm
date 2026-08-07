package com.flowcrm.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Explicit wiring for login rate limiting.
 *
 * <p>Do not use {@code @ConditionalOnBean(StringRedisTemplate)} on {@code @Component}
 * classes — that condition is evaluated during component scanning before Redis
 * auto-configuration creates {@link StringRedisTemplate}, so the Redis limiter
 * would never register and startup would fail.
 */
@Configuration
public class LoginRateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.login.enabled", havingValue = "true", matchIfMissing = true)
    LoginRateLimiter redisLoginRateLimiter(
            StringRedisTemplate stringRedisTemplate, LoginRateLimitProperties properties) {
        return new RedisLoginRateLimiter(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.login.enabled", havingValue = "false")
    LoginRateLimiter noOpLoginRateLimiter() {
        return new NoOpLoginRateLimiter();
    }

    @Bean
    LoginRateLimitFilter loginRateLimitFilter(LoginRateLimiter loginRateLimiter, ObjectMapper objectMapper) {
        return new LoginRateLimitFilter(loginRateLimiter, objectMapper);
    }
}
