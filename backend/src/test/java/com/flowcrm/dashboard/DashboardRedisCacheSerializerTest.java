package com.flowcrm.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.config.CacheConfig;
import com.flowcrm.dashboard.dto.DashboardSummaryResponse;
import com.flowcrm.enums.LeadStatus;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Proves the production Redis dashboard value serializer round-trips to a type that
 * the HTTP {@link ObjectMapper} can write (the failure mode of the previous GenericJackson + EnumMap setup).
 */
class DashboardRedisCacheSerializerTest {

    @Test
    void productionTypedSerializer_roundTripsAndHttpWritable() throws Exception {
        DashboardSummaryResponse original = sampleLinkedHashMap();
        RedisSerializer<DashboardSummaryResponse> serializer = CacheConfig.dashboardSummaryRedisSerializer();

        byte[] bytes = serializer.serialize(original);
        DashboardSummaryResponse restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(DashboardSummaryResponse.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.leadsByStatus().get(LeadStatus.CONTACTED)).isEqualTo(1L);
        assertThat(restored.leadsByStatus().get(LeadStatus.CONTACTED)).isInstanceOf(Long.class);

        ObjectMapper httpMapper = new ObjectMapper().findAndRegisterModules();
        String json = httpMapper.writeValueAsString(restored);
        assertThat(json).contains("\"totalLeads\":1");
        assertThat(json).contains("\"CONTACTED\":1");
    }

    @Test
    void genericJacksonPlusEnumMap_breaksHttpWrite_documentingPreviousBug() {
        // Previous production bug: GenericJackson restored EnumMap values as Integer;
        // HTTP LongSerializer then threw ClassCastException → HttpMessageNotWritableException / 500.
        EnumMap<LeadStatus, Long> byStatus = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, status == LeadStatus.CONTACTED ? 1L : 0L);
        }
        DashboardSummaryResponse original =
                new DashboardSummaryResponse(
                        1,
                        byStatus,
                        4,
                        2,
                        1,
                        0,
                        java.math.BigDecimal.ZERO.setScale(2),
                        java.math.BigDecimal.ZERO.setScale(2),
                        java.util.Map.of(),
                        0,
                        java.math.BigDecimal.ZERO.setScale(2));

        GenericJackson2JsonRedisSerializer generic = new GenericJackson2JsonRedisSerializer();
        Object restored = generic.deserialize(generic.serialize(original));
        assertThat(restored).isInstanceOf(DashboardSummaryResponse.class);

        ObjectMapper httpMapper = new ObjectMapper().findAndRegisterModules();
        assertThatThrownBy(() -> httpMapper.writeValueAsString(restored))
                .hasStackTraceContaining("Integer cannot be cast to class java.lang.Long");
    }

    private static DashboardSummaryResponse sampleLinkedHashMap() {
        Map<LeadStatus, Long> byStatus = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, status == LeadStatus.CONTACTED ? 1L : 0L);
        }
        return DashboardSummaryResponse.of(1, byStatus, 4, 2, 1);
    }
}
