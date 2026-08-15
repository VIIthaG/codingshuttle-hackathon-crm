package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "UTC window used for created-at metrics: from inclusive, toExclusive exclusive")
public record AnalyticsRangeResponse(
        Instant from,
        Instant toExclusive,
        AnalyticsPreset preset,
        @Schema(description = "DAY for 7/30/90 day presets; MONTH for ALL_TIME and wide custom ranges")
        TrendBucket bucket) {}
