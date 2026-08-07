package com.flowcrm.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.flowcrm.lock.DistributedLock;
import com.flowcrm.lock.RedisDistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.rate-limit.login.enabled=true",
            "app.locks.outbox.enabled=true"
        })
@Import(LoginRateLimitEnabledWiringTest.RedisStubConfig.class)
class LoginRateLimitEnabledWiringTest {

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private LoginRateLimitFilter loginRateLimitFilter;

    @Autowired
    private DistributedLock distributedLock;

    @Test
    void redisLimiterSelectedWhenEnabled() {
        assertThat(loginRateLimiter).isInstanceOf(RedisLoginRateLimiter.class);
        assertThat(loginRateLimitFilter).isNotNull();
        assertThat(distributedLock).isInstanceOf(RedisDistributedLock.class);
    }

    @TestConfiguration
    static class RedisStubConfig {
        @Bean
        @Primary
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
