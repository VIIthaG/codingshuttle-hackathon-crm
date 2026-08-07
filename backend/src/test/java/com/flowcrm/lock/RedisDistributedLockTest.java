package com.flowcrm.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    private static final String KEY = "lock:flowcrm:outbox-publisher";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLock lock;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lock = new RedisDistributedLock(redisTemplate);
    }

    @Test
    void firstContenderAcquiresLock() {
        when(valueOperations.setIfAbsent(eq(KEY), any(String.class), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        Optional<String> token = lock.tryAcquire(KEY, Duration.ofSeconds(30));

        assertThat(token).isPresent();
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), tokenCaptor.capture(), eq(Duration.ofSeconds(30)));
        assertThat(tokenCaptor.getValue()).isEqualTo(token.get());
    }

    @Test
    void secondContenderCannotAcquireLiveLock() {
        when(valueOperations.setIfAbsent(eq(KEY), any(String.class), any(Duration.class))).thenReturn(false);

        assertThat(lock.tryAcquire(KEY, Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    void ownerCanRelease() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("owner-token"))).thenReturn(1L);

        assertThat(lock.release(KEY, "owner-token")).isTrue();
    }

    @Test
    void nonOwnerCannotRelease() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("other-token"))).thenReturn(0L);

        assertThat(lock.release(KEY, "other-token")).isFalse();
    }

    @Test
    void redisFailureOnAcquire_returnsEmpty() {
        when(valueOperations.setIfAbsent(eq(KEY), any(String.class), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(lock.tryAcquire(KEY, Duration.ofSeconds(30))).isEmpty();
        verify(redisTemplate, never()).delete(KEY);
    }
}
