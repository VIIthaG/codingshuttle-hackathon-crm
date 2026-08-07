package com.flowcrm.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowcrm.lock.AlwaysAcquireDistributedLock;
import com.flowcrm.lock.DistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LoginRateLimitDisabledWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private LoginRateLimitFilter loginRateLimitFilter;

    @Autowired
    private DistributedLock distributedLock;

    @Test
    void contextStartsWithNoOpLimiterWhenDisabled() {
        assertThat(applicationContext.getBean(LoginRateLimiter.class)).isInstanceOf(NoOpLoginRateLimiter.class);
        assertThat(loginRateLimiter).isInstanceOf(NoOpLoginRateLimiter.class);
        assertThat(loginRateLimitFilter).isNotNull();
        assertThat(loginRateLimiter.check("127.0.0.1").allowed()).isTrue();
        assertThat(distributedLock).isInstanceOf(AlwaysAcquireDistributedLock.class);
    }
}
