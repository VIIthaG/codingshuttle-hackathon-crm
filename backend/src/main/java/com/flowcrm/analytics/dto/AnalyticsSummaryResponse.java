package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Role-scoped analytics summary. Uncached. team is empty for SALES_REP.")
public record AnalyticsSummaryResponse(
        AnalyticsRangeResponse range,
        LeadAnalyticsResponse leads,
        DealAnalyticsResponse deals,
        ActivityAnalyticsResponse activities,
        AnalyticsTrendsResponse trends,
        List<TeamMemberMetricsResponse> team) {}
