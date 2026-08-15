package com.flowcrm.analytics;

import com.flowcrm.analytics.dto.AnalyticsPreset;
import com.flowcrm.analytics.dto.AnalyticsRangeResponse;
import com.flowcrm.analytics.dto.TrendBucket;
import com.flowcrm.common.exception.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsRangeResolver {

    static final Instant ALL_TIME_FROM = Instant.parse("1970-01-01T00:00:00Z");

    public AnalyticsRangeResponse resolve(String range, Instant from, Instant toExclusive) {
        boolean custom = from != null || toExclusive != null;
        if (custom && (from == null || toExclusive == null)) {
            throw new BadRequestException("Both from and toExclusive are required when using a custom window");
        }
        if (custom) {
            if (!from.isBefore(toExclusive)) {
                throw new BadRequestException("from must be before toExclusive");
            }
            TrendBucket bucket = chooseBucket(from, toExclusive, AnalyticsPreset.CUSTOM);
            return new AnalyticsRangeResponse(from, toExclusive, AnalyticsPreset.CUSTOM, bucket);
        }

        AnalyticsPreset preset = parsePreset(range);
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        Instant exclusiveEnd = todayUtc.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant start =
                switch (preset) {
                    case LAST_7_DAYS -> todayUtc.minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
                    case LAST_30_DAYS -> todayUtc.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC);
                    case LAST_90_DAYS -> todayUtc.minusDays(89).atStartOfDay().toInstant(ZoneOffset.UTC);
                    case ALL_TIME -> ALL_TIME_FROM;
                    case CUSTOM -> throw new IllegalStateException("CUSTOM requires from/toExclusive");
                };
        return new AnalyticsRangeResponse(start, exclusiveEnd, preset, chooseBucket(start, exclusiveEnd, preset));
    }

    private static AnalyticsPreset parsePreset(String range) {
        if (range == null || range.isBlank() || "30d".equalsIgnoreCase(range)) {
            return AnalyticsPreset.LAST_30_DAYS;
        }
        return switch (range.trim().toLowerCase()) {
            case "7d" -> AnalyticsPreset.LAST_7_DAYS;
            case "90d" -> AnalyticsPreset.LAST_90_DAYS;
            case "all" -> AnalyticsPreset.ALL_TIME;
            default -> throw new BadRequestException("range must be one of 7d, 30d, 90d, all");
        };
    }

    private static TrendBucket chooseBucket(Instant from, Instant toExclusive, AnalyticsPreset preset) {
        if (preset == AnalyticsPreset.ALL_TIME) {
            return TrendBucket.MONTH;
        }
        long days = java.time.Duration.between(from, toExclusive).toDays();
        return days > 120 ? TrendBucket.MONTH : TrendBucket.DAY;
    }
}
