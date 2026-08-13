package com.flowcrm.dashboard.dto;

import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "Cached dashboard aggregates for the current user scope")
public record DashboardSummaryResponse(
        @Schema(description = "Total visible leads", example = "12")
        long totalLeads,

        @Schema(description = "Lead counts keyed by LeadStatus")
        Map<LeadStatus, Long> leadsByStatus,

        @Schema(description = "OPEN tasks", example = "4")
        long openTasks,

        @Schema(description = "OPEN tasks past dueAt", example = "1")
        long overdueTasks,

        @Schema(description = "Upcoming follow-ups with reminderAt in the near term", example = "2")
        long upcomingFollowUps,

        @Schema(description = "Open (non-terminal) deals", example = "5")
        long openDeals,

        @Schema(description = "Sum of open deal amounts", example = "125000.00")
        BigDecimal openPipelineValue,

        @Schema(description = "Sum of open deal amounts weighted by probability", example = "43750.00")
        BigDecimal weightedPipelineValue,

        @Schema(description = "Deal counts keyed by DealStage")
        Map<DealStage, Long> dealsByStage,

        @Schema(description = "CLOSED_WON deals", example = "2")
        long wonDeals,

        @Schema(description = "Sum of CLOSED_WON deal amounts", example = "80000.00")
        BigDecimal wonDealValue) {

    public static DashboardSummaryResponse of(
            long totalLeads,
            Map<LeadStatus, Long> leadsByStatus,
            long openTasks,
            long overdueTasks,
            long upcomingFollowUps) {
        return of(
                totalLeads,
                leadsByStatus,
                openTasks,
                overdueTasks,
                upcomingFollowUps,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of(),
                0,
                BigDecimal.ZERO);
    }

    public static DashboardSummaryResponse of(
            long totalLeads,
            Map<LeadStatus, Long> leadsByStatus,
            long openTasks,
            long overdueTasks,
            long upcomingFollowUps,
            long openDeals,
            BigDecimal openPipelineValue,
            BigDecimal weightedPipelineValue,
            Map<DealStage, Long> dealsByStage,
            long wonDeals,
            BigDecimal wonDealValue) {
        Map<LeadStatus, Long> byStatus = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, leadsByStatus.getOrDefault(status, 0L));
        }
        Map<DealStage, Long> byStage = new LinkedHashMap<>();
        for (DealStage stage : DealStage.values()) {
            byStage.put(stage, dealsByStage.getOrDefault(stage, 0L));
        }
        return new DashboardSummaryResponse(
                totalLeads,
                byStatus,
                openTasks,
                overdueTasks,
                upcomingFollowUps,
                openDeals,
                scaleMoney(openPipelineValue),
                scaleMoney(weightedPipelineValue),
                byStage,
                wonDeals,
                scaleMoney(wonDealValue));
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP);
    }
}
