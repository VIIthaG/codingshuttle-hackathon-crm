package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(
        description =
                "Deal metrics. total/open/won/lost counts and values are the current pipeline snapshot in scope "
                        + "(not reconstructed stage history). created is deals with created_at in the selected range. "
                        + "byStage is the current snapshot. weightedPipelineValue = sum(open amount * probability / 100).")
public record DealAnalyticsResponse(
        long total,
        long created,
        long openCount,
        long wonCount,
        long lostCount,
        BigDecimal openPipelineValue,
        BigDecimal weightedPipelineValue,
        BigDecimal wonValue,
        BigDecimal lostValue,
        BigDecimal averageOpenDealSize,
        BigDecimal averageWonDealSize,
        List<DealStageMetricsResponse> byStage) {}
