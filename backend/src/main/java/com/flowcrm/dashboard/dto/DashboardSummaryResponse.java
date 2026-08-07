package com.flowcrm.dashboard.dto;

import com.flowcrm.enums.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
        long upcomingFollowUps) {

    public static DashboardSummaryResponse of(
            long totalLeads,
            Map<LeadStatus, Long> leadsByStatus,
            long openTasks,
            long overdueTasks,
            long upcomingFollowUps) {
        // LinkedHashMap (not EnumMap): safer Redis/HTTP JSON round-trips for enum-keyed maps.
        Map<LeadStatus, Long> byStatus = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, leadsByStatus.getOrDefault(status, 0L));
        }
        return new DashboardSummaryResponse(totalLeads, byStatus, openTasks, overdueTasks, upcomingFollowUps);
    }
}
