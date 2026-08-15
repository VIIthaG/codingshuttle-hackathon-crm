package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Current operational workload/pipeline for one user. ADMIN only. Not a performance score.")
public record TeamMemberMetricsResponse(
        UUID userId,
        String displayName,
        long openDeals,
        BigDecimal openPipelineValue,
        long wonDeals,
        BigDecimal wonValue,
        long openTasks,
        long overdueTasks,
        long scheduledMeetings,
        long plannedCalls) {}
