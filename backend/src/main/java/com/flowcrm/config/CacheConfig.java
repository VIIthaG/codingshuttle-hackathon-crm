package com.flowcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.dashboard.dto.DashboardSummaryResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String DASHBOARD_SUMMARY_CACHE = "dashboard-summary";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "app.cache")
    CacheProperties cacheProperties() {
        return new CacheProperties();
    }

    /**
     * Typed JSON serializer for dashboard summaries.
     *
     * <p>Do not use {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}
     * here: its default typing restores {@code EnumMap} values as {@link Integer}, and the HTTP
     * {@link ObjectMapper} then fails with {@code Integer cannot be cast to Long} while writing
     * {@code Map&lt;LeadStatus, Long&gt;} (HttpMessageNotWritableException / HTTP 500 on cache HIT).
     */
    public static RedisSerializer<DashboardSummaryResponse> dashboardSummaryRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new Jackson2JsonRedisSerializer<>(mapper, DashboardSummaryResponse.class);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, CacheProperties cacheProperties) {
        RedisCacheConfiguration dashboard = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(cacheProperties.getDashboardTtlSeconds()))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        dashboardSummaryRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(dashboard)
                .initialCacheNames(new LinkedHashSet<>(List.of(DASHBOARD_SUMMARY_CACHE)))
                .withInitialCacheConfigurations(Map.of(DASHBOARD_SUMMARY_CACHE, dashboard))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple")
    CacheManager simpleCacheManager() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(DASHBOARD_SUMMARY_CACHE);
        manager.setAllowNullValues(false);
        return manager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed cache={} key={}; falling through to DB", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed cache={}", cache.getName(), exception);
            }
        };
    }

    public static class CacheProperties {
        private long dashboardTtlSeconds = 60;

        public long getDashboardTtlSeconds() {
            return dashboardTtlSeconds;
        }

        public void setDashboardTtlSeconds(long dashboardTtlSeconds) {
            this.dashboardTtlSeconds = dashboardTtlSeconds;
        }
    }
}
