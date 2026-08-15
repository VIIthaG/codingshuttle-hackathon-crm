package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(
        description =
                "Lead metrics. total is current inventory in scope. created/byStatus/converted/lost are the "
                        + "created-at cohort (current status, not historical). conversionRate = converted / (converted + lost), "
                        + "or 0 when the denominator is 0.")
public record LeadAnalyticsResponse(
        long total,
        long created,
        long converted,
        long lost,
        BigDecimal conversionRate,
        List<LeadStatusCountResponse> byStatus) {}
