package com.flowcrm.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisLoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginRateLimitProperties properties;
    private RedisLoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new LoginRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(3);
        properties.setWindowSeconds(60);
        properties.setFailOpen(true);
        limiter = new RedisLoginRateLimiter(redisTemplate, properties);
    }

    @Test
    void belowThreshold_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(-1L);

        assertThat(limiter.check("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void aboveThreshold_deniedWithRetryAfter() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(45_000L);

        RateLimitDecision decision = limiter.check("10.0.0.1");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(45);
    }

    @Test
    void independentClientKeys_useDistinctRedisKeys() {
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        eq(List.of(RedisLoginRateLimiter.KEY_PREFIX + "1.1.1.1")),
                        anyString(),
                        anyString()))
                .thenReturn(-1L);
        when(redisTemplate.execute(
                        any(RedisScript.class),
                        eq(List.of(RedisLoginRateLimiter.KEY_PREFIX + "2.2.2.2")),
                        anyString(),
                        anyString()))
                .thenReturn(10_000L);

        assertThat(limiter.check("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.check("2.2.2.2").allowed()).isFalse();
    }

    @Test
    void newWindowAllowsAgain_whenScriptReturnsAllow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(5_000L)
                .thenReturn(-1L);

        assertThat(limiter.check("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.check("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void redisError_failOpen_allows() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(limiter.check("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void redisError_failClosed_denies() {
        properties.setFailOpen(false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(limiter.check("10.0.0.1").allowed()).isFalse();
    }
}
